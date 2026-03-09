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
 * 用于从 product-mapping-rules.properties 文件加载商品标题映射规则
 */
@Component
@ConfigurationProperties(prefix = "product.mapping")
public class ProductMappingConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductMappingConfig.class);

    // 商品标题映射规则列表
    private List<ProductTitleMappingRule> titleMappingRules = new ArrayList<>();

    // 配置文件路径
    private static final String CONFIG_FILE = "title-product-mapping-rules.properties";

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

                // 解析商品标题映射规则
                ProductTitleMappingRule rule = parseTitleMappingRule(key, value);
                if (rule != null) {
                    titleMappingRules.add(rule);
                }
            }

            reader.close();
            log.info("成功加载商品映射配置: 标题映射规则 {} 条", titleMappingRules.size());

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
            return rule;

        } catch (Exception e) {
            log.error("解析商品标题映射规则失败: {}", value, e);
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

    // Getter and Setter
    public List<ProductTitleMappingRule> getTitleMappingRules() {
        return titleMappingRules;
    }

    public void setTitleMappingRules(List<ProductTitleMappingRule> titleMappingRules) {
        this.titleMappingRules = titleMappingRules;
    }

    /**
     * 商品标题映射规则
     */
    public static class ProductTitleMappingRule {
        private String ruleKey;
        private String productTitle;
        private String mainProductType;

        // Getters and Setters
        public String getRuleKey() { return ruleKey; }
        public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

        public String getProductTitle() { return productTitle; }
        public void setProductTitle(String productTitle) { this.productTitle = productTitle; }

        public String getMainProductType() { return mainProductType; }
        public void setMainProductType(String mainProductType) { this.mainProductType = mainProductType; }

        @Override
        public String toString() {
            return "ProductTitleMappingRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", productTitle='" + productTitle + '\'' +
                    ", mainProductType='" + mainProductType + '\'' +
                    '}';
        }
    }
}
