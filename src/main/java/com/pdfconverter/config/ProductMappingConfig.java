package com.pdfconverter.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import org.springframework.core.io.ClassPathResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 商品映射配置类
 * 用于从 product-mapping-rules.properties 文件加载商品标题映射规则和附属商品补充规则
 */
@Component
@ConfigurationProperties(prefix = "product.mapping")
public class ProductMappingConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductMappingConfig.class);

    // 商品标题映射规则列表
    private List<ProductTitleMappingRule> titleMappingRules = new ArrayList<>();

    // 附属商品补充规则列表
    private List<AccessoryMappingRule> accessoryRules = new ArrayList<>();

    // 配置文件路径
    private static final String CONFIG_FILE = "product-mapping-rules.properties";

    @PostConstruct
    public void init() {
        loadConfig();
    }

    /**
     * 从配置文件加载映射规则
     */
    public void loadConfig() {
        try {
            titleMappingRules.clear();
            accessoryRules.clear();

            ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8)
            );

            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                // 跳过空行和注释行
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }

                // 解析配置行
                String[] parts = line.split("=", 2);
                if (parts.length != 2) {
                    log.warn("配置行格式错误: {}", line);
                    continue;
                }

                String key = parts[0].trim();
                String value = parts[1].trim();

                // 根据键前缀判断规则类型
                if (key.startsWith("accessory.rule.")) {
                    // 附属商品规则
                    AccessoryMappingRule rule = parseAccessoryRule(key, value);
                    if (rule != null) {
                        accessoryRules.add(rule);
                    }
                } else {
                    // 商品标题映射规则
                    ProductTitleMappingRule rule = parseTitleMappingRule(key, value);
                    if (rule != null) {
                        titleMappingRules.add(rule);
                    }
                }
            }

            reader.close();
            log.info("成功加载商品映射配置: 标题映射规则 {} 条，附属商品规则 {} 条",
                titleMappingRules.size(), accessoryRules.size());

        } catch (Exception e) {
            log.error("加载商品映射配置文件失败", e);
        }
    }

    /**
     * 解析商品标题映射规则
     */
    private ProductTitleMappingRule parseTitleMappingRule(String key, String value) {
        try {
            String[] parts = value.split("\\|");
            if (parts.length < 2) {
                log.warn("商品标题映射规则格式错误: {}", value);
                return null;
            }

            ProductTitleMappingRule rule = new ProductTitleMappingRule();
            rule.setRuleKey(key);
            rule.setProductTitle(parts[0].trim());
            rule.setMainProductType(parts[1].trim());
            rule.setAccessoryRule(parts.length > 2 ? parts[2].trim() : "");
            return rule;

        } catch (Exception e) {
            log.error("解析商品标题映射规则失败: {}", value, e);
            return null;
        }
    }

    /**
     * 解析附属商品规则
     */
    private AccessoryMappingRule parseAccessoryRule(String key, String value) {
        try {
            String[] parts = value.split("\\|");
            if (parts.length < 4) {
                log.warn("附属商品规则格式错误: {}", value);
                return null;
            }

            AccessoryMappingRule rule = new AccessoryMappingRule();
            rule.setRuleKey(key);
            rule.setMainProductType(parts[0].trim());
            rule.setCondition(parts[1].trim());
            rule.setAccessoryProductType(parts[2].trim());
            rule.setAccessoryChineseName(parts[3].trim());
            return rule;

        } catch (Exception e) {
            log.error("解析附属商品规则失败: {}", value, e);
            return null;
        }
    }

    /**
     * 根据商品标题匹配映射规则（支持模糊匹配）
     */
    public ProductTitleMappingRule findMatchingRule(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        String titleLower = title.toLowerCase().trim();

        for (ProductTitleMappingRule rule : titleMappingRules) {
            String ruleTitle = rule.getProductTitle().toLowerCase().trim();
            // 使用包含关系进行模糊匹配
            if (titleLower.contains(ruleTitle) || ruleTitle.contains(titleLower)) {
                return rule;
            }
        }

        return null;
    }

    /**
     * 根据主商品类型查找附属商品规则
     */
    public List<AccessoryMappingRule> findAccessoryRules(String mainProductType) {
        List<AccessoryMappingRule> matchedRules = new ArrayList<>();

        for (AccessoryMappingRule rule : accessoryRules) {
            if (rule.getMainProductType().equals(mainProductType)) {
                matchedRules.add(rule);
            }
        }

        return matchedRules;
    }

    // Getter and Setter
    public List<ProductTitleMappingRule> getTitleMappingRules() {
        return titleMappingRules;
    }

    public void setTitleMappingRules(List<ProductTitleMappingRule> titleMappingRules) {
        this.titleMappingRules = titleMappingRules;
    }

    public List<AccessoryMappingRule> getAccessoryRules() {
        return accessoryRules;
    }

    public void setAccessoryRules(List<AccessoryMappingRule> accessoryRules) {
        this.accessoryRules = accessoryRules;
    }

    /**
     * 商品标题映射规则
     */
    public static class ProductTitleMappingRule {
        private String ruleKey;
        private String productTitle;
        private String mainProductType;
        private String accessoryRule;

        // Getters and Setters
        public String getRuleKey() { return ruleKey; }
        public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }

        public String getMainProductType() { return mainProductType; }
        public void setMainProductType(String mainProductType) { this.mainProductType = mainProductType; }

        public String getAccessoryRule() { return accessoryRule; }
        public void setAccessoryRule(String accessoryRule) { this.accessoryRule = accessoryRule; }

        @Override
        public String toString() {
            return "ProductTitleMappingRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", productTitle='" + productTitle + '\'' +
                    ", mainProductType='" + mainProductType + '\'' +
                    ", accessoryRule='" + accessoryRule + '\'' +
                    '}';
        }
    }

    /**
     * 附属商品映射规则
     */
    public static class AccessoryMappingRule {
        private String ruleKey;
        private String mainProductType;
        private String condition;
        private String accessoryProductType;
        private String accessoryChineseName;

        // Getters and Setters
        public String getRuleKey() { return ruleKey; }
        public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

        public String getMainProductType() { return mainProductType; }
        public void setMainProductType(String mainProductType) { this.mainProductType = mainProductType; }

        public String getCondition() { return condition; }
        public void setCondition(String condition) { this.condition = condition; }

        public String getAccessoryProductType() { return accessoryProductType; }
        public void setAccessoryProductType(String accessoryProductType) { this.accessoryProductType = accessoryProductType; }

        public String getAccessoryChineseName() { return accessoryChineseName; }
        public void setAccessoryChineseName(String accessoryChineseName) { this.accessoryChineseName = accessoryChineseName; }

        @Override
        public String toString() {
            return "AccessoryMappingRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", mainProductType='" + mainProductType + '\'' +
                    ", condition='" + condition + '\'' +
                    ", accessoryProductType='" + accessoryProductType + '\'' +
                    ", accessoryChineseName='" + accessoryChineseName + '\'' +
                    '}';
        }
    }
}
