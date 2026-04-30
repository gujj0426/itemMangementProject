package com.pdfconverter.service;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.OrderContext;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.model.ProductAttribute;
import com.pdfconverter.model.ProductItem;
import com.pdfconverter.util.ExtractUtil;
import com.pdfconverter.util.ItemDetailAdditionalUtil;
import com.pdfconverter.util.PersonalizationParserUtil;
import com.pdfconverter.service.mapper.ProductNameMappingService;
import com.pdfconverter.service.mapper.ProductVariableMapperService;
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
    private AttributeRuleEngine attributeRuleEngine;
    @Resource
    private ProductTitleRecognitionService productTitleRecognitionService;
    @Resource
    private ExtractUtil extractUtil;
    @Resource
    private ProductListService productListService;
    @Resource
    private ProductVariableMapperService productVariableMapper;
    @Resource
    private AccessoryItemFactory accessoryItemFactory;
    /**
     * 解析单个pdf订单信息，同时收集需要 Style 6 标注的页码
     *
     * @param pdfPath 文件路径
     * @return 解析订单实体list
     */
    public List<PdfOrderData> extractFromPdf(String pdfPath) throws IOException {
        return extractFromPdf(pdfPath, null, null);
    }

    /**
     * 解析单个pdf订单信息，同时收集需要 Style 6 标注的页码。
     * 采用两遍扫描策略，支持跨页订单：
     * - 第一遍：记录每个订单（Order #）出现的页码范围
     * - 第二遍：按页码范围提取完整文本再解析，避免订单被页码边界切断
     *
     * @param pdfPath              文件路径
     * @param style6Pages          传出参数：需要标注领带夹Style 6的页码集合（0基），若为null则不收集
     * @param silentDogTagSPages   传出参数：需要标注静音狗牌S的页码集合（0基），若为null则不收集
     * @return 解析订单实体list
     */
    public List<PdfOrderData> extractFromPdf(String pdfPath, Set<Integer> style6Pages, Set<Integer> silentDogTagSPages) throws IOException {
        List<PdfOrderData> orders = new ArrayList<>();
        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            PDFTextStripper stripper = new PDFTextStripper();
            int pageCount = document.getNumberOfPages();

            // ── 第一遍：扫描所有页，记录每个订单首次出现页 ──────────────────
            // key: 订单号，value: 起始页(0基)
            // 说明：部分跨页订单的续页不会重复出现 "Order #xxxx"，因此不能依赖“同订单号再次出现”来更新结束页。
            // 正确做法：按订单起始页切分，结束页 = 下一订单起始页 - 1。
            Map<String, Integer> orderStartPages = new LinkedHashMap<>();
            Pattern orderNumPattern = Pattern.compile("Order\\s*#\\s*(\\S+)");

            for (int pageIdx = 1; pageIdx <= pageCount; pageIdx++) {
                stripper.setStartPage(pageIdx);
                stripper.setEndPage(pageIdx);
                String pageText = stripper.getText(document);

                Matcher matcher = orderNumPattern.matcher(pageText);
                while (matcher.find()) {
                    String orderNum = matcher.group(1).trim();
                    int page0 = pageIdx - 1;
                    // 仅记录首次出现页；同页/后续重复出现不影响起始页
                    if (!orderStartPages.containsKey(orderNum)) {
                        orderStartPages.put(orderNum, page0);
                    }
                }
            }

            // 根据起始页推导每个订单的页码范围
            // key: 订单号，value: [起始页(0基), 结束页(0基)]
            Map<String, int[]> orderPageRanges = new LinkedHashMap<>();
            List<Map.Entry<String, Integer>> starts = new ArrayList<>(orderStartPages.entrySet());
            for (int i = 0; i < starts.size(); i++) {
                String orderNum = starts.get(i).getKey();
                int startPage0 = starts.get(i).getValue();
                int endPage0 = (i + 1 < starts.size())
                        ? starts.get(i + 1).getValue() - 1
                        : pageCount - 1;
                // 防御：若出现异常顺序，至少保证不小于起始页
                if (endPage0 < startPage0) {
                    endPage0 = startPage0;
                }
                orderPageRanges.put(orderNum, new int[]{startPage0, endPage0});
            }

            // ── 第二遍：按页码范围提取完整文本并解析 ───────────────────────
            for (Map.Entry<String, int[]> entry : orderPageRanges.entrySet()) {
                String orderNum = entry.getKey();
                int startPage = entry.getValue()[0] + 1; // 转回1基给PDFBox
                int endPage = entry.getValue()[1] + 1;

                stripper.setStartPage(startPage);
                stripper.setEndPage(endPage);
                String fullOrderText = stripper.getText(document);

                try {
                    PdfOrderData order = parseOrder(fullOrderText, startPage - 1);
                    orders.add(order);

                    // 记录含 Style 6 的所有页码（整个订单范围都标注）
                    if (style6Pages != null && order.isHasTieClipStyle6()) {
                        for (int p = startPage - 1; p <= endPage - 1; p++) {
                            style6Pages.add(p);
                        }
                    }
                    // 记录含静音狗牌S的所有页码
                    if (silentDogTagSPages != null && order.isHasSilentDogTagS()) {
                        for (int p = startPage - 1; p <= endPage - 1; p++) {
                            silentDogTagSPages.add(p);
                        }
                    }
                } catch (Exception e) {
                    log.warn("解析订单 {} 失败（页码 {}~{}）: {}",
                            orderNum, startPage, endPage, e.getMessage());
                    // 继续处理其他订单，不中断
                }
            }
        }
        return orders;
    }

    /**
     * 处理单个订单
     *
     * @param orderText 单个订单的完整内容
     * @param pageIdx   所属页码（0基）
     * @return PdfOrderData 单个订单实体 包含订单基本信息和商品信息
     */
    private PdfOrderData parseOrder(String orderText, int pageIdx) throws IOException {
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

            // 使用 "Quantity:" 分割商品块（每个商品都有 Quantity: 行，比用标题分割更可靠）
            // 先找到第一个 Quantity: 的位置
            int firstQtyIndex = remainingText.indexOf("Quantity:");
            if (firstQtyIndex == -1) {
                throw new IllegalArgumentException("No 'Quantity:' found in order items.");
            }

            // 从第一个 Quantity: 向前找，确定第一个商品的起始位置
            // 第一个商品的起始位置应该是 "Ship to" 行之后、第一个 Quantity: 之前的某处
            String[] itemBlocks = new String[itemCount];
            // 记录每个商品块的起始和结束位置
            // blockEnd[i] 同时也是 blockStart[i+1]，避免二次查找
            int[] blockEnds = new int[itemCount];

            // 收集所有 Quantity: 的位置
            List<Integer> qtyPositions = new ArrayList<>();
            int scanPos = firstQtyIndex;
            while (scanPos != -1 && qtyPositions.size() < itemCount) {
                qtyPositions.add(scanPos);
                scanPos = remainingText.indexOf("Quantity:", scanPos + 1);
            }

            for (int i = 0; i < itemCount; i++) {
                int currentQtyPos = (i < qtyPositions.size()) ? qtyPositions.get(i) : -1;
                if (currentQtyPos == -1) break;

                int nextQtyPos = (i + 1 < qtyPositions.size()) ? qtyPositions.get(i + 1) : -1;
                int blockStart, blockEnd;

                // 从当前 Quantity: 向前找商品标题的起始位置
                blockStart = findItemBlockStart(remainingText, currentQtyPos);

                if (nextQtyPos != -1) {
                    // 在下一个 Quantity: 之前找结束位置（商品分隔空行处）
                    blockEnd = findItemBlockEnd(remainingText, nextQtyPos);
                    blockEnds[i] = blockEnd;
                } else {
                    // 最后一个商品：结束在文本末尾
                    blockEnd = remainingText.length();
                    blockEnds[i] = blockEnd;
                }

                // 最后一个商品块需要截断 "Do the green thing"
                if (i == itemCount - 1) {
                    int endIndex = remainingText.indexOf("Do the green thing", blockStart);
                    if (endIndex != -1 && endIndex < blockEnd) {
                        blockEnd = endIndex;
                        blockEnds[i] = blockEnd;
                    }
                }

                if (blockStart < blockEnd) {
                    itemBlocks[i] = remainingText.substring(blockStart, blockEnd).trim();
                    items.addAll(parseItemDetail(itemBlocks[i], order.getShopName()));
                }
            }
        } else {
            System.out.println("Debug: No match found for item count."); // 调试输出
            throw new IllegalArgumentException("No valid item count found in text.");
        }
        order.setItemDetails(items);
        order.setAdditionalNote(extractUtil.extractAfter(orderText, "Do the green thing"));

        // 计算 Style 6 标识：只要任何商品（含袖扣、领带夹等）的 Personalization 中含 "Style 6" 即标注
        // 注意：不限制商品类型，袖扣和领带夹都可能含 Style 6
        // 修复：使用 find() + DOTALL 替代 matches()，支持多行文本（含换行符时 .* 可跨行匹配）
        java.util.regex.Pattern style6Pattern = java.util.regex.Pattern.compile(
                "(?i)\\b(S6|Style\\s*6)\\b",
                java.util.regex.Pattern.DOTALL);
        boolean hasStyle6 = items.stream().anyMatch(item -> {
            String p = item.getPersonalization();
            if (p == null || p.trim().isEmpty()) return false;
            boolean matched = style6Pattern.matcher(p).find();
            log.debug("[Style6调试] order={}, personalization={}, matched={}",
                    order.getOrderNumber(), p.length() > 200 ? p.substring(0, 200) + "..." : p, matched);
            return matched;
        });
        order.setHasTieClipStyle6(hasStyle6);

        // 计算静音狗牌 S码标识：存在静音狗牌商品且型号为 S（含 S/M 合并档），排除尼龙狗牌
        boolean hasSilentDogTagS = items.stream().anyMatch(item -> {
            if (item.getOrderType() == com.pdfconverter.constant.OrderType.DOG_TAG) {
                com.pdfconverter.constant.ProductSize size = item.getProductSize();
                if (size == com.pdfconverter.constant.ProductSize.S ||
                        size == com.pdfconverter.constant.ProductSize.SM) {
                    // 排除尼龙狗牌（尼龙的 S/M 合并档不输出标注）
                    com.pdfconverter.constant.ProductName pn = item.getProductName();
                    if (pn != null && pn.name().contains("NYLON")) {
                        return false;
                    }
                    log.debug("[静音狗牌S调试] order={}, product={}, size={}",
                            order.getOrderNumber(),
                            item.getProductName() != null ? item.getProductName().getDisplayName() : "null",
                            size.getSizeCode());
                    return true;
                }
            }
            return false;
        });
        order.setHasSilentDogTagS(hasSilentDogTagS);

        return order;
    }

    /**
     * 找到商品块的起始位置（从 Quantity: 向前找商品标题开始位置）
     *
     * <p>PDF 两列布局中，第 N+1 个商品的标题位于第 N 个商品的 Personalization 行之后：
     * <pre>
     *   ...Personalization: xxx
     *   Shop               ← 左栏固定标记（独立行）
     *   TheVoro [商品标题第一行]  ← ShopName 与商品标题第一行同行
     *   [商品标题续行]
     *   ...
     *   Order date Quantity: 1   ← 第 N+1 个商品的 Quantity
     * </pre>
     * 因此，标题的起始 = "Shop\n" 之后那一行（包含 ShopName 和商品标题第一部分）。
     * getitemTitle() 负责从该行中去掉 ShopName 前缀，提取真正的商品标题。
     * </p>
     */
    private int findItemBlockStart(String text, int quantityPos) {
        int searchStart = Math.max(0, quantityPos - 1000);
        String window = text.substring(searchStart, quantityPos);

        // 策略1：找最后一个 "Shop\n" 独立行，商品标题从 Shop 后的第一行开始
        // 格式：\nShop\n（ShopName+标题首行）\n（标题续行）
        int shopLineIdx = window.lastIndexOf("\nShop\n");
        if (shopLineIdx >= 0) {
            // 商品块从 "Shop\n" 之后开始（ShopName 行包含商品标题的一部分，一起放入块内）
            return searchStart + shopLineIdx + "\nShop\n".length();
        }

        // 策略2（兜底）：找独立 "Shop " 行（Shop 后跟空格）
        int shopSpaceIdx = window.lastIndexOf("\nShop ");
        if (shopSpaceIdx >= 0) {
            int shopLineEnd = window.indexOf('\n', shopSpaceIdx + 1);
            if (shopLineEnd >= 0) {
                return searchStart + shopLineEnd + 1;
            }
        }

        // 策略3（原始逻辑）：从 Quantity: 向前遍历，找到空行或属性标签行
        int currentQtyIndex = window.lastIndexOf("Quantity:");
        if (currentQtyIndex == -1) {
            return searchStart;
        }

        String beforeCurrentQty = window.substring(0, currentQtyIndex);
        String[] lines = beforeCurrentQty.split("\n", -1);

        int titleStartLine = lines.length;

        for (int i = lines.length - 1; i >= 0; i--) {
            String line = lines[i].trim();

            if (line.isEmpty()) {
                titleStartLine = i + 1;
                break;
            }

            if (line.startsWith("Ship to") || line.startsWith("Scheduled to ship by") ||
                    line.startsWith("Shop") || line.startsWith("Order date") ||
                    line.startsWith("Payment method") || line.startsWith("Shipping method") ||
                    line.startsWith("Packaging") || line.startsWith("Tracking")) {
                titleStartLine = i + 1;
                break;
            }

            if (line.contains(": ")) {
                String beforeColon = line.substring(0, line.indexOf(": ")).trim();
                if (isAttributeLabel(beforeColon)) {
                    titleStartLine = i + 1;
                    break;
                }
            }

            titleStartLine = i;
        }

        int charPos = 0;
        for (int i = 0; i < titleStartLine && i < lines.length; i++) {
            charPos += lines[i].length() + 1;
        }

        return searchStart + charPos;
    }

    /**
     * 判断是否为属性标签（用于区分属性行和商品标题）
     */
    private boolean isAttributeLabel(String text) {
        String[] attributeLabels = {
                "Color", "Size", "Color and Size", "Color Finish", "Color Finish and Box",
                "Box Options", "Item Options", "Item", "Add-on Box", "Design Options",
                "Engraving Sides", "Customization Option", "Number and Size of Discs",
                "Wooden Case", "Quantity", "Personalization", "Font", "Style",
                "Silicone Rubber Holder Color", "Additional Add-on Engraving"
        };

        for (String label : attributeLabels) {
            if (text.equalsIgnoreCase(label) || text.toLowerCase().startsWith(label.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 找到商品块的结束位置（在下一个 Quantity: 之前）
     * 简单策略：直接返回 nextQuantityPos 位置，让商品块在下一个 Quantity: 之前结束
     */
    private int findItemBlockEnd(String text, int nextQuantityPos) {
        return nextQuantityPos;
    }

    /**
     * 解析单个商品详情（重构后）
     * 使用AttributeRuleEngine从配置中提取属性，替换硬编码逻辑
     */
    private List<ItemDetail> parseItemDetail(String block, String shopName) throws IOException {
        // 1. 提取商品标题
        String itemTitle = extractUtil.getitemTitle(block);
        // 如果标题以 ShopName 开头（PDF 两列中 ShopName 与标题同行），截掉 ShopName 前缀
        if (shopName != null && !shopName.isEmpty() && itemTitle.startsWith(shopName + " ")) {
            itemTitle = itemTitle.substring(shopName.length()).trim();
        } else if (shopName != null && !shopName.isEmpty() && itemTitle.startsWith(shopName)) {
            itemTitle = itemTitle.substring(shopName.length()).trim();
        }
        // 2. 提取订购数量
        int quantity = extractUtil.extractLineAfter(block, "Quantity:");

        // 3. 提取Personalization内容
        String personalization = extractUtil.getPersonalization(block);

        // 4. 提取动态属性（原始文本 + Map形式）
        // 先尝试从 Quantity: 到 Personalization: 之间提取
        String dynamicSection = extractUtil.extractBetween(block, "Quantity:", "Personalization:").trim();
        // 若没有 Personalization: 行（如纯礼盒listing），则从 Quantity: 后截取剩余内容，并过滤噪音行
        if (dynamicSection.isEmpty()) {
            int qtyIdx = block.indexOf("Quantity:");
            if (qtyIdx != -1) {
                String afterQty = block.substring(qtyIdx + "Quantity:".length()).trim();
                StringBuilder dynBuilder = new StringBuilder();
                for (String dynLine : afterQty.split("\n")) {
                    String trimmed = dynLine.trim();
                    if (trimmed.isEmpty()) continue;
                    // 过滤数字数量行（Quantity: 1 已被提取，这里过滤纯数字行）
                    if (trimmed.matches("^\\d+$")) continue;
                    // 遇到左栏边界标记，停止（后续内容不属于本商品的动态属性）
                    if (trimmed.matches("(?i)Scheduled to ship by.*")) break;
                    if (trimmed.equals("Shop") || trimmed.startsWith("Order date") ||
                            trimmed.startsWith("Payment method") || trimmed.startsWith("Shipping method") ||
                            trimmed.startsWith("Packaging") || trimmed.startsWith("Tracking")) break;
                    if (trimmed.matches("(?i)(Do the green thing).*")) break;
                    // 过滤纯地址行（仅包含数字+字母，如邮编/州名/国家名）
                    if (trimmed.matches("[A-Z]{2}\\s+\\d{5}.*")) continue;  // 州 邮编
                    if (trimmed.equalsIgnoreCase("United States") || trimmed.equalsIgnoreCase("Canada")) continue;
                    if (dynBuilder.length() > 0) dynBuilder.append("\n");
                    dynBuilder.append(trimmed);
                }
                dynamicSection = dynBuilder.toString().trim();
            }
        }
        Map<String, String> dynamicAttrsMap = extractUtil.parseDynamicAttributes(dynamicSection.split("\n"));

        // 订购完全信息：动态属性原始文本 + Personalization 原样拼接
        String fullOrderInfo = buildFullOrderInfo(dynamicSection, personalization);

        log.debug("商品标题：{}", itemTitle);
        log.debug("动态属性：{}", dynamicAttrsMap);

        // 5. 识别产品类型
        ItemDetail mainItemDetail = productTitleRecognitionService.identifyProductType(itemTitle);
        mainItemDetail.setItemQuantity(quantity);
        mainItemDetail.setPersonalization(personalization);
        mainItemDetail.setItemTitle(itemTitle);
        // 订购完全信息存入dynamicAttributes字段
        mainItemDetail.setDynamicAttributes(fullOrderInfo);

        // 6. 使用规则引擎提取属性（包含颜色、尺寸、附属商品列表）
        // 注意：配置文件中用的是 nameCode（英文），需要用 getNameCode() 而非 getDisplayName()
        String listingId = mainItemDetail.getListingId();
        String productNameCode = mainItemDetail.getProductName() != null ?
                mainItemDetail.getProductName().getNameCode() : null;
        String productNameDisplay = mainItemDetail.getProductName() != null ?
                mainItemDetail.getProductName().getDisplayName() : null;

        ProductAttribute productAttribute;
        if (productNameCode != null && !productNameCode.isEmpty()) {
            productAttribute = attributeRuleEngine.extractAttributes(listingId, productNameCode, dynamicAttrsMap);
        } else {
            productAttribute = attributeRuleEngine.extractAttributes(listingId, "UNKNOWN", dynamicAttrsMap);
        }

        log.info("产品[{}({})]属性提取结果: listingId={}, color={}, size={}, accessories={}",
                productNameDisplay, productNameCode, listingId,
                productAttribute.getColor(), productAttribute.getSize(),
                productAttribute.getAdditionalProductNames());

        // 7. 将提取的属性应用到mainItemDetail
        applyProductAttribute(mainItemDetail, productAttribute, personalization);

        // 8. 组装附属产品列表（TieClip、Box 等）
        List<ItemDetail> combineItemDetailList = assembleItemDetailListForVoro(
                dynamicAttrsMap, mainItemDetail, productAttribute);

        // 9. 组装临时列表（主商品 + 附属商品）
        List<ItemDetail> tempItemList = new ArrayList<>();
        tempItemList.add(mainItemDetail);
        tempItemList.addAll(combineItemDetailList);

        // 10. 根据规则生成附加商品（Add-ons）
        List<ItemDetail> addOnItemList = accessoryItemFactory.supplementAddOns(tempItemList);

        // 11. 组装最终列表
        return assembleItemDetailList(mainItemDetail, combineItemDetailList, null, addOnItemList);
    }

    /**
     * 构建订购完全信息：动态属性（过滤噪音行）+ Personalization
     * 过滤掉 Quantity: X、Scheduled to ship by...、日期行等非属性内容
     */
    private String buildFullOrderInfo(String dynamicSection, String personalization) {
        StringBuilder sb = new StringBuilder();
        if (dynamicSection != null && !dynamicSection.trim().isEmpty()) {
            // 按行过滤噪音
            for (String line : dynamicSection.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                // 过滤：Quantity: X
                if (trimmed.matches("(?i)Quantity:\\s*\\d+")) continue;
                // 过滤：Scheduled to ship by...
                if (trimmed.matches("(?i)Scheduled to ship by.*")) continue;
                // 过滤：纯日期行（如 Oct 25, 2025）
                if (trimmed.matches("[A-Za-z]{3}\\s+\\d{1,2},\\s+\\d{4}")) continue;
                // 过滤：纯数字行（如残余的"1"）
                if (trimmed.matches("\\d+")) continue;
                // 过滤：Engraving Fee / Additional Add-on Engraving（照片设计服务，不需要输出）
                if (trimmed.matches("(?i).*[Ee]ngraving\\s+[Ff]ee.*")) continue;
                if (trimmed.matches("(?i).*[Aa]dditional\\s+[Aa]dd-[Oo]n.*[Ee]ngraving.*")) continue;
                // 遇到 PDF 左栏边界标记，停止（后续内容属于订单基本信息或下一商品标题）
                if (trimmed.equals("Shop") || trimmed.startsWith("Order date") ||
                        trimmed.startsWith("Payment method") || trimmed.startsWith("Shipping method") ||
                        trimmed.startsWith("Packaging") || trimmed.startsWith("Tracking")) {
                    break;
                }
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
            }
        }
        if (personalization != null && !personalization.trim().isEmpty()) {
            if (sb.length() > 0) sb.append("\n");
            sb.append(personalization.trim());
        }
        return sb.toString();
    }

    /**
     * 从Personalization中提取样式
     */
    private String extractStyleFromPersonalization(String personalization) {
        if (personalization == null || personalization.isEmpty()) {
            return null;
        }
        // 简化实现：从Personalization中提取样式信息
        // 实际实现可能需要更复杂的逻辑
        return null;
    }

    /**
     * 从Personalization中提取字体
     */
    private String extractFontFromPersonalization(String personalization) {
        if (personalization == null || personalization.isEmpty()) {
            return null;
        }
        // 简化实现：从Personalization中提取字体信息
        // 实际实现可能需要更复杂的逻辑
        return null;
    }

    /**
     * 将ProductAttribute应用到ItemDetail
     * @param personalization 个性化文本，用于兜底提取 style/font
     */
    private void applyProductAttribute(ItemDetail itemDetail, ProductAttribute productAttribute, String personalization) {
        // 设置颜色
        if (productAttribute.getColor() != null) {
            itemDetail.setProductColor(productAttribute.getColor());
        }

        // 设置尺寸
        if (productAttribute.getSize() != null) {
            itemDetail.setProductSize(productAttribute.getSize());
        }

        // 设置产品变量
        if (productAttribute.getProductVariable() != null && !productAttribute.getProductVariable().isEmpty()) {
            String varStr = productAttribute.getProductVariable();
            // 尝试转换为ProductVariable枚举（使用extractFromText支持"10月"等数字格式）
            com.pdfconverter.constant.ProductVariable pv = com.pdfconverter.constant.ProductVariable.extractFromText(varStr);
            if (pv != com.pdfconverter.constant.ProductVariable.UNKNOWN) {
                itemDetail.setProductVariable(pv);
            }
        }

        // 特殊场景：对于 BOX 类型主商品（如 box_addon listing），
        // ACCESSORY_ITEMS 解析出的附属商品 boxVariable 会与主商品类型相同而被跳过，
        // 因此在这里直接把 additionalBoxVariables 中第一个有效值赋给主商品自身的 productVariable。
        if (itemDetail.getOrderType() == OrderType.BOX
                && (itemDetail.getProductVariable() == null || itemDetail.getProductVariable() == com.pdfconverter.constant.ProductVariable.UNKNOWN)
                && productAttribute.getAdditionalBoxVariables() != null
                && !productAttribute.getAdditionalBoxVariables().isEmpty()) {
            String boxVar = productAttribute.getAdditionalBoxVariables().get(0);
            if (boxVar != null && !boxVar.isEmpty()) {
                com.pdfconverter.constant.ProductVariable pv = com.pdfconverter.constant.ProductVariable.fromDisplayName(boxVar);
                if (pv != com.pdfconverter.constant.ProductVariable.UNKNOWN) {
                    itemDetail.setProductVariable(pv);
                    log.debug("BOX主商品直接继承ACCESSORY_ITEMS boxVariable: {}", boxVar);
                }
            }
        }

        // 设置字体（ItemDetail构造器初始化为空字符串""，不能用!=null判断，要用isEmpty）
        String extractedStyle = PersonalizationParserUtil.extractStyle(personalization);
        String extractedFont = PersonalizationParserUtil.extractFont(personalization);
        if (!productAttribute.getFont().isEmpty()) {
            itemDetail.setFont(productAttribute.getFont());
        } else if (extractedFont != null) {
            itemDetail.setFont(extractedFont);
        }

        // 设置样式
        if (!productAttribute.getStyle().isEmpty()) {
            itemDetail.setStyle(productAttribute.getStyle());
        } else if (extractedStyle != null) {
            itemDetail.setStyle(extractedStyle);
        }
    }

    /**
     * 组装最终的产品列表
     * */
    private List<ItemDetail> assembleItemDetailList(ItemDetail mainItemDetail, List<ItemDetail> combineIitemDetailList, ItemDetail addOnItemDetail, List<ItemDetail> addOnItemList) {
        List<ItemDetail> itemDetailList = new ArrayList<>();
        //添加主产品
        itemDetailList.add(mainItemDetail);
        //添加附属产品(TieClip、Box等)
        if (combineIitemDetailList != null) {
            itemDetailList.addAll(combineIitemDetailList);
        }
        //添加默认添加的商品(旧版本兼容)
        if (addOnItemDetail != null && addOnItemDetail.getOrderType() != null) {
            itemDetailList.add(addOnItemDetail);
        }
        //添加规则生成的附加商品(Add-ons)
        if (addOnItemList != null && !addOnItemList.isEmpty()) {
            itemDetailList.addAll(addOnItemList);
        }
        return itemDetailList;
    }
    /**
     * 组装附属产品列表（从 Item: Cufflink+TieClip+Box 中提取，排除主商品类型）
     *
     * @param dynamicAttrsMap  动态属性Map
     * @param mainItemDetail   主商品实体
     * @param productAttribute 产品属性实体（已从规则引擎提取）
     * @return 附属产品列表（不含主商品），如果没有附属商品则返回空列表
     */
    private List<ItemDetail> assembleItemDetailListForVoro(Map<String, String> dynamicAttrsMap,
                                                           ItemDetail mainItemDetail,
                                                           ProductAttribute productAttribute) {
        List<ItemDetail> itemDetailList = new ArrayList<>();

        List<String> allProductTypeCodes = productAttribute.getAdditionalProductNames();
        if (allProductTypeCodes == null || allProductTypeCodes.isEmpty()) {
            return itemDetailList; // 没有附属商品
        }

        List<String> allProductNameCodes = productAttribute.getAdditionalProductNameCodes();
        List<String> allBoxVariables = productAttribute.getAdditionalBoxVariables();
        List<String> allAdditionalSizes = productAttribute.getAdditionalSizes();

        // 主商品的 OrderType，用于过滤（避免主商品重复输出）
        OrderType mainOrderType = mainItemDetail.getOrderType();

        log.debug("全部 Item 商品类型：{}，主商品类型：{}", allProductTypeCodes, mainOrderType);

        for (int i = 0; i < allProductTypeCodes.size(); i++) {
            String productTypeCode = allProductTypeCodes.get(i);
            String productNameCode = (allProductNameCodes != null && i < allProductNameCodes.size())
                    ? allProductNameCodes.get(i) : "";
            String boxVariable = (allBoxVariables != null && i < allBoxVariables.size())
                    ? allBoxVariables.get(i) : "";
            String additionalSize = (allAdditionalSizes != null && i < allAdditionalSizes.size())
                    ? allAdditionalSizes.get(i) : "";

            OrderType orderType = OrderType.fromOrderTypeCode(productTypeCode);
            if (orderType == OrderType.UNKNOWN) {
                log.warn("无法识别附属商品类型：{}", productTypeCode);
                continue;
            }

            // 跳过与主商品相同的类型（主商品已经单独输出，不需要重复）
            if (orderType == mainOrderType) {
                log.debug("跳过主商品类型：{}", orderType);
                continue;
            }

            ItemDetail item = new ItemDetail();
            item.setItemTitle(mainItemDetail.getItemTitle());
            item.setItemQuantity(mainItemDetail.getItemQuantity());
            item.setOrderType(orderType);
            item.setPersonalization(mainItemDetail.getPersonalization());
            // 订购完全信息与主商品一致（同一个订单行的信息）
            item.setDynamicAttributes(mainItemDetail.getDynamicAttributes());

            // 设置 ProductName（用于查产品清单细类）
            if (productNameCode != null && !productNameCode.isEmpty()) {
                ProductName pn = ProductName.fromNameCode(productNameCode);
                if (pn != ProductName.UNKNOWN) {
                    item.setProductName(pn);
                }
            }
            // 注意：不再根据 orderType==TIE_CLIP 盲目继承主商品ProductName
            // 因为非combo场景下主商品可能是Cufflink，继承会导致TieClip被错误识别为袖扣

            // 根据产品类型设置属性继承规则
            if (orderType == OrderType.BOX) {
                // 包装盒：无尺寸/颜色/字体/样式，产品变量 = 具体盒型（如"Oval Box-椭圆形开窗木盒"）
                // 优先用从 boxVariableMapping 解析出的变量
                String resolvedBoxVar = (boxVariable != null && !boxVariable.isEmpty())
                        ? boxVariable : productAttribute.getProductVariable();
                if (resolvedBoxVar != null && !resolvedBoxVar.isEmpty()) {
                    item.setProductVariable(com.pdfconverter.constant.ProductVariable.fromDisplayName(resolvedBoxVar));
                }
            } else {
                // 领带夹等其他附属商品：继承主商品颜色。
                // 尺寸规则：优先附属独立尺寸，其次按品类默认（领带夹默认L），最后才回退主商品尺寸。
                if (additionalSize != null && !additionalSize.isEmpty()) {
                    com.pdfconverter.constant.ProductSize size = com.pdfconverter.constant.ProductSize.fromSizeCode(additionalSize);
                    if (size != com.pdfconverter.constant.ProductSize.UNKNOWN) {
                        item.setProductSize(size);
                    } else if (orderType == OrderType.TIE_CLIP) {
                        item.setProductSize(com.pdfconverter.constant.ProductSize.L);
                    } else {
                        item.setProductSize(productAttribute.getSize());
                    }
                } else if (orderType == OrderType.TIE_CLIP) {
                    item.setProductSize(com.pdfconverter.constant.ProductSize.L);
                } else {
                    item.setProductSize(productAttribute.getSize());
                }
                item.setProductColor(productAttribute.getColor());
                String productVariable = productAttribute.getProductVariable();
                if (productVariable != null && !productVariable.isEmpty()) {
                    item.setProductVariable(com.pdfconverter.constant.ProductVariable.fromDisplayName(productVariable));
                }
                item.setStyle(productAttribute.getStyle());
                item.setFont(productAttribute.getFont());
            }

            log.info("添加附属商品：{} ({}), productName={}",
                    orderType.getDisplayName(), orderType.getOrderTypeCode(), item.getProductName());
            itemDetailList.add(item);
        }

        return itemDetailList;
    }
    /**
     * 将ItemDetail转换为ProductItem
     */
//    private ProductItem convertToProductItem(ItemDetail item) {
//        ProductItem productItem = new ProductItem();
//        productItem.setSourceTitle(item.getItemTitle());
//        productItem.setProductType(item.getOrderType());
//        productItem.setSize(item.getProductSize());
//        productItem.setColor(item.getProductColor());
//        productItem.setVariable(item.getProductVariable());
//        productItem.setQuantity(item.getItemQuantity());
//        productItem.setPersonalization(item.getPersonalization());
//        productItem.setFont(item.getFont());
//        productItem.setStyle(item.getStyle());
//        productItem.setMainProduct(true);
//        return productItem;
//    }

    /**
     * 将OrderContext中的附属产品转换为ItemDetail
     */
//    private List<ItemDetail> convertAccessoriesToItemDetail(OrderContext orderContext, List<ItemDetail> mainItems) {
//        List<ItemDetail> accessoryDetails = new ArrayList<>();
//
//        for (ProductItem accessory : orderContext.getAccessories()) {
//            ItemDetail detail = new ItemDetail();
//            detail.setItemTitle(accessory.getSourceTitle());
//            detail.setItemQuantity(accessory.getQuantity());
//            detail.setOrderType(accessory.getProductType());
//            detail.setProductSize(accessory.getSize());
//            detail.setProductColor(accessory.getColor() );
//            detail.setProductVariable(accessory.getVariable() );
//            detail.setPersonalization(accessory.getPersonalization());
//            detail.setFont(accessory.getFont());
//            detail.setStyle(accessory.getStyle());
//            detail.setDynamicAttributes("");
//
//            accessoryDetails.add(detail);
//        }
//
//        return accessoryDetails;
//    }




//    private ItemDetail addDefaultBoxItem(int quantity, Set<String> productTypes) {
//        ItemDetail defaultBoxItem = new ItemDetail();
//        defaultBoxItem.setItemTitle("");
//        defaultBoxItem.setItemQuantity(quantity);
//        defaultBoxItem.setDynamicAttributes("");
//        defaultBoxItem.setPersonalization("");
//        defaultBoxItem.setOrderType(OrderType.BOX);
//        // 包装盒不设置型号、颜色、设计风格、字体
//        defaultBoxItem.setProductSize(null);
//        defaultBoxItem.setProductColor(null);
//        defaultBoxItem.setFont(null);
//        defaultBoxItem.setStyle(null);
//
//        // 根据产品类型设置产品变量
//        if (productTypes.contains(getCufflinkName())) {
//            defaultBoxItem.setProductVariable(getSmallSquareBoxName());
//        } else if (productTypes.contains(getTieClipName())) {
//            defaultBoxItem.setProductVariable(getRectangleBoxName());
//        }
//        return defaultBoxItem;
//    }
//
//    private ItemDetail addBaseChainItem(String itemTitle, int quantity, String productColor) {
//        ItemDetail baseChainItem = new ItemDetail();
//        baseChainItem.setItemTitle(itemTitle);
//        baseChainItem.setItemQuantity(quantity); // 数量同原商品
//        baseChainItem.setDynamicAttributes("");
//        baseChainItem.setPersonalization("");
//        baseChainItem.setOrderType(OrderType.BASE_CHAIN); // 产品名称为基础链
//        baseChainItem.setProductVariable(getFullChainName()); // 产品变量为全链
//        baseChainItem.setProductSize(""); // 继承型号
//        baseChainItem.setProductColor(productColor); // 继承颜色
//        baseChainItem.setFont(""); // 继承字体
//        baseChainItem.setStyle(""); // 继承设计风格
//        return baseChainItem;
//    }
//
//    private Map<String, String> getDynamicInfo(String[] dynamicSectionInfo) {
//        Map<String, String> dynamicAttrsMap = new HashMap<>();
//        for (String line : dynamicSectionInfo) {
//            line = line.trim();
//            if (line.isEmpty()) continue;
//            // 尝试解析 key: value
//            int colon = line.indexOf(":");
//            if (colon != -1) {
//                String key = line.substring(0, colon).trim();
//                String value = line.substring(colon + 1).trim();
//                dynamicAttrsMap.put(key, value);
//            } else {
//                // 无冒号的行，作为上一个 key 的延续（罕见情况）
//                if (!dynamicAttrsMap.isEmpty()) {
//                    String lastKey = new ArrayList<>(dynamicAttrsMap.keySet()).get(dynamicAttrsMap.size() - 1);
//                    dynamicAttrsMap.put(lastKey, dynamicAttrsMap.get(lastKey) + " " + line);
//                }
//            }
//        }
//        return dynamicAttrsMap;
//    }

}