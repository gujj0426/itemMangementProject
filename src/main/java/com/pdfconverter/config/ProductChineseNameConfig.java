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
import java.util.ArrayList;
import java.util.List;

/**
 * 商品中文名称映射配置类
 * 用于从 product-chinese-name-mapping.properties 文件加载商品中文名称映射规则
 */
@Component
@ConfigurationProperties(prefix = "product.chinese.name")
public class ProductChineseNameConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductChineseNameConfig.class);

    // 商品中文名称映射规则列表
    private List<ProductChineseNameRule> chineseNameRules = new ArrayList<>();

    // 配置文件路径
    private static final String CONFIG_FILE = "product-chinese-name-mapping.properties";

    @PostConstruct
    public void init() {
        loadConfig();
    }

    /**
     * 从配置文件加载映射规则
     */
    public void loadConfig() {
        try {
            chineseNameRules.clear();

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

                // 解析中文名称映射规则
                ProductChineseNameRule rule = parseChineseNameRule(key, value);
                if (rule != null) {
                    chineseNameRules.add(rule);
                }
            }

            reader.close();
            log.info("成功加载商品中文名称映射配置: {} 条规则", chineseNameRules.size());

        } catch (Exception e) {
            log.error("加载商品中文名称映射配置文件失败", e);
        }
    }

    /**
     * 解析商品中文名称映射规则
     */
    private ProductChineseNameRule parseChineseNameRule(String key, String value) {
        try {
            String[] parts = value.split("\\|");
            if (parts.length < 5) {
                log.warn("商品中文名称映射规则格式错误: {}", value);
                return null;
            }

            ProductChineseNameRule rule = new ProductChineseNameRule();
            rule.setRuleKey(key);
            rule.setMainProductType(parts[0].trim());
            rule.setModel(parts[1].trim());
            rule.setColor(parts[2].trim());
            rule.setProductVariable(parts[3].trim());
            rule.setChineseName(parts[4].trim());
            return rule;

        } catch (Exception e) {
            log.error("解析商品中文名称映射规则失败: {}", value, e);
            return null;
        }
    }

    /**
     * 根据主商品类型、型号、颜色、产品变量查找中文名称
     * 按配置顺序匹配，返回第一个符合所有条件的规则
     *
     * @param mainProductType 主商品类型
     * @param model 型号（可为空）
     * @param color 颜色（可为空）
     * @param productVariable 产品变量（可为空）
     * @return 匹配的中文名称，未匹配返回null
     */
    public String findChineseName(String mainProductType, String model, String color, String productVariable) {
        for (ProductChineseNameRule rule : chineseNameRules) {
            // 检查主商品类型
            if (!rule.getMainProductType().equals(mainProductType)) {
                continue;
            }

            // 检查型号
            if (!isMatch(rule.getModel(), model)) {
                continue;
            }

            // 检查颜色
            if (!isMatch(rule.getColor(), color)) {
                continue;
            }

            // 检查产品变量
            if (!isMatch(rule.getProductVariable(), productVariable)) {
                continue;
            }

            // 所有条件匹配
            log.debug("匹配到中文名称: {} -> {}", mainProductType, rule.getChineseName());
            return rule.getChineseName();
        }

        log.debug("未找到匹配的中文名称: type={}, model={}, color={}, productVariable={}",
            mainProductType, model, color, productVariable);
        return null;
    }

    /**
     * 检查是否匹配（空值表示不限制）
     */
    private boolean isMatch(String ruleValue, String actualValue) {
        if (ruleValue == null || ruleValue.trim().isEmpty()) {
            return true; // 规则值为空，不限制
        }
        if (actualValue == null || actualValue.trim().isEmpty()) {
            return false; // 规则有值但实际值为空，不匹配
        }
        return ruleValue.trim().equals(actualValue.trim());
    }

    /**
     * 根据主商品类型查找所有可能的中文名称
     *
     * @param mainProductType 主商品类型
     * @return 匹配的中文名称列表
     */
    public List<String> findAllChineseNamesByType(String mainProductType) {
        List<String> names = new ArrayList<>();
        for (ProductChineseNameRule rule : chineseNameRules) {
            if (rule.getMainProductType().equals(mainProductType)) {
                if (!names.contains(rule.getChineseName())) {
                    names.add(rule.getChineseName());
                }
            }
        }
        return names;
    }

    // Getter and Setter
    public List<ProductChineseNameRule> getChineseNameRules() {
        return chineseNameRules;
    }

    public void setChineseNameRules(List<ProductChineseNameRule> chineseNameRules) {
        this.chineseNameRules = chineseNameRules;
    }

    /**
     * 商品中文名称映射规则
     */
    public static class ProductChineseNameRule {
        private String ruleKey;
        private String mainProductType;
        private String model;
        private String color;
        private String productVariable;
        private String chineseName;

        // Getters and Setters
        public String getRuleKey() { return ruleKey; }
        public void setRuleKey(String ruleKey) { this.ruleKey = ruleKey; }

        public String getMainProductType() { return mainProductType; }
        public void setMainProductType(String mainProductType) { this.mainProductType = mainProductType; }

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }

        public String getProductVariable() { return productVariable; }
        public void setProductVariable(String productVariable) { this.productVariable = productVariable; }

        public String getChineseName() { return chineseName; }
        public void setChineseName(String chineseName) { this.chineseName = chineseName; }

        @Override
        public String toString() {
            return "ProductChineseNameRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", mainProductType='" + mainProductType + '\'' +
                    ", model='" + model + '\'' +
                    ", color='" + color + '\'' +
                    ", productVariable='" + productVariable + '\'' +
                    ", chineseName='" + chineseName + '\'' +
                    '}';
        }
    }
}
