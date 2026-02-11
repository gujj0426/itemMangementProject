package com.pdfconverter.service;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.model.ProductAttribute;
import com.pdfconverter.util.ExtractUtil;
import com.pdfconverter.util.PersonalizationParserUtil;
import com.pdfconverter.util.ProductNameMapper;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class PdfExtractorService {
    private static final Logger log = LoggerFactory.getLogger(PdfExtractorService.class);
    @Resource
    private AttributeExtractor attributeExtractor;
    private ProductNameMapper productNameMapper;
    @Resource
    private ProductTitleRecognitionService productTitleRecognitionService;
    @Resource
    private ProductMappingService productMappingService;
    @Resource
    private ExtractUtil extractUtil;
    /**
     * 解析单个pdf订单信息
     *
     * @param pdfPath 文件路径
     * @return 解析订单实体list
     */
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
     * 处理单个订单
     *
     * @param orderText 单个订单的完整内容
     * @return PdfOrderData 单个订单实体 包含订单基本信息和商品信息
     */
    private PdfOrderData parseOrder(String orderText) throws IOException {
        PdfOrderData order = new PdfOrderData();
        List<ItemDetail> items = new ArrayList<>();
        //---------------------------------开始提起取订单基本信息（这部分的内容是固定的）------------------------------
        // 提取订单编号
        Pattern orderNumberPattern = Pattern.compile("Order\\s*#\\s*(\\S+)");
        Matcher orderMatcher = orderNumberPattern.matcher(orderText);
        if (orderMatcher.find()) {
            order.setOrderNumber(orderMatcher.group(1).trim());
        } else {
            throw new IllegalArgumentException("No valid order number found in text.");
        }
        // 用户名 (Ship to 第一行)
        String shipTo = extractUtil.extractBetween(orderText, "Ship to", "Scheduled to ship by");
        String[] shipLines = shipTo.trim().split("\n");
        order.setUsername(shipLines[0].trim());
        order.setShippingAddress(String.join("\n", Arrays.copyOfRange(shipLines, 1, shipLines.length)).trim());
        // 计划发货日期
        order.setScheduledShippingDate( extractUtil.cleanDateText( extractUtil.extractBetween(orderText, "Scheduled to ship by", "Shop").trim()));

        // 店铺名
        order.setShopName(extractUtil.extractBetween(orderText, "Shop", "Order date").trim());

        // 下单日期
        order.setOrderDate(extractUtil.cleanDateText(extractUtil.extractBetween(orderText, "Order date", "Payment method").trim()));

        // 支付方式
        order.setPaymentMethod(extractUtil.extractBetween(orderText, "Payment method", "Shipping method").trim());

        // 物流方式
        order.setShippingMethod(extractUtil.extractBetween(orderText, "Shipping method", "Packaging").trim());

        // 包装信息
        order.setPackagingInfo(extractUtil.extractBetween(orderText, "Packaging", "Tracking").trim());

        // 跟踪信息
        String trackingLine = extractUtil.extractBetween(orderText, "Tracking", "\\d+\\s+items").trim();
        order.setTrackingInfo(trackingLine);
        String[] trackParts = trackingLine.split("via");
        order.setTrackingNumber(trackParts[0].trim());
        order.setCourierCompany(trackParts.length > 1 ? trackParts[1].trim() : "");
        //--------------------------------------结束提取订单基本信息------------------------------------
        // 商品数量
        Pattern itemCountPattern = Pattern.compile("\\d+\\s*item(s)?\\b");
        Matcher matcher = itemCountPattern.matcher(orderText);// 调试输出
        //商品信息提取
        if (matcher.find()) {
            int itemCount = 0;
            String itemLine = matcher.group().trim();
            itemCount = Integer.parseInt(itemLine.split("\\s+")[0]);
            order.setTotalItemQuantity(itemCount);
            //--------------------------------- 商品块提取------------------------------
            int startIndex = matcher.end(); // "X items" 的结束位置
            String remainingText = orderText.substring(startIndex);
            // 2. 提取商品标题的第一行作为分割标志
            String[] lines = remainingText.split("\\n");
            if (lines.length > 0) {
                String firstItemTitle = lines[1].trim();
                String[] itemBlocks = new String[0];
                // 3. 分割商品块（这里用第一个商品的标题分割，不严谨，应该使用配置的商品标题匹配--todo）
                itemBlocks = remainingText.split("(?=" + Pattern.quote(firstItemTitle) + ")");
                //循环处理商品块的内容
                for (int i = 1; i < itemBlocks.length && i <= itemCount; i++) {
                    // 判断是否为最后一笔商品
                    if (i == itemCount) {
                        // 删除 "Do the green thing" 及其之后的内容
                        int endIndex = itemBlocks[i].indexOf("Do the green thing");
                        if (endIndex != -1) {
                            itemBlocks[i] = itemBlocks[i].substring(0, endIndex);
                        }
                    }
                    // 解析单个商品信息
                    items.addAll(parseItemDetail(itemBlocks[i], order.getShopName()));
                }
            } else {
                throw new IllegalArgumentException("No valid item count found in text.");
            }
        } else {
            System.out.println("Debug: No match found for item count."); // 调试输出
            throw new IllegalArgumentException("No valid item count found in text.");
        }
        order.setItemDetails(items);
        order.setAdditionalNote(extractUtil.extractAfter(orderText, "Do the green thing"));

        return order;
    }

    /**
     * 根据店铺名称的同，使用不同的商品解析方法，
     * 已知店铺名称 1、TheVoro 2、hululuca
     * 解析单个 items 商品信息 样例：
     * //Silent Slide-On Dog Tag: Personalized Stainless Steel Pet
     * //ID
     * //Quantity: 1
     * //Color & Size: Gold_S
     * //Font: Font 23
     * //Personalization: Chloe
     * //145 S. Del Rancho Mesa, AZ
     * //(480) 238-6227
     * // 支持多种产品组合，如 "Item: Cufflinks + Oval Box"
     * // 会在 itemDetailList 中为每种产品创建一个独立的 ItemDetail
     */
    private List<ItemDetail> parseItemDetail(String block, String shopName) throws IOException {
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
     * 解析hululuca的商品信息
     */
    private List<ItemDetail> parseItemHululuca(String block) {
        List<ItemDetail> itemDetailList = new ArrayList<>();

        try {
            //1. 提取商品标题
            String itemTitle = getitemTitle(block);
            // 2. 提取订购数量 Quantity
            int quantity = extractLineAfter(block, "Quantity:");
            // 3. 提取 Personalization 内容
            String personalization = getPersonalization(block);
            // 4. 提取动态属性
            String dynamicSection = extractBetween(block, "Quantity:", "Personalization:").trim();
            String[] dynamicLines = dynamicSection.split("\n");
            Map<String, String> dynamicAttrsMap = attributeExtractor.parseDynamicAttributes(dynamicLines);

            // 5. 识别商品类型
            Set<String> productTypes = new LinkedHashSet<>();
            String titleLower = itemTitle.toLowerCase();

            if (titleLower.contains("dog tag holder")) {
                productTypes.add(getDogTagHolderName());
            } else if (titleLower.contains("dog tag")) {
                productTypes.add(getDogTagName());
            }

            // 6. 使用AttributeExtractor统一提取属性
            ItemDetail extractedAttrs = attributeExtractor.extractAllAttributes(dynamicAttrsMap, productTypes);
            String productSize = extractedAttrs.getProductSize().getDisplayName();
            String productColor = extractedAttrs.getProductColor().getDisplayName();
            String productVariable = extractedAttrs.getProductVariable().getDisplayName();

            // 7. 组装主商品列表
            for (String productType : productTypes) {
                ItemDetail mainItem = new ItemDetail();
                mainItem.setItemTitle(itemTitle);
                mainItem.setItemQuantity(quantity);
                mainItem.setPersonalization(personalization);
                mainItem.setDynamicAttributes(dynamicSection);
                mainItem.setOrderType(OrderType.fromDisplayName(productType));
                mainItem.setProductSize(productSize);
                mainItem.setProductColor(productColor);
                mainItem.setProductVariable(productVariable);
                mainItem.setFont("");
                mainItem.setStyle("");

                itemDetailList.add(mainItem);
            }

            // 8. 补充附属商品（狗牌需要硅胶绑带）
            if (productTypes.contains(getDogTagName())) {
                ItemDetail siliconeBandItem = accessoryFactory.createSimpleAccessory(
                        OrderType.DOG_TAG_HOLDER,
                        quantity * 2,  // 数量是狗牌数量的2倍
                        OrderType.DOG_TAG,
                        productColor,
                        productSize
                );
                itemDetailList.add(siliconeBandItem);
            }
        } catch (Exception e) {
            log.error("解析hululuca商品信息失败: {}", e.getMessage());
            throw e;
        }

        return itemDetailList;
    }

    /**
     * 解析TheVoro的商品信息
     */
    private List<ItemDetail> parseItemTheVoro(String block) throws IOException {
        List<ItemDetail> itemDetailList = new ArrayList<>();
        //1. 提取商品标题
        String itemTitle = extractUtil.getitemTitle(block);
        // 2. 提取订购数量 Quantity
        int quantity = extractUtil.extractLineAfter(block, "Quantity:");
        // 3. 提取 Personalization 内容
        String personalization = extractUtil.getPersonalization(block);
        // 4. 提取动态属性
        String dynamicSection = extractUtil.extractBetween(block, "Quantity:", "Personalization:").trim();
        // 4.1 拼接商品订购信息
        String information = "Quantity: " + quantity + "\n" + dynamicSection;
        // 4.2 将动态属性分割为Map
        Map<String, String> dynamicAttrsMap = attributeExtractor.parseDynamicAttributes(dynamicSection.split("\n"));
        // 5. 建立当前商品的主商品实体 ItemDetail
        ItemDetail mainItemDetail = getVoroProductTypes(itemTitle, dynamicAttrsMap);
        // 5.1 解析所有动态属性，提取主商品的Size、Color，拆分附属商品信息,返回附加商品类型列表
        ProductAttribute productAttribute = parseVoroDynamicInfo(dynamicAttrsMap, mainItemDetail, personalization);
        // 7. 组装附属产品列表
        itemDetailList = assembleItemDetailListForVoro(mainItemDetail,productAttribute);

        // 8. 补充默认添加的商品，比如心形相盒默认搭配一个同色系的基础链
        ProductAttribute addOnAttribute = productMappingService.parseProductFromTitle(itemTitle);
        if (addOnAttribute != null) {
            List<ItemDetail> accessories = supplementAccessories(addOnAttribute, mainItemDetail);
            itemDetailList.addAll(accessories);
        }

        return itemDetailList;
    }

    /**
     * 补全默认附加商品信息
     */
    private List<ItemDetail> supplementAccessories(ProductAttribute addOnAttribute, ItemDetail mainItemDetail) {
        List<ItemDetail> accessories = new ArrayList<>();
        for (int i = 0; i < addOnAttribute.getAdditionalProductCount(); i++) {
            ItemDetail addOnItem = new ItemDetail();
            addOnItem.setItemTitle(addOnAttribute.getAdditionalProductNames().get(i));
            addOnItem.setItemQuantity(mainItemDetail.getItemQuantity());
            addOnItem.setOrderType(addOnAttribute.getOrderType());
            addOnItem.setProductSize(ProductSize.UNKNOWN);
            addOnItem.setProductColor(ProductColor.UNKNOWN);
            addOnItem.setProductVariable(addOnAttribute.getProductVariable());
            addOnItem.setDynamicAttributes("");
            addOnItem.setPersonalization("");
            addOnItem.setStyle("");
            addOnItem.setFont("");
            accessories.add(addOnItem);
        }
        return accessories;
    }

    private List<ItemDetail> assembleItemDetailListForVoro(ItemDetail mainItemDetail, ProductAttribute productAttribute) {
        List<ItemDetail> itemDetailList = new ArrayList<>();

        for (String productType : productTypes) {
            OrderType orderType = OrderType.fromDisplayName(productType);
            ItemDetail item = new ItemDetail();
            item.setItemTitle(itemTitle);
            item.setItemQuantity(quantity);
            item.setDynamicAttributes(information);
            item.setPersonalization(personalization);
            item.setOrderType(orderType);
            item.setStyle(style); // 设置设计风格字段
            item.setFont(font); // 设置字体字段

            // 根据产品类型设置特殊字段
            if (orderType == OrderType.BOX) {
                // 包装盒的特殊处理：只有数量，没有型号、颜色、设计风格、字体
                String standardName = productNameMapper.getStandardName(productType); // 映射后的标准名称
                item.setProductVariable(standardName); // 产品变量，如 "Oval Box-椭圆形开窗木盒"
                // 包装盒不设置型号、颜色、设计风格、字体
                item.setProductSize("");
                item.setProductColor("");
                item.setFont("");
                item.setStyle("");
                item.setItemTitle("");
                item.setDynamicAttributes("");
                item.setPersonalization("");
            } else {
                // 其他产品类型（袖扣、领带夹、狗牌、相盒）保留原有字段
                item.setProductSize(productAttribute.getProductSize());
                item.setProductColor(productAttribute.getProductColor());
                item.setProductVariable(productAttribute.getProductVariable());
            }

            itemDetailList.add(item);
        }
        return itemDetailList;
    }

    private ProductAttribute parseVoroDynamicInfo(Map<String, String> dynamicAttrsMap, ItemDetail itemDetail, String personalization) {
        ProductAttribute productAttribute = new ProductAttribute();
        for (Map.Entry<String, String> entry : dynamicAttrsMap.entrySet()) {
            String key = entry.getKey().toLowerCase();
            String value = entry.getValue();
            //解析商品型号
            if (key.contains("size")) {
                itemDetail.setProductSize(attributeExtractor.extractSizeFromValue(value));
            }
            //解析商品颜色
            if ((key.contains("color") || key.contains("colour") || key.contains("locket finish"))) {
                productAttribute.setColor(attributeExtractor.extractColorFromValue(value));
            }
            // 如果 Size 和 Color 都在同一个字段（如 "Size and Color: Gold_L"）
            if (key.contains("size") && key.contains("color") && value.contains("_")) {
                String[] parts = value.split("_");
                if (parts.length >= 2) {
                    productAttribute.setColor(attributeExtractor.extractColorFromValue(parts[0].trim()));
                    productAttribute.setSize(parts[1].trim());
                }
            }
            //从动态属性中识别产品类型（袖扣、领带夹、包装盒）
            if ((value.toLowerCase().contains("cufflink") || value.toLowerCase().contains("cufflinks"))) {
                productAttribute.setAdditionalProductName(getCufflinkName());
            }
            if ((value.matches(".*Tie\\s{0,}Clip.*"))) {
                productAttribute.setAdditionalProductName(getTieClipName());
            }
            if (value.toLowerCase().contains("oval box")) {
                productAttribute.setAdditionalProductName(getBoxName());
                productAttribute.setProductVariable("Oval Box");
            } else if (value.toLowerCase().contains("square box")) {
                productAttribute.setAdditionalProductName(getBoxName());
                productAttribute.setProductVariable("Square Box");
            } else if (value.toLowerCase().contains("box")) {
                productAttribute.setAdditionalProductName(getBoxName());
                productAttribute.setProductVariable("Box");
            }
            //如果productTypes中包含"花卉心形相盒吊坠"，解析月份"Birth Flower Style"，调用函数getmonthFromValue实现
            if (productAttribute.getAdditionalProductName().contains(getFlowerHeartBoxName())
                    && key.contains("birth flower style")) {
                productAttribute.setProductVariable(attributeExtractor.getMonthFromValue(value));
            }
            // 6. 从Personalization中提取刻录及设计信息
            productAttribute.setStyle(PersonalizationParserUtil.extractStyle(personalization));
            productAttribute.setFont(PersonalizationParserUtil.extractFont(personalization));
        }
        return productAttribute;
    }

    /**
     * 通过标题识别出主商品
     *
     * @param itemTitle       商品标题
     * @param dynamicAttrsMap 动态属性Map
     * @return 主商品类型
     */
    private ItemDetail getVoroProductTypes(String itemTitle, Map<String, String> dynamicAttrsMap) {
        ItemDetail itemDetail = new ItemDetail();

        // 使用商品标题识别服务识别主商品类型
        String mainProductType = productTitleRecognitionService.identifyProductType(itemTitle);
        if (!mainProductType.equals("未知商品")) {
            // 将识别到的商品类型转换为内部使用的常量
            String internalType = convertToInternalProductType(itemTitle, mainProductType);
            if (internalType != null && !internalType.isEmpty()) {
                itemDetail.setOrderType(OrderType.fromDisplayName(internalType));
            }
        }
        return itemDetail;

    }

    /**
     * 将标题识别服务返回的商品类型转换为内部使用的常量
     * 通过匹配标题找到对应的规则，然后从配置中获取内部类型常量
     *
     * @param itemTitle      商品标题（用于重新匹配规则）
     * @param recognizedType 标题识别服务返回的商品类型
     * @return 内部使用的商品类型常量
     */
    private String convertToInternalProductType(String itemTitle, String recognizedType) {
        // 先尝试从配置中直接映射
        if (recognizedType.equals(ProductTitleMapping.PRODUCT_FLOWER_HEART_BOX)) {
            return getFlowerHeartBoxName();
        } else if (recognizedType.equals(ProductTitleMapping.PRODUCT_CUFFLINKS)) {
            return getCufflinkName();
        } else if (recognizedType.equals(ProductTitleMapping.PRODUCT_TIE_CLIP_SINGLE) ||
                recognizedType.equals(ProductTitleMapping.PRODUCT_TIE_CLIP_DOUBLE)) {
            return getTieClipName();
        }

        // 如果无法直接映射，尝试通过标题重新匹配规则获取内部类型
        String titleLower = itemTitle.toLowerCase();
        Map<String, List<String>> rules = productTitleRecognitionService.getAllRules();

        for (Map.Entry<String, List<String>> entry : rules.entrySet()) {
            String ruleKey = entry.getKey();
            List<String> keywords = entry.getValue();

            // 检查标题中是否包含该规则的所有关键词
            boolean allKeywordsMatched = true;
            for (String keyword : keywords) {
                if (!titleLower.contains(keyword)) {
                    allKeywordsMatched = false;
                    break;
                }
            }

            if (allKeywordsMatched) {
                // 获取规则对应的内部类型
                String internalType = productTitleRecognitionService.getInternalTypeByRule(ruleKey);
                if (internalType != null && !internalType.isEmpty()) {
                    return internalType;
                }
            }
        }

        // 默认返回识别到的类型
        return recognizedType;
    }

    private ItemDetail addDefaultBoxItem(int quantity, Set<String> productTypes) {
        ItemDetail defaultBoxItem = new ItemDetail();
        defaultBoxItem.setItemTitle("");
        defaultBoxItem.setItemQuantity(quantity);
        defaultBoxItem.setDynamicAttributes("");
        defaultBoxItem.setPersonalization("");
        defaultBoxItem.setOrderType(OrderType.BOX);
        // 包装盒不设置型号、颜色、设计风格、字体
        defaultBoxItem.setProductSize(null);
        defaultBoxItem.setProductColor(null);
        defaultBoxItem.setFont(null);
        defaultBoxItem.setStyle(null);

        // 根据产品类型设置产品变量
        if (productTypes.contains(getCufflinkName())) {
            defaultBoxItem.setProductVariable(getSmallSquareBoxName());
        } else if (productTypes.contains(getTieClipName())) {
            defaultBoxItem.setProductVariable(getRectangleBoxName());
        }
        return defaultBoxItem;
    }

    private ItemDetail addBaseChainItem(String itemTitle, int quantity, String productColor) {
        ItemDetail baseChainItem = new ItemDetail();
        baseChainItem.setItemTitle(itemTitle);
        baseChainItem.setItemQuantity(quantity); // 数量同原商品
        baseChainItem.setDynamicAttributes("");
        baseChainItem.setPersonalization("");
        baseChainItem.setOrderType(OrderType.BASE_CHAIN); // 产品名称为基础链
        baseChainItem.setProductVariable(getFullChainName()); // 产品变量为全链
        baseChainItem.setProductSize(""); // 继承型号
        baseChainItem.setProductColor(productColor); // 继承颜色
        baseChainItem.setFont(""); // 继承字体
        baseChainItem.setStyle(""); // 继承设计风格
        return baseChainItem;
    }

    private Map<String, String> getDynamicInfo(String[] dynamicSectionInfo) {
        Map<String, String> dynamicAttrsMap = new HashMap<>();
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

}