package com.pdfconverter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * 附加商品补充规则配置
 * 从 accessory-rules.json 文件加载规则（支持外部文件覆盖 classpath 文件）
 */
@Component
public class AccessoryRuleConfig {

    private static final Logger log = LoggerFactory.getLogger(AccessoryRuleConfig.class);

    /** 外部 JSON 覆盖路径（优先级高于 classpath，可选） */
    @Value("${app.config.accessory-rules-file:accessory-rules.json}")
    private String accessoryRulesFile;

    private List<AccessoryRule> rules = new ArrayList<>();

    @PostConstruct
    public void init() {
        loadRules();
    }

    /**
     * 加载规则：优先从外部路径，其次从 classpath
     */
    public void loadRules() {
        try {
            RulesWrapper wrapper = null;
            ObjectMapper mapper = new ObjectMapper();

            // 尝试外部文件
            File externalFile = new File(accessoryRulesFile);
            if (externalFile.isAbsolute() && externalFile.exists()) {
                log.info("从外部文件加载附加商品规则: {}", externalFile.getAbsolutePath());
                wrapper = mapper.readValue(externalFile, RulesWrapper.class);
            } else {
                // 从 classpath 加载
                ClassPathResource resource = new ClassPathResource("accessory-rules.json");
                if (resource.exists()) {
                    try (InputStream is = resource.getInputStream()) {
                        log.info("从 classpath 加载附加商品规则: accessory-rules.json");
                        wrapper = mapper.readValue(is, RulesWrapper.class);
                    }
                }
            }

            if (wrapper != null && wrapper.getAccessoryRules() != null) {
                this.rules = wrapper.getAccessoryRules();
                log.info("成功加载附加商品规则: {} 条", rules.size());
                for (AccessoryRule rule : rules) {
                    log.info("  规则 {}: {} (enabled={}, priority={}, mainTypes={}, accessoryType={})",
                            rule.getRuleId(), rule.getDescription(), rule.isEnabled(), rule.getPriority(),
                            rule.getMainProductTypes(), rule.getAccessoryType());
                }
            } else {
                log.warn("未找到有效的附加商品规则文件，规则列表为空");
                this.rules = new ArrayList<>();
            }
        } catch (Exception e) {
            log.error("加载附加商品规则失败，将使用空规则列表: {}", e.getMessage(), e);
            this.rules = new ArrayList<>();
        }
    }

    public List<AccessoryRule> getRules() {
        return rules;
    }

    public void setRules(List<AccessoryRule> rules) {
        this.rules = rules;
    }

    // ============================================================
    // JSON 反序列化包装类
    // ============================================================
    public static class RulesWrapper {
        private List<AccessoryRule> accessoryRules;

        public List<AccessoryRule> getAccessoryRules() { return accessoryRules; }
        public void setAccessoryRules(List<AccessoryRule> accessoryRules) { this.accessoryRules = accessoryRules; }
    }

    // ============================================================
    // 单个附属商品规则
    // ============================================================
    public static class AccessoryRule {
        private String ruleId;
        private String description;
        private int priority = 100;
        private boolean enabled = true;
        private List<String> mainProductTypes;
        private List<String> excludeProductTypes;
        private List<String> titleKeywords;
        private OrderCondition orderCondition;
        private String accessoryType;
        private String accessoryVariable;
        private QuantityRule quantityRule;
        private InheritConfig inheritConfig;

        public String getRuleId() { return ruleId; }
        public void setRuleId(String ruleId) { this.ruleId = ruleId; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public int getPriority() { return priority; }
        public void setPriority(int priority) { this.priority = priority; }

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }

        public List<String> getMainProductTypes() { return mainProductTypes; }
        public void setMainProductTypes(List<String> mainProductTypes) { this.mainProductTypes = mainProductTypes; }

        public List<String> getExcludeProductTypes() { return excludeProductTypes; }
        public void setExcludeProductTypes(List<String> excludeProductTypes) { this.excludeProductTypes = excludeProductTypes; }

        public List<String> getTitleKeywords() { return titleKeywords; }
        public void setTitleKeywords(List<String> titleKeywords) { this.titleKeywords = titleKeywords; }

        public OrderCondition getOrderCondition() { return orderCondition; }
        public void setOrderCondition(OrderCondition orderCondition) { this.orderCondition = orderCondition; }

        public String getAccessoryType() { return accessoryType; }
        public void setAccessoryType(String accessoryType) { this.accessoryType = accessoryType; }

        public String getAccessoryVariable() { return accessoryVariable; }
        public void setAccessoryVariable(String accessoryVariable) { this.accessoryVariable = accessoryVariable; }

        public QuantityRule getQuantityRule() { return quantityRule; }
        public void setQuantityRule(QuantityRule quantityRule) { this.quantityRule = quantityRule; }

        public InheritConfig getInheritConfig() { return inheritConfig; }
        public void setInheritConfig(InheritConfig inheritConfig) { this.inheritConfig = inheritConfig; }
    }

    public static class OrderCondition {
        private List<String> requireOtherProducts;
        private boolean requireNoWoodBox;

        public OrderCondition() {}
        public OrderCondition(List<String> requireOtherProducts, boolean requireNoWoodBox) {
            this.requireOtherProducts = requireOtherProducts;
            this.requireNoWoodBox = requireNoWoodBox;
        }

        public List<String> getRequireOtherProducts() { return requireOtherProducts; }
        public void setRequireOtherProducts(List<String> requireOtherProducts) { this.requireOtherProducts = requireOtherProducts; }

        public boolean isRequireNoWoodBox() { return requireNoWoodBox; }
        public void setRequireNoWoodBox(boolean requireNoWoodBox) { this.requireNoWoodBox = requireNoWoodBox; }
    }

    public static class QuantityRule {
        private boolean sameAsMain = true;
        private Integer fixedQuantity;
        private Integer multiplier = 1;

        public QuantityRule() {}
        public QuantityRule(boolean sameAsMain, Integer fixedQuantity, int multiplier) {
            this.sameAsMain = sameAsMain;
            this.fixedQuantity = fixedQuantity;
            this.multiplier = multiplier;
        }

        public boolean isSameAsMain() { return sameAsMain; }
        public void setSameAsMain(boolean sameAsMain) { this.sameAsMain = sameAsMain; }

        public Integer getFixedQuantity() { return fixedQuantity; }
        public void setFixedQuantity(Integer fixedQuantity) { this.fixedQuantity = fixedQuantity; }

        public Integer getMultiplier() { return multiplier; }
        public void setMultiplier(Integer multiplier) { this.multiplier = multiplier; }
    }

    public static class InheritConfig {
        private boolean inheritColor = false;
        private boolean inheritSize = false;
        private boolean inheritVariable = false;

        public InheritConfig() {}
        public InheritConfig(boolean inheritColor, boolean inheritSize, boolean inheritVariable) {
            this.inheritColor = inheritColor;
            this.inheritSize = inheritSize;
            this.inheritVariable = inheritVariable;
        }

        public boolean isInheritColor() { return inheritColor; }
        public void setInheritColor(boolean inheritColor) { this.inheritColor = inheritColor; }

        public boolean isInheritSize() { return inheritSize; }
        public void setInheritSize(boolean inheritSize) { this.inheritSize = inheritSize; }

        public boolean isInheritVariable() { return inheritVariable; }
        public void setInheritVariable(boolean inheritVariable) { this.inheritVariable = inheritVariable; }
    }
}
