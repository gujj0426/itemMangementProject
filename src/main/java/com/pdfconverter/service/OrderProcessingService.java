package com.pdfconverter.service;

import com.pdfconverter.config.AccessoryRuleConfig;
import com.pdfconverter.config.AddonRuleConfig;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.OrderContext;
import com.pdfconverter.model.ProductItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 订单处理服务
 * 主流程：
 * 1. 识别产品标题，确定产品类型
 * 2. 提取动态属性（型号、颜色、变量等）
 * 3. 映射为标准化值
 * 4. 解析附属产品
 * 5. 应用附加产品规则
 */
@Service
public class OrderProcessingService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProcessingService.class);

    @Resource
    private ProductTitleRecognitionService titleRecognitionService;

    @Resource
    private AttributeExtractor attributeExtractor;

    @Resource
    private ColorMapperService colorMapper;

    @Resource
    private ProductVariableMapperService productVariableMapper;

    @Resource
    private ProductSizeMapperService productSizeMapper;

    @Resource
    private ProductNameMapper productNameMapper;

    @Resource
    private AccessoryRuleConfig accessoryRuleConfig;

    @Resource
    private AddonRuleConfig addonRuleConfig;

    /**
     * 主处理流程
     * @param orderText 订单文本
     * @return 订单上下文
     */
    public OrderContext processOrder(String orderText) {
        OrderContext context = new OrderContext();

        try {
            logger.info("开始处理订单...");

            // 1. 提取订单号
            String orderNumber = extractOrderNumber(orderText);
            context.setOrderNumber(orderNumber);
            logger.info("订单号: {}", orderNumber);

            // 2. 解析产品块
            List<ProductItem> mainItems = parseProductItems(orderText, context);

            // 3. 添加到上下文
            context.addItems(mainItems);

            logger.info("识别到 {} 个主产品", mainItems.size());
            for (ProductItem item : mainItems) {
                logger.info("  - {}", item.getSummary());
            }

            // 4. 应用附加产品规则
            applyAddonRules(context);

            logger.info("附加产品规则应用完成，总计 {} 个产品项", context.getTotalItemCount());

            logger.info("订单处理完成");
            logger.debug("订单摘要:\n{}", context.getSummary());

        } catch (Exception e) {
            logger.error("订单处理失败", e);
        }

        return context;
    }

    /**
     * 从订单文本中提取订单号
     */
    private String extractOrderNumber(String orderText) {
        if (orderText == null) {
            return "UNKNOWN";
        }

        // 尝试匹配订单号格式（根据实际订单格式调整）
        Pattern orderNumberPattern = Pattern.compile("订单[:：]?\\s*([A-Za-z0-9-]+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = orderNumberPattern.matcher(orderText);
        if (matcher.find()) {
            return matcher.group(1);
        }

        // 如果找不到订单号，返回默认值
        return "UNKNOWN-" + System.currentTimeMillis();
    }

    /**
     * 解析产品项
     */
    private List<ProductItem> parseProductItems(String orderText, OrderContext context) {
        List<ProductItem> items = new ArrayList<>();

        if (orderText == null || orderText.trim().isEmpty()) {
            return items;
        }

        // 1. 识别产品块（假设每个产品块由标题和属性组成）
        List<String> productBlocks = identifyProductBlocks(orderText);

        for (String block : productBlocks) {
            try {
                ProductItem item = parseSingleProductBlock(block, context);
                if (item != null) {
                    items.add(item);
                }
            } catch (Exception e) {
                logger.error("解析产品块失败: {}", block, e);
            }
        }

        return items;
    }

    /**
     * 识别产品块
     * 简单实现：根据标题和属性结构分割
     */
    private List<String> identifyProductBlocks(String orderText) {
        List<String> blocks = new ArrayList<>();

        // 简单实现：按标题模式分割
        // 假设产品标题通常包含在特定格式中（需要根据实际订单格式调整）
        Pattern productPattern = Pattern.compile("(?i)(Product|Item|商品|产品)\\s*[:：]?\\s*([^\\n]+(?:\\n[^\\n]+)*)");
        Matcher matcher = productPattern.matcher(orderText);

        while (matcher.find()) {
            String block = matcher.group(2).trim();
            if (!block.isEmpty()) {
                blocks.add(block);
            }
        }

        // 如果没有匹配到，将整个文本作为一个块
        if (blocks.isEmpty()) {
            blocks.add(orderText);
        }

        return blocks;
    }

    /**
     * 解析单个产品块
     */
    private ProductItem parseSingleProductBlock(String block, OrderContext context) {
        ProductItem item = new ProductItem();
        item.setSourceTitle(block);

        // 1. 识别产品类型
        OrderType productType = titleRecognitionService.recognizeProductType(block);
        item.setProductType(productType);

        // 2. 识别产品名称（中文）
        ProductName productName = productNameMapper.mapFromTitle(block, productType);
        item.setProductName(productName);

        // 3. 提取动态属性
        item.setDynamicAttributes(attributeExtractor.extractAttributes(block, productType));

        // 4. 映射颜色
        String colorRaw = item.getDynamicAttribute("color");
        if (colorRaw != null) {
            ProductColor color = colorMapper.mapColor(colorRaw);
            item.setColor(color);
        }

        // 5. 映射型号
        String sizeRaw = item.getDynamicAttribute("size");
        if (sizeRaw != null) {
            ProductSize size = productSizeMapper.mapSize(sizeRaw);
            item.setSize(size);
        }

        // 6. 映射产品变量
        String variableRaw = item.getDynamicAttribute("variable");
        if (variableRaw != null) {
            ProductVariable variable = productVariableMapper.mapVariable(variableRaw);
            item.setVariable(variable);
        }

        // 7. 提取个性化信息
        String personalization = item.getDynamicAttribute("personalization");
        if (personalization != null) {
            item.setPersonalization(personalization);
        }

        // 8. 提取样式
        String style = item.getDynamicAttribute("style");
        if (style != null) {
            item.setStyle(style);
        }

        // 9. 提取字体
        String font = item.getDynamicAttribute("font");
        if (font != null) {
            item.setFont(font);
        }

        // 10. 解析附属产品（如果有）
        parseAccessoryProducts(item, context);

        return item;
    }

    /**
     * 解析附属产品
     */
    private void parseAccessoryProducts(ProductItem mainItem, OrderContext context) {
        if (mainItem.getProductType() == null) {
            return;
        }

        // 检查该产品类型是否可以包含附属产品
        if (!accessoryRuleConfig.canHaveAccessories(mainItem.getProductType())) {
            return;
        }

        // 获取该产品类型的附属产品规则
        List<AccessoryRuleConfig.AccessoryProductRule> rules =
                accessoryRuleConfig.getAccessoryRulesByMainType(mainItem.getProductType());

        for (AccessoryRuleConfig.AccessoryProductRule rule : rules) {
            try {
                // 检查是否需要包含该附属产品
                if (shouldIncludeAccessory(mainItem, rule)) {
                    ProductItem accessory = createAccessoryProduct(mainItem, rule);
                    context.addItem(accessory);
                    logger.info("  + 附属产品: {}", accessory.getSummary());
                }
            } catch (Exception e) {
                logger.error("创建附属产品失败", e);
            }
        }
    }

    /**
     * 判断是否需要包含附属产品
     */
    private boolean shouldIncludeAccessory(ProductItem mainItem, AccessoryRuleConfig.AccessoryProductRule rule) {
        if (rule == null || rule.getTriggerCondition() == null) {
            return false;
        }

        String condition = rule.getTriggerCondition();

        // 如果条件为空或包含"always"，则总是包含
        if (condition == null || condition.isEmpty() ||
                "always".equalsIgnoreCase(condition) ||
                "总是".equals(condition)) {
            return true;
        }

        // 如果条件包含标题关键词
        if (mainItem.getSourceTitle() != null) {
            String title = mainItem.getSourceTitle().toLowerCase();
            if (title.contains(condition.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    /**
     * 创建附属产品项
     */
    private ProductItem createAccessoryProduct(ProductItem mainItem, AccessoryRuleConfig.AccessoryProductRule rule) {
        ProductItem accessory = new ProductItem();
        accessory.setMainProduct(false);
        accessory.setParentItemId(mainItem.getId());

        // 设置附属产品类型
        if (rule.getAccessoryType() != null) {
            accessory.setProductType(rule.getAccessoryType());
        }

        // 设置数量
        int quantity = rule.getDefaultQuantity() != null ? rule.getDefaultQuantity() : 1;
        accessory.setQuantity(quantity);

        // 继承属性配置
        boolean inheritColor = rule.isInheritColor() != null && rule.isInheritColor();
        boolean inheritSize = rule.isInheritSize() != null && rule.isInheritSize();
        boolean inheritVariable = rule.isInheritVariable() != null && rule.isInheritVariable();

        accessory.inheritAttributesFrom(mainItem, inheritColor, inheritSize, inheritVariable);

        // 设置产品名称
        if (accessory.getProductType() != null) {
            accessory.setProductName(ProductName.fromOrderType(accessory.getProductType()));
        }

        // 设置原始标题
        String title = rule.getAccessoryType() != null ?
                rule.getAccessoryType().getDisplayName() : "附属产品";
        accessory.setSourceTitle(title);

        return accessory;
    }

    /**
     * 应用附加产品规则（公共方法，供外部调用）
     */
    public void applyAddonRules(OrderContext context) {
        // 按优先级排序规则
        List<AddonRuleConfig.AddonRule> sortedRules = addonRuleConfig.getSortedRules();

        logger.info("开始应用 {} 条附加产品规则...", sortedRules.size());

        for (ProductItem mainItem : context.getMainProducts()) {
            for (AddonRuleConfig.AddonRule rule : sortedRules) {
                try {
                    // 检查规则是否已应用（避免重复）
                    if (mainItem.hasAppliedRule(rule.getRuleId())) {
                        continue;
                    }

                    // 检查规则是否匹配
                    if (rule.matches(context, mainItem)) {
                        // 创建附属产品
                        ProductItem accessory = rule.createAccessory(mainItem);

                        if (accessory != null) {
                            // 设置产品名称
                            if (accessory.getProductType() != null) {
                                accessory.setProductName(ProductName.fromOrderType(accessory.getProductType()));
                            }

                            context.addItem(accessory);
                            mainItem.addAppliedRule(rule.getRuleId());

                            logger.info("  + 附加产品 [规则: {}]: {}",
                                    rule.getRuleId(),
                                    accessory.getSummary());
                        }
                    }
                } catch (Exception e) {
                    logger.error("应用附加产品规则失败: {}", rule.getRuleId(), e);
                }
            }
        }
    }
}
