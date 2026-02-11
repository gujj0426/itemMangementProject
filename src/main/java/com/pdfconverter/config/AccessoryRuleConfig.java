package com.pdfconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 附属商品补充规则配置
 * 将硬编码的附属商品补充逻辑配置化，方便维护和扩展
 */
@Component
@ConfigurationProperties(prefix = "accessory.rules")
public class AccessoryRuleConfig {

    /**
     * 附属商品规则列表
     */
    private List<AccessoryRule> rules = new ArrayList<>();

    public List<AccessoryRule> getRules() {
        return rules;
    }

    public void setRules(List<AccessoryRule> rules) {
        this.rules = rules;
    }

    /**
     * 单个附属商品规则
     */
    public static class AccessoryRule {
        /**
         * 规则描述
         */
        private String description;

        /**
         * 主商品类型列表（必须包含其中之一才补充）
         */
        private List<String> mainProductTypes;

        /**
         * 排除的商品类型（如果包含这些类型则不补充）
         */
        private List<String> excludeProductTypes;

        /**
         * 标题关键词（如果包含这些关键词则补充）
         */
        private List<String> titleKeywords;

        /**
         * 需要补充的附属商品类型
         */
        private String accessoryProductType;

        /**
         * 附属商品数量是否与主商品相同
         */
        private boolean quantitySameAsMain = true;

        /**
         * 是否继承主商品的颜色
         */
        private boolean inheritColor = false;

        /**
         * 是否继承主商品的尺寸
         */
        private boolean inheritSize = false;

        // Getters and Setters
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

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

        public String getAccessoryProductType() {
            return accessoryProductType;
        }

        public void setAccessoryProductType(String accessoryProductType) {
            this.accessoryProductType = accessoryProductType;
        }

        public boolean isQuantitySameAsMain() {
            return quantitySameAsMain;
        }

        public void setQuantitySameAsMain(boolean quantitySameAsMain) {
            this.quantitySameAsMain = quantitySameAsMain;
        }

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
    }
}
