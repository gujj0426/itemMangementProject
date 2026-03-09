package com.pdfconverter.config;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.OrderContext;
import com.pdfconverter.model.ProductItem;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 附加产品规则引擎配置
 * 支持复杂的多条件组合和动作定义
 * 完全配置化，无需修改代码即可调整附加产品规则
 */
@Component
@ConfigurationProperties(prefix = "addon.rules")
public class AddonRuleConfig {

    private List<AddonRule> rules = new ArrayList<>();

    public List<AddonRule> getRules() {
        return rules;
    }

    public void setRules(List<AddonRule> rules) {
        this.rules = rules;
    }

    /**
     * 获取排序后的规则列表（按优先级）
     */
    public List<AddonRule> getSortedRules() {
        return rules.stream()
                .sorted(Comparator.comparingInt(AddonRule::getPriority))
                .collect(Collectors.toList());
    }

    /**
     * 根据规则ID获取规则
     */
    public Optional<AddonRule> getRuleById(String ruleId) {
        return rules.stream()
                .filter(rule -> ruleId.equals(rule.getRuleId()))
                .findFirst();
    }

    /**
     * 附加产品规则
     */
    public static class AddonRule {
        private String ruleId;                    // 规则ID
        private String description;               // 规则描述
        private int priority = 100;               // 优先级（数字越小优先级越高）
        private boolean enabled = true;           // 是否启用

        // 触发条件
        private TriggerCondition condition;

        // 执行动作
        private AddonAction action;

        /**
         * 判断规则是否匹配
         */
        public boolean matches(OrderContext context, ProductItem mainItem) {
            if (!enabled) {
                return false;
            }
            return condition != null && condition.evaluate(context, mainItem);
        }

        /**
         * 创建附属产品
         */
        public ProductItem createAccessory(ProductItem mainItem) {
            if (action == null) {
                return null;
            }
            return action.createAccessory(mainItem);
        }

        // Getters and Setters
        public String getRuleId() {
            return ruleId;
        }

        public void setRuleId(String ruleId) {
            this.ruleId = ruleId;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public int getPriority() {
            return priority;
        }

        public void setPriority(int priority) {
            this.priority = priority;
        }

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public TriggerCondition getCondition() {
            return condition;
        }

        public void setCondition(TriggerCondition condition) {
            this.condition = condition;
        }

        public AddonAction getAction() {
            return action;
        }

        public void setAction(AddonAction action) {
            this.action = action;
        }
    }

    /**
     * 触发条件
     */
    public static class TriggerCondition {
        // 主产品类型列表（必须包含其中之一）
        private List<String> mainProductTypes;

        // 排除的产品类型（如果订单中包含则不触发）
        private List<String> excludeProductTypes;

        // 标题关键词（必须包含）
        private List<String> titleKeywords;

        // 订单级条件
        private OrderLevelCondition orderCondition;

        /**
         * 评估条件是否满足
         */
        public boolean evaluate(OrderContext context, ProductItem mainItem) {
            // 检查产品类型
            if (mainProductTypes != null && !mainProductTypes.isEmpty()) {
                boolean typeMatched = mainProductTypes.stream()
                        .anyMatch(type -> matchesProductType(mainItem.getProductType(), type));
                if (!typeMatched) {
                    return false;
                }
            }

            // 检查排除类型（订单级）
            if (excludeProductTypes != null && !excludeProductTypes.isEmpty()) {
                boolean hasExcluded = excludeProductTypes.stream()
                        .anyMatch(type -> {
                            OrderType orderType = parseOrderType(type);
                            return orderType != null && context.hasProductType(orderType);
                        });
                if (hasExcluded) {
                    return false;
                }
            }

            // 检查标题关键词
            if (titleKeywords != null && !titleKeywords.isEmpty()) {
                String title = mainItem.getSourceTitle() != null ?
                        mainItem.getSourceTitle().toLowerCase() : "";
                boolean keywordMatched = titleKeywords.stream()
                        .anyMatch(keyword -> title.contains(keyword.toLowerCase()));
                if (!keywordMatched) {
                    return false;
                }
            }

            // 检查订单级条件
            if (orderCondition != null && !orderCondition.evaluate(context)) {
                return false;
            }

            return true;
        }

        /**
         * 匹配产品类型
         */
        private boolean matchesProductType(OrderType productType, String typeString) {
            if (productType == null || typeString == null) {
                return false;
            }

            OrderType targetType = parseOrderType(typeString);
            if (targetType != null) {
                return productType == targetType;
            }

            // 尝试通过显示名称匹配
            return productType.getDisplayName().equalsIgnoreCase(typeString) ||
                   productType.name().equalsIgnoreCase(typeString.replace(" ", "_"));
        }

        /**
         * 解析订单类型
         */
        private OrderType parseOrderType(String typeString) {
            if (typeString == null) {
                return null;
            }

            try {
                return OrderType.valueOf(typeString.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                // 尝试通过显示名称查找
                for (OrderType type : OrderType.values()) {
                    if (type.getDisplayName().equalsIgnoreCase(typeString)) {
                        return type;
                    }
                }
            }
            return null;
        }

        // Getters and Setters
        public List<String> getMainProductTypes() {
            return mainProductTypes;
        }

        public void setMainProductTypes(List<String> mainProductTypes) {
            this.mainProductTypes = mainProductTypes;
        }

        public List<String> getExcludeProductTypes() {
            return excludeProductTypes;
        }

        public void setExcludeProductTypes(List<String> excludeProductTypes) {
            this.excludeProductTypes = excludeProductTypes;
        }

        public List<String> getTitleKeywords() {
            return titleKeywords;
        }

        public void setTitleKeywords(List<String> titleKeywords) {
            this.titleKeywords = titleKeywords;
        }

        public OrderLevelCondition getOrderCondition() {
            return orderCondition;
        }

        public void setOrderCondition(OrderLevelCondition orderCondition) {
            this.orderCondition = orderCondition;
        }
    }

    /**
     * 订单级条件
     */
    public static class OrderLevelCondition {
        // 需要同时存在的其他产品类型
        private List<String> requireOtherProducts;

        // 不能存在的其他产品类型
        private List<String> excludeOtherProducts;

        // 是否要求无木盒
        private boolean requireNoWoodBox = false;

        // 是否要求有木盒
        private boolean requireWoodBox = false;

        /**
         * 评估订单级条件是否满足
         */
        public boolean evaluate(OrderContext context) {
            // 检查必须存在的其他产品
            if (requireOtherProducts != null && !requireOtherProducts.isEmpty()) {
                for (String productType : requireOtherProducts) {
                    OrderType type = parseOrderType(productType);
                    if (type != null && !context.hasProductType(type)) {
                        return false;
                    }
                }
            }

            // 检查不能存在的其他产品
            if (excludeOtherProducts != null && !excludeOtherProducts.isEmpty()) {
                for (String productType : excludeOtherProducts) {
                    OrderType type = parseOrderType(productType);
                    if (type != null && context.hasProductType(type)) {
                        return false;
                    }
                }
            }

            // 检查木盒条件
            if (requireNoWoodBox && context.hasWoodBox()) {
                return false;
            }

            if (requireWoodBox && !context.hasWoodBox()) {
                return false;
            }

            return true;
        }

        /**
         * 解析订单类型
         */
        private OrderType parseOrderType(String typeString) {
            if (typeString == null) {
                return null;
            }

            try {
                return OrderType.valueOf(typeString.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                // 尝试通过显示名称查找
                for (OrderType type : OrderType.values()) {
                    if (type.getDisplayName().equalsIgnoreCase(typeString)) {
                        return type;
                    }
                }
            }
            return null;
        }

        // Getters and Setters
        public List<String> getRequireOtherProducts() {
            return requireOtherProducts;
        }

        public void setRequireOtherProducts(List<String> requireOtherProducts) {
            this.requireOtherProducts = requireOtherProducts;
        }

        public List<String> getExcludeOtherProducts() {
            return excludeOtherProducts;
        }

        public void setExcludeOtherProducts(List<String> excludeOtherProducts) {
            this.excludeOtherProducts = excludeOtherProducts;
        }

        public boolean isRequireNoWoodBox() {
            return requireNoWoodBox;
        }

        public void setRequireNoWoodBox(boolean requireNoWoodBox) {
            this.requireNoWoodBox = requireNoWoodBox;
        }

        public boolean isRequireWoodBox() {
            return requireWoodBox;
        }

        public void setRequireWoodBox(boolean requireWoodBox) {
            this.requireWoodBox = requireWoodBox;
        }
    }

    /**
     * 附加产品动作
     */
    public static class AddonAction {
        // 附加产品类型
        private String accessoryType;

        // 附加产品变量（如包装盒的具体类型）
        private String accessoryVariable;

        // 数量计算方式
        private QuantityRule quantityRule;

        // 属性继承配置
        private InheritConfig inheritConfig;

        /**
         * 创建附属产品项
         */
        public ProductItem createAccessory(ProductItem mainItem) {
            ProductItem accessory = new ProductItem();
            accessory.setMainProduct(false);
            accessory.setParentItemId(mainItem.getId());

            // 设置产品类型
            OrderType type = parseOrderType(accessoryType);
            if (type != null) {
                accessory.setProductType(type);
            }

            // 设置产品变量
            if (accessoryVariable != null) {
                ProductVariable variable = parseProductVariable(accessoryVariable);
                if (variable != null) {
                    accessory.setVariable(variable);
                }
            }

            // 设置数量
            if (quantityRule != null) {
                accessory.setQuantity(quantityRule.calculate(mainItem));
            } else {
                accessory.setQuantity(1);
            }

            // 继承属性
            if (inheritConfig != null) {
                inheritConfig.apply(accessory, mainItem);
            } else {
                // 默认继承颜色和型号
                accessory.inheritAttributesFrom(mainItem, true, true, false);
            }

            // 记录原始标题
            accessory.setSourceTitle(accessoryType);
            if (accessoryVariable != null) {
                accessory.setSourceTitle(accessoryType + " - " + accessoryVariable);
            }

            return accessory;
        }

        /**
         * 解析订单类型
         */
        private OrderType parseOrderType(String typeString) {
            if (typeString == null) {
                return null;
            }

            try {
                return OrderType.valueOf(typeString.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                for (OrderType type : OrderType.values()) {
                    if (type.getDisplayName().equalsIgnoreCase(typeString)) {
                        return type;
                    }
                }
            }
            return null;
        }

        /**
         * 解析产品变量
         */
        private ProductVariable parseProductVariable(String variableString) {
            if (variableString == null) {
                return null;
            }

            try {
                return ProductVariable.valueOf(variableString.toUpperCase().replace(" ", "_"));
            } catch (IllegalArgumentException e) {
                for (ProductVariable variable : ProductVariable.values()) {
                    if (variable.getDisplayName().equalsIgnoreCase(variableString)) {
                        return variable;
                    }
                }
            }
            return null;
        }

        // Getters and Setters
        public String getAccessoryType() {
            return accessoryType;
        }

        public void setAccessoryType(String accessoryType) {
            this.accessoryType = accessoryType;
        }

        public String getAccessoryVariable() {
            return accessoryVariable;
        }

        public void setAccessoryVariable(String accessoryVariable) {
            this.accessoryVariable = accessoryVariable;
        }

        public QuantityRule getQuantityRule() {
            return quantityRule;
        }

        public void setQuantityRule(QuantityRule quantityRule) {
            this.quantityRule = quantityRule;
        }

        public InheritConfig getInheritConfig() {
            return inheritConfig;
        }

        public void setInheritConfig(InheritConfig inheritConfig) {
            this.inheritConfig = inheritConfig;
        }
    }

    /**
     * 数量规则
     */
    public static class QuantityRule {
        private int fixedQuantity = 1;           // 固定数量
        private int multiplier = 1;              // 倍数（如狗牌绑带需要2倍）
        private boolean sameAsMain = false;      // 是否与主产品相同

        /**
         * 计算数量
         */
        public int calculate(ProductItem mainItem) {
            if (sameAsMain && mainItem != null) {
                return mainItem.getQuantity() * multiplier;
            }
            return fixedQuantity * multiplier;
        }

        // Getters and Setters
        public int getFixedQuantity() {
            return fixedQuantity;
        }

        public void setFixedQuantity(int fixedQuantity) {
            this.fixedQuantity = fixedQuantity;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public void setMultiplier(int multiplier) {
            this.multiplier = multiplier;
        }

        public boolean isSameAsMain() {
            return sameAsMain;
        }

        public void setSameAsMain(boolean sameAsMain) {
            this.sameAsMain = sameAsMain;
        }
    }

    /**
     * 继承配置
     */
    public static class InheritConfig {
        private boolean inheritColor = false;
        private boolean inheritSize = false;
        private boolean inheritVariable = false;

        /**
         * 应用继承配置
         */
        public void apply(ProductItem accessory, ProductItem mainItem) {
            if (accessory == null || mainItem == null) {
                return;
            }

            if (inheritColor) {
                accessory.setColor(mainItem.getColor());
            }
            if (inheritSize) {
                accessory.setSize(mainItem.getSize());
            }
            if (inheritVariable) {
                accessory.setVariable(mainItem.getVariable());
            }
        }

        // Getters and Setters
        public boolean isInheritColor() {
            return inheritColor;
        }

        public void setInheritColor(boolean inheritColor) {
            this.inheritColor = inheritColor;
        }

        public boolean isInheritSize() {
            return inheritSize;
        }

        public void setInheritSize(boolean inheritSize) {
            this.inheritSize = inheritSize;
        }

        public boolean isInheritVariable() {
            return inheritVariable;
        }

        public void setInheritVariable(boolean inheritVariable) {
            this.inheritVariable = inheritVariable;
        }
    }
}
