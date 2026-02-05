package com.pdfconverter.service;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.util.MonthMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractorService {
    private static final Logger log = LoggerFactory.getLogger(PdfExtractorService.class);

    @Resource
    private ProductNameMapper productNameMapper;


    public List<PdfOrderData> extractFromPdf(String pdfPath) throws IOException {
        List<PdfOrderData> orders = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            log.debug("Extracted PDF text: {}", text);
            
            // 分割多个订单块（适配 Order 和 # 之间的任意空格数量）
            String[] orderBlocks = text.split("(?=Order\\s{0,}#)");
            for (String block : orderBlocks) {
                if (!block.trim().isEmpty()) {
                    //解析单个订单
                    orders.add(parseOrder(block));
                }
            }
        }
        return orders;
    }
    /**
     * 拆分单个订单编号对应的商品信息
     * */
    private PdfOrderData parseOrder(String text) {
        PdfOrderData order = new PdfOrderData();
        List<ItemDetail> items = new ArrayList<>();

        // 提取订单编号
        Pattern orderNumberPattern = Pattern.compile("Order\\s*#\\s*(\\S+)");
        Matcher orderMatcher = orderNumberPattern.matcher(text);
        if (orderMatcher.find()) {
            order.setOrderNumber(orderMatcher.group(1).trim());
        } else {
            throw new IllegalArgumentException("No valid order number found in text.");
        }

      // 用户名 (Ship to 第一行)
        String shipTo = extractBetween(text, "Ship to", "Scheduled to ship by");
        String[] shipLines = shipTo.trim().split("\n");
        order.setUsername(shipLines[0].trim());
        order.setShippingAddress(String.join("\n", Arrays.copyOfRange(shipLines, 1, shipLines.length)).trim());

        // 计划发货日期
        String scheduledShippingDateText = cleanDateText(extractBetween(text, "Scheduled to ship by", "Shop").trim());
            log.debug("Parsing scheduled shipping date text: {}", scheduledShippingDateText);
            try {
                LocalDate date = LocalDate.parse(
                        scheduledShippingDateText,
                        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
                order.setScheduledShippingDate(date);
                log.debug("Parsed scheduled shipping date: {}", date.format(DateTimeFormatter.ofPattern("yyyy/MM/dd")));
            } catch (DateTimeParseException e) {
                log.error("Failed to parse scheduled shipping date: {} with formatter: MMM d, yyyy", scheduledShippingDateText);
                order.setScheduledShippingDate(null);
            }

        // 店铺名
        order.setShopName(extractBetween(text, "Shop", "Order date").trim());

        // 下单日期
        String orderDateText = cleanDateText(extractBetween(text, "Order date", "Payment method").trim());
            log.debug("Parsing order date text: {}", orderDateText);
            try {
                order.setOrderDate(LocalDate.parse(
                        orderDateText,
                        DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)));
            } catch (DateTimeParseException e) {
                log.error("Failed to parse order date: {} with formatter: MMM d, yyyy", orderDateText);
                order.setOrderDate(null);
            }

        // 支付方式
        order.setPaymentMethod(extractBetween(text, "Payment method", "Shipping method").trim());

        // 物流方式
        order.setShippingMethod(extractBetween(text, "Shipping method", "Packaging").trim());

        // 包装信息
        order.setPackagingInfo(extractBetween(text, "Packaging", "Tracking").trim());

        // 跟踪信息
        String trackingLine = extractBetween(text, "Tracking", "\\d+\\s+items").trim();
        order.setTrackingInfo(trackingLine);
        String[] trackParts = trackingLine.split("via");
        order.setTrackingNumber(trackParts[0].trim());
        order.setCourierCompany(trackParts.length > 1 ? trackParts[1].trim() : "");

        // 商品数量
        Pattern itemCountPattern = Pattern.compile("\\d+\\s*item(s)?\\b");
        Matcher matcher = itemCountPattern.matcher(text);// 调试输出
        int itemCount = 0;

        if (matcher.find()) {
            String itemLine = matcher.group().trim();
            itemCount = Integer.parseInt(itemLine.split("\\s+")[0]);
            order.setTotalItemQuantity(itemCount);
            // 商品块提取
            int startIndex = matcher.end(); // "X items" 的结束位置
            String remainingText = text.substring(startIndex);
            // 2. 提取商品标题的第一行作为分割标志
            String[] lines = remainingText.split("\\n");
            if (lines.length > 0) {
                String firstItemTitle = lines[1].trim();
                String[] itemBlocks = new String[0];
                // 3. 分割商品块
                itemBlocks = remainingText.split("(?=" + Pattern.quote(firstItemTitle) + ")");
                for (int i = 1; i < itemBlocks.length && i <= itemCount; i++) {
                    // 判断是否为最后一笔商品
                    if (i == itemCount) {
                        // 删除 "Do the green thing" 及其之后的内容
                        int endIndex = itemBlocks[i].indexOf("Do the green thing");
                        if (endIndex != -1) {
                            itemBlocks[i] = itemBlocks[i].substring(0, endIndex);
                        }
                    }
                    items.addAll (parseItemDetail(itemBlocks[i], order.getShopName()));
                }
            } else {
                throw new IllegalArgumentException("No valid item count found in text.");
            }
        } else {
            System.out.println("Debug: No match found for item count."); // 调试输出
            throw new IllegalArgumentException("No valid item count found in text.");
        }
        order.setItemDetails(items);
        order.setAdditionalNote(extractAfter(text, "Do the green thing"));

        return order;
    }
    /**
     * 根据店铺名称的同，使用不同的商品解析方法，
     * 已知店铺名称 1、TheVoro 2、hululuca
     * 解析单个 items 商品信息 样例：
    //Silent Slide-On Dog Tag: Personalized Stainless Steel Pet
    //ID
    //Quantity: 1
    //Color & Size: Gold_S
    //Font: Font 23
    //Personalization: Chloe
    //145 S. Del Rancho Mesa, AZ
    //(480) 238-6227
    // 支持多种产品组合，如 "Item: Cufflinks + Oval Box"
    // 会在 itemDetailList 中为每种产品创建一个独立的 ItemDetail
     * */
    private List<ItemDetail> parseItemDetail(String block, String shopName) {
        // VORO店铺商品解析
        if (shopName.equals("TheVoro")) {
            return parseItemTheVoro(block);
        //hululu店铺商品解析
        } else if (shopName.equals("hululuca")) {
            return parseItemHululuca(block);
        } else {
            return null;
        }
    }
    /**
     *  解析hululuca的商品信息
     * */
    private List<ItemDetail> parseItemHululuca(String block) {
        List<ItemDetail> itemDetailList = new ArrayList<>();
        //1. 提取商品标题
        String itemTitle = getitemTitle(block);
        // 2. 提取订购数量 Quantity
        int quantity = extractLineAfter(block, "Quantity:");
        //调用解析商品函数，根据店铺名称shopname的不用，使用不同的方法解析商品属性等信息，返回解析后的商品信息
        // 3. 提取 Personalization 内容：从 "Personalization:" 开始，直到下一个商品块或文件结束
        String personalization = gePpersonalization(block);
        // 4. 提取动态属性：从 Quantity 到 Personalization 之间的"动态属性"部分
        String dynamicSection = extractBetween(block,"Quantity:","Personalization:").trim();
        // 5. 按行分割动态属性（保留换行）
        String[] dynamicLines = dynamicSection.split("\n");
        Map<String, String> dynamicAttrsMap = new LinkedHashMap<>(); // 保持顺序
        StringBuilder fullDynamicInfo = new StringBuilder();

        for (String line : dynamicLines) {
            line = line.trim();
            if (line.isEmpty()) continue;

            // 拼接"商品信息栏完全信息"（保留原始格式）
            if (fullDynamicInfo.length() > 0) {
                fullDynamicInfo.append("|");
            }
            fullDynamicInfo.append(line);

            // 尝试解析 key: value
            int colon = line.indexOf(":");
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                dynamicAttrsMap.put(key, value);
            } else {
                // 无冒号的行，作为上一个 key 的延续（罕见情况）
                if (!dynamicAttrsMap.isEmpty()) {
                    String lastKey = new ArrayList<>(dynamicAttrsMap.keySet()).get(dynamicAttrsMap.size() - 1);
                    dynamicAttrsMap.put(lastKey, dynamicAttrsMap.get(lastKey) + " " + line);
                }
            }
        }
        // 8. 识别商品块中的所有产品类型
        Set<String> productTypes = new LinkedHashSet<>(); // 保持顺序

        // 8.1 从商品标题判断狗牌
        String titleLower = itemTitle.toLowerCase();

        if (titleLower.contains("dog tag holder")) {
            productTypes.add("硅胶绑带");
        }else if (titleLower.contains("dog tag")) {
            productTypes.add("狗牌");
        }
        // 6. 从所有动态属性中提取 Size 和 Color
        String size = null, color = null ,productVariable="";
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();
            //解析商品型号
            if (key.contains("size") && size == null) {
                size = extractSizeFromValue(value);
            }
            //解析商品颜色
            if ((key.contains("color") || key.contains("colour")|| key.contains("locket finish")) && color == null) {
                color = extractColorFromValue(value);
            }

            // 如果 Size 和 Color 都在同一个字段（如 "Size and Color: Gold_L"）
            if (key.contains("size") && key.contains("color") && value.contains("_")) {
                String[] parts = value.split("_");
                if (parts.length >= 2) {
                    color = extractColorFromValue(parts[0].trim());
                    size = parts[1].trim();
                }
            }
            //如果productTypes中包含"花卉心形相盒吊坠"，解析月份“Birth Flower Style”，调用函数getmonthFromValue实现
            if (productTypes.contains("花卉心形相盒吊坠")&& key.contains("birth flower style")) {
                productVariable = getMonthFromValue(value);
            }

        }
        // 10.2 如果是狗牌，则补充硅胶绑带
        if (productTypes.contains("狗牌")) {
            ItemDetail siliconeBandItem = new ItemDetail();
            siliconeBandItem.setItemTitle("");
            siliconeBandItem.setItemQuantity(quantity * 2); // 数量是狗牌数量的2倍
            siliconeBandItem.setDynamicAttributes("");
            siliconeBandItem.setPersonalization("");
            siliconeBandItem.setOrderType("硅胶绑带");
            siliconeBandItem.setSize(size); // 型号同狗牌
            siliconeBandItem.setColor(color);
            siliconeBandItem.setFont(""); // 硅胶绑带没有字体
            siliconeBandItem.setStyle(""); // 硅胶绑带保留设计风格

            itemDetailList.add(siliconeBandItem);
        }
        return itemDetailList;
    }
    /**
     *  解析TheVoro的商品信息
     * */
    private List<ItemDetail> parseItemTheVoro(String block) {
        //1. 提取商品标题
        String itemTitle = getitemTitle(block);
        // 2. 提取订购数量 Quantity
        int quantity = extractLineAfter(block, "Quantity:");
        //调用解析商品函数，根据店铺名称shopname的不用，使用不同的方法解析商品属性等信息，返回解析后的商品信息
        List<ItemDetail> itemDetailList = new ArrayList<>();
        // 3. 提取 Personalization 内容：从 "Personalization:" 开始，直到下一个商品块或文件结束
        String personalization = gePpersonalization(block);
        // 4. 提取动态属性：从 Quantity 到 Personalization 之间的"动态属性"部分
        String dynamicSection = extractBetween(block,"Quantity:","Personalization:").trim();
        // 5. 按行分割动态属性（保留换行）
        Map<String, String> dynamicAttrsMap = getDynamicInfo(dynamicSection.split("\n"));
        // 8. 识别商品块中的所有产品类型
        Set<String> productTypes = new LinkedHashSet<>(); // 保持顺序

        // 8.1 从商品标题判断商品类型
        String titleLower = itemTitle.toLowerCase();
        if (titleLower.contains("birth flower")) {
            productTypes.add("花卉心形相盒吊坠");
        }else if (titleLower.contains("cufflink") || titleLower.contains("cufflinks")) {
            productTypes.add("袖扣");
        }else if (titleLower.matches(".*Tie\\s{0,}Clip.*")) {
            productTypes.add("领带夹");
        }
        // 6. 从所有动态属性中提取 Size 和 Color
        String size = null, color = null ,productVariable="";
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();
            //解析商品型号
            if (key.contains("size") && size == null) {
                size = extractSizeFromValue(value);
            }
            //解析商品颜色
            if ((key.contains("color") || key.contains("colour")|| key.contains("locket finish")) && color == null) {
                color = extractColorFromValue(value);
            }

            // 如果 Size 和 Color 都在同一个字段（如 "Size and Color: Gold_L"）
            if (key.contains("size") && key.contains("color") && value.contains("_")) {
                String[] parts = value.split("_");
                if (parts.length >= 2) {
                    color = extractColorFromValue(parts[0].trim());
                    size = parts[1].trim();
                }
            }
            //如果productTypes中包含"花卉心形相盒吊坠"，解析月份“Birth Flower Style”，调用函数getmonthFromValue实现
            if (productTypes.contains("花卉心形相盒吊坠")&& key.contains("birth flower style")) {
                productVariable = getMonthFromValue(value);
            }

        }
        // 8.2 从动态属性中识别产品类型（袖扣、领带夹、包装盒）
        boolean hasCufflink = false;
        boolean hasTieClip = false;
        // Oval Box, Square Box, Box
        String boxType = null;

        for (String value : dynamicAttrsMap.values()) {
            if (!productTypes.contains("袖扣") &&
                    ((value.toLowerCase().contains("cufflink") || value.toLowerCase().contains("cufflinks")))) {
                hasCufflink = true;
            }
            if (!productTypes.contains("领带夹") &&
                    (value.matches(".*Tie\\s{0,}Clip.*"))) {
                hasTieClip = true;
            }
            if (value.toLowerCase().contains("oval box")) {
                boxType = "Oval Box";
            } else if (value.toLowerCase().contains("square box")) {
                boxType = "Square Box";
            } else if (value.toLowerCase().contains("box")) {
                if (boxType == null) {
                    boxType = "Box";
                }
            }
        }

        // 添加识别到的产品类型
        if (hasCufflink) {
            productTypes.add("袖扣");
        }
        if (hasTieClip) {
            productTypes.add("领带夹");
        }
        if (boxType != null) {
            productTypes.add("包装盒");
        }

        // 9. 为每个产品类型创建独立的 ItemDetail
        String information = "Quantity: " + quantity + "\n" + dynamicSection.toString();
        // 分别提取设计风格和字体
        String style = PersonalizationParser.extractStyle(personalization);
        String font = PersonalizationParser.extractFont(personalization);

        for (String productType : productTypes) {
            ItemDetail item = new ItemDetail();
            item.setItemTitle(itemTitle);
            item.setItemQuantity(quantity);
            item.setDynamicAttributes(information);
            item.setPersonalization(personalization);
            item.setOrderType(productType);
            item.setStyle(style); // 设置设计风格字段
            item.setFont(font); // 设置字体字段

            // 根据产品类型设置特殊字段
            if (productType.equals("包装盒") && boxType != null) {
                // 包装盒的特殊处理：只有数量，没有型号、颜色、设计风格、字体
                item.setPackagingBox(boxType); // 原始值，如 "Oval Box"
                String standardName = productNameMapper.getStandardName(boxType); // 映射后的标准名称
                item.setProductVariable(standardName); // 产品变量，如 "Oval Box-椭圆形开窗木盒"
                item.setOrderType("包装盒");
                // 包装盒不设置型号、颜色、设计风格、字体
                item.setSize("");
                item.setColor("");
                item.setFont("");
                item.setStyle("");
                item.setItemTitle("");
                item.setDynamicAttributes("");
                item.setPersonalization("");
            } else {
                // 其他产品类型（袖扣、领带夹、狗牌、相盒）保留原有字段
                item.setSize(size);
                item.setColor(color);
                item.setProductVariable(productVariable);
            }

            itemDetailList.add(item);
        }

        // 10. 根据规则补充额外的 ItemDetail

        // 10.1 如果是袖扣或领带夹，且没有包装盒，则补充默认包装盒
        if ((hasCufflink || hasTieClip) && boxType == null) {
            itemDetailList.add(addDefaultBoxItem(quantity,hasCufflink,hasTieClip));
        }
        // 10.2 如果是花卉心形相盒吊坠，则补充基础链
        if (titleLower.contains("birth flower") || titleLower.contains("花卉心形相盒吊坠")) {
            itemDetailList.add(addBaseChainItem(itemTitle,quantity,color));
        }
        return itemDetailList;
    }

    private ItemDetail addDefaultBoxItem(int quantity, boolean hasCufflink, boolean hasTieClip) {
        ItemDetail defaultBoxItem = new ItemDetail();
        defaultBoxItem.setItemTitle("");
        defaultBoxItem.setItemQuantity(quantity);
        defaultBoxItem.setDynamicAttributes("");
        defaultBoxItem.setPersonalization("");
        defaultBoxItem.setOrderType("包装盒");
        // 包装盒不设置型号、颜色、设计风格、字体
        defaultBoxItem.setSize(null);
        defaultBoxItem.setColor(null);
        defaultBoxItem.setFont(null);
        defaultBoxItem.setStyle(null);

        // 根据产品类型设置产品变量
        if (hasCufflink) {
            defaultBoxItem.setProductVariable("小方形礼盒");
        } else if (hasTieClip) {
            defaultBoxItem.setProductVariable("长方形礼盒");
        }
        return defaultBoxItem;
    }

    private ItemDetail addBaseChainItem(String itemTitle, int quantity, String color) {
        ItemDetail baseChainItem = new ItemDetail();
        baseChainItem.setItemTitle(itemTitle);
        baseChainItem.setItemQuantity(quantity); // 数量同原商品
        baseChainItem.setDynamicAttributes("");
        baseChainItem.setPersonalization("");
        baseChainItem.setOrderType("基础链"); // 产品名称为基础链
        baseChainItem.setProductVariable("全链"); // 产品变量为全链
        baseChainItem.setSize(""); // 继承型号
        baseChainItem.setColor(color); // 继承颜色
        baseChainItem.setFont(""); // 继承字体
        baseChainItem.setStyle(""); // 继承设计风格
        return baseChainItem;
    }

    private Map<String, String> getDynamicInfo(String[] dynamicSectionInfo) {
        Map<String, String> dynamicAttrsMap=new HashMap<>();
        for (String line : dynamicSectionInfo) {
            line = line.trim();
            if (line.isEmpty()) continue;
            // 尝试解析 key: value
            int colon = line.indexOf(":");
            if (colon != -1) {
                String key = line.substring(0, colon).trim();
                String value = line.substring(colon + 1).trim();
                dynamicAttrsMap.put(key, value);
            } else {
                // 无冒号的行，作为上一个 key 的延续（罕见情况）
                if (!dynamicAttrsMap.isEmpty()) {
                    String lastKey = new ArrayList<>(dynamicAttrsMap.keySet()).get(dynamicAttrsMap.size() - 1);
                    dynamicAttrsMap.put(lastKey, dynamicAttrsMap.get(lastKey) + " " + line);
                }
            }
        }
        return dynamicAttrsMap;
    }

    private String getitemTitle(String block) {
        // 1. 提取商品标题：从块开头到 "Quantity:" 之前
        int qtyIndex = block.indexOf("Quantity:");
        if (qtyIndex == -1) {
            throw new IllegalArgumentException("Invalid block: missing 'Quantity:'");
        }
        return block.substring(0, qtyIndex).trim();
    }

    /**解析个性化信息*/
    private String gePpersonalization(String block) {
        String personalization="";
        int personalizationStart = block.indexOf("Personalization:");
        if (personalizationStart != -1) {
            personalizationStart += "Personalization:".length();
            int nextItemStart = block.indexOf("Custom Engraved Initials", personalizationStart);
            int end = (nextItemStart == -1) ? block.length() : nextItemStart;
            personalization = block.substring(personalizationStart, end).trim();
        }
        return personalization;
    }

    private String extractBetween(String text, String startRegex, String endRegex) {
        Pattern pattern = Pattern.compile(startRegex + "(.*?)" + endRegex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    private String extractAfter(String text, String marker) {
        int index = text.indexOf(marker);
        return index != -1 ? text.substring(index).trim() : "";
    }
    // 从 value 中提取 Size（如 L, S）
    private String extractSizeFromValue(String value) {
        value = value.toUpperCase();
        if (value.contains("L")) return "L";
        if (value.contains("S")) return "S";
        if (value.contains("M")) return "M";
        if (value.contains("XL")) return "XL";
        return null;
    }

    // 从 value 中提取 Color（如 Gold, Silver, Rose Gold, Red, Yellow, White, Blue, Pink）
    private String extractColorFromValue(String value) {
        value = value.toLowerCase();

        // 金色系
        if (value.contains("gold") && value.contains("rose")) return "玫瑰金";
        if (value.contains("gold")) return "金色";

        // 银色系
        if (value.contains("silver")) return "银色";

        // 黑色
        if (value.contains("black")) return "黑色";

        // 红色
        if (value.contains("red")) return "红色";

        // 黄色
        if (value.contains("yellow")) return "黄色";

        // 白色
        if (value.contains("white")) return "白色";

        // 蓝色
        if (value.contains("blue")) return "蓝色";

        // 粉色
        if (value.contains("light pink")) return "浅粉色";
        // 霓虹粉色
        if (value.contains("hot pink")) return "霓虹粉";
        return null;
    }

    // 提取某关键字后的第一行
    private String cleanDateText(String dateText) {
        // 清理日期文本中的多余空格和特殊字符
        return dateText.replaceAll("\\s+", " ").trim();
    }

    /**提取商品数量 Quantity*/
    private int extractLineAfter(String text, String keyword) {
        int index = text.indexOf(keyword);
        if (index == -1) return 0;
        index += keyword.length();
        int eol = text.indexOf("\n", index);

        int quantity = 1;
        try {
            quantity = Integer.parseInt(eol == -1 ? text.substring(index).trim() : text.substring(index, eol).trim().split("\\s+")[0]);
        } catch (Exception e) {
            quantity = 1; // 默认
        }
        return quantity;
    }

    /**
     * 根据月份value返回对应的中文月份
     *
     * @param value 月份value（如 "12 Topaz Snow"）
     * @return 对应的中文月份（如 "十二月"），如果未找到则返回原始value
     *
     * 示例：
     * getMonthFromValue("12 Topaz Snow") → "十二月"
     * getMonthFromValue("1 Garnet Glow") → "一月"
     * getMonthFromValue("6 Pearl Blossom") → "六月"
     */
    public String getMonthFromValue(String value) {
        if (value == null || value.trim().isEmpty()) {
            return value;
        }

        // 使用MonthMapper获取中文月份
        String chineseMonth = MonthMapper.getMonthFromValue(value);

        // 如果找到映射，返回中文月份；否则返回原始value
        return chineseMonth != null ? chineseMonth : value;
    }
}