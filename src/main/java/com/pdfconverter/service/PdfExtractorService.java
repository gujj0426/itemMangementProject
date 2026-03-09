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
import com.pdfconverter.service.mapper.ProductNameMappingService;
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
    @Resource
    private ProductTitleRecognitionService productTitleRecognitionService;
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
     * 解析方法
     *
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
        Map<String, String> dynamicAttrsMap = extractUtil.parseDynamicAttributes(dynamicSection.split("\n"));
        // 5. 建立当前商品的主商品实体 ItemDetail
        ItemDetail mainItemDetail = productTitleRecognitionService.identifyProductType(itemTitle);
        // 5.1 解析所有动态属性，提取主商品的Size、Color，拆分附属商品信息,返回附加商品类型列表
        ProductAttribute productAttribute = attributeExtractor.parseVoroDynamicInfo(dynamicAttrsMap, mainItemDetail, personalization);
        // 5.1 调用给mainItemDetail赋值函数补充型号、颜色、产品变量、设计风格、字体等信息
        ItemDetailAdditionalUtil.additionalDetail(productAttribute, mainItemDetail);
        // 6. 组装附属产品列表
        List<ItemDetail> combineIitemDetailList = assembleItemDetailListForVoro(dynamicAttrsMap, mainItemDetail, productAttribute);

        // 8. 补充默认添加的商品，比如心形相盒默认搭配一个同色系的基础链
        ItemDetail addOnItemDetail = productTitleRecognitionService.identifyProductType(itemTitle);

        // 9. 添加返回列表
        return assembleItemDetailList(mainItemDetail, combineIitemDetailList, addOnItemDetail);
    }

    /**
     * 组装最终的产品列表
     * */
    private List<ItemDetail> assembleItemDetailList(ItemDetail mainItemDetail, List<ItemDetail> combineIitemDetailList, ItemDetail addOnItemDetail) {
        List<ItemDetail> itemDetailList = new ArrayList<>();
        //添加主产品
        itemDetailList.add(mainItemDetail);
        //添加附加产品
        if (combineIitemDetailList != null) {
            itemDetailList.addAll(combineIitemDetailList);
        }
        //添加默认添加的商品
        if (addOnItemDetail != null && addOnItemDetail.getOrderType() != null) {
            itemDetailList.add(addOnItemDetail);
        }
        return itemDetailList;
    }
    /**
     *  组装附属产品列表
     *  @param dynamicAttrsMap 动态属性Map
     *  @param mainItemDetail 主商品实体
     *  @param productAttribute 产品属性实体
     *  @return 附属产品列表
     * */
    private List<ItemDetail> assembleItemDetailListForVoro(Map<String, String> dynamicAttrsMap, ItemDetail mainItemDetail,
                                                           ProductAttribute productAttribute) {
        List<ItemDetail> itemDetailList = new ArrayList<>();

        // 使用 ProductAttribute 中的产品类型列表
        Set<String> productTypes = new LinkedHashSet<>();
        if (productAttribute.getAdditionalProductNames() != null && !productAttribute.getAdditionalProductNames().isEmpty()) {
            productTypes.addAll(productAttribute.getAdditionalProductNames());
        }

        // 如果没有附属商品，则使用主商品类型
        if (productTypes.isEmpty()) {
            return null;
        }

        // 循环读取dynamicAttrsMap
        for (String key : dynamicAttrsMap.keySet()){
            String value = dynamicAttrsMap.get(key);
            // 如果productTypes中包含花卉心形相盒吊坠，解析月份
            String flowerHeartBoxName = ProductName.FLOWER_HEART_BOX_PENDANT.getDisplayName();
            if (productAttribute.getAdditionalProductNames() != null
                    && productAttribute.getAdditionalProductNames().contains(flowerHeartBoxName)
                    && key.toLowerCase().contains("birth flower style")) {
                String month = attributeExtractor.getMonthFromValue(value);
                if (month != null && !month.isEmpty()) {
                    productAttribute.setProductVariable(month);
                }
            }

        }
        // 循环productTypes，生成ItemDetail列表,每个产品都有7个属性，包括订单类别productType
        //产品名称productName、尺寸productSize、颜色productColor、产品变量productVariable、设计风格style、字体font
        //附属产品的尺寸productSize、颜色productColor、产品变量productVariable特定品类需要继承主商品的属性，比如领带夹会继承主商品
        //有些产品没有尺寸、颜色、产品变量、设计风格、字体，比如包装盒
        //规则：主商品是领带夹、袖扣、相盒，则继承主商品的属性，否则使用自己的属性，如果没有则为空
        //
        for (String productType : productTypes) {
            OrderType orderType = OrderType.fromOrderTypeCode(productType);
            if (orderType == OrderType.UNKNOWN) {
                continue;
            }
            ItemDetail item = new ItemDetail();
            item.setItemTitle(mainItemDetail.getItemTitle());
            item.setItemQuantity(mainItemDetail.getItemQuantity());
            item.setOrderType(orderType);

            // 根据产品类型设置特殊字段
            if (orderType == OrderType.BOX) {
                // 包装盒的特殊处理：只有数量，没有型号、颜色、设计风格、字体
                String productVariable = productAttribute.getProductVariable();
                if (productVariable != null && !productVariable.isEmpty()) {
                    item.setProductVariable(ProductVariable.fromDisplayName(productVariable));
                }

            } else {
                // 其他产品类型（袖扣、领带夹、狗牌、相盒）保留原有字段
                item.setProductSize(productAttribute.getSize());
                item.setProductColor(productAttribute.getColor());
                String productVariable = productAttribute.getProductVariable();
                if (productVariable != null && !productVariable.isEmpty()) {
                    item.setProductVariable(ProductVariable.fromDisplayName(productVariable));
                }
                item.setStyle(productAttribute.getStyle());
                item.setFont(productAttribute.getFont());
            }

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