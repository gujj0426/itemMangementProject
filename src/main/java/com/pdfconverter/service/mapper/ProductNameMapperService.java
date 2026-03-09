package com.pdfconverter.service.mapper;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * 产品名称映射服务
 * 用于根据主商品类型、型号、颜色、产品变量映射为中文名称
 * 映射关系从 product-output-name-mapping.properties 配置文件中读取
 */
@Service
public class ProductNameMapperService {

    private static final Logger log = LoggerFactory.getLogger(ProductNameMapperService.class);

    /**
     * 映射配置文件路径（默认值）
     */
    @Value("${app.mapping.product-chinese-name-file:product-output-name-mapping.properties}")
    private String mappingFilePath;

    /**
     * 产品中文名称映射规则列表
     */
    private List<ProductChineseNameMappingRule> mappingRules;

    @PostConstruct
    public void init() {
        mappingRules = new ArrayList<>();
        loadMappingFromFile();
    }

    /**
     * 从配置文件加载映射关系
     */
    private void loadMappingFromFile() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();

        try {
            Resource resource = resolver.getResource("classpath:" + mappingFilePath);

            if (!resource.exists()) {
                log.warn("产品中文名称映射配置文件不存在: {}", mappingFilePath);
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {

                String line;
                int lineNumber = 0;
                while ((line = reader.readLine()) != null) {
                    lineNumber++;

                    // 跳过空行和注释行
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) {
                        continue;
                    }

                    // 解析 key=value 格式
                    int equalIndex = line.indexOf("=");
                    if (equalIndex > 0) {
                        String key = line.substring(0, equalIndex).trim();
                        String value = line.substring(equalIndex + 1).trim();

                        ProductChineseNameMappingRule rule = parseMappingRule(key, value);
                        if (rule != null) {
                            mappingRules.add(rule);
                            log.debug("加载映射规则: {} -> {}", key, rule.getChineseName());
                        }
                    } else {
                        log.warn("配置文件第 {} 行格式错误，跳过: {}", lineNumber, line);
                    }
                }
            }

            log.info("成功加载产品中文名称映射，共 {} 条映射规则", mappingRules.size());

        } catch (IOException e) {
            log.error("加载产品中文名称映射配置文件失败: {}", mappingFilePath, e);
        }
    }

    /**
     * 解析单条映射规则
     * 格式：<主商品类型>|<型号>|<颜色>|<产品变量>|<主商品中文名称>
     */
    private ProductChineseNameMappingRule parseMappingRule(String key, String value) {
        try {
            String[] parts = value.split("\\|");
            if (parts.length != 5) {
                log.warn("映射规则格式错误，应为5个字段: {}", value);
                return null;
            }

            ProductChineseNameMappingRule rule = new ProductChineseNameMappingRule();
            rule.setRuleKey(key);
            rule.setProductType(parts[0].trim());
            rule.setSize(parts[1].trim().isEmpty() ? null : parts[1].trim());
            rule.setColor(parts[2].trim().isEmpty() ? null : parts[2].trim());
            rule.setProductVariable(parts[3].trim().isEmpty() ? null : parts[3].trim());
            rule.setChineseName(parts[4].trim());

            return rule;
        } catch (Exception e) {
            log.error("解析映射规则失败: {}", value, e);
            return null;
        }
    }

    /**
     * 根据主商品类型获取中文名称（不限制其他条件）
     *
     * @param productType 主商品类型
     * @return 主商品中文名称，如果未找到映射则返回 null
     */
    public String getChineseName(String productType) {
        return getChineseName(productType, null, null, null);
    }

    /**
     * 根据主商品类型和型号获取中文名称
     *
     * @param productType 主商品类型
     * @param size 型号（S, L 等）
     * @return 主商品中文名称，如果未找到映射则返回 null
     */
    public String getChineseName(String productType, String size) {
        return getChineseName(productType, size, null, null);
    }

    /**
     * 根据主商品类型、型号、颜色获取中文名称
     *
     * @param productType 主商品类型
     * @param size 型号（S, L 等）
     * @param color 颜色
     * @return 主商品中文名称，如果未找到映射则返回 null
     */
    public String getChineseName(String productType, String size, String color) {
        return getChineseName(productType, size, color, null);
    }

    /**
     * 根据主商品类型、型号、颜色、产品变量获取中文名称
     *
     * @param productType 主商品类型
     * @param size 型号（S, L 等），为 null 或空表示不限制
     * @param color 颜色，为 null 或空表示不限制
     * @param productVariable 产品变量，为 null 或空表示不限制
     * @return 主商品中文名称，如果未找到映射则返回 null
     */
    public String getChineseName(String productType, String size, String color, String productVariable) {
        if (productType == null || productType.trim().isEmpty()) {
            return null;
        }

        // 按顺序匹配，找到第一个符合所有条件的规则即返回
        for (ProductChineseNameMappingRule rule : mappingRules) {
            if (matchesRule(rule, productType, size, color, productVariable)) {
                log.debug("匹配规则: {} -> {}", rule.getRuleKey(), rule.getChineseName());
                return rule.getChineseName();
            }
        }

        log.debug("未找到匹配的映射规则: type={}, size={}, color={}, variable={}",
                productType, size, color, productVariable);
        return null;
    }

    /**
     * 检查规则是否匹配
     *
     * @param rule 映射规则
     * @param productType 主商品类型
     * @param size 型号
     * @param color 颜色
     * @param productVariable 产品变量
     * @return true 表示匹配，false 表示不匹配
     */
    private boolean matchesRule(ProductChineseNameMappingRule rule, String productType,
                                 String size, String color, String productVariable) {
        // 检查主商品类型
        if (!productType.equals(rule.getProductType())) {
            return false;
        }

        // 检查型号（如果规则中有要求）
        if (rule.getSize() != null && !rule.getSize().isEmpty()) {
            if (size == null || size.isEmpty() || !size.equals(rule.getSize())) {
                return false;
            }
        }

        // 检查颜色（如果规则中有要求）
        if (rule.getColor() != null && !rule.getColor().isEmpty()) {
            if (color == null || color.isEmpty() || !color.equals(rule.getColor())) {
                return false;
            }
        }

        // 检查产品变量（如果规则中有要求）
        if (rule.getProductVariable() != null && !rule.getProductVariable().isEmpty()) {
            if (productVariable == null || productVariable.isEmpty() || !productVariable.equals(rule.getProductVariable())) {
                return false;
            }
        }

        return true;
    }

    /**
     * 重新加载配置文件（用于动态更新映射关系）
     */
    public void reloadMapping() {
        mappingRules.clear();
        loadMappingFromFile();
        log.info("产品中文名称映射已重新加载");
    }

    /**
     * 获取某个主商品类型所有可能的中文名称
     *
     * @param productType 主商品类型
     * @return 所有可能的中文名称列表
     */
    public List<String> getAllChineseNames(String productType) {
        List<String> names = new ArrayList<>();

        for (ProductChineseNameMappingRule rule : mappingRules) {
            if (rule.getProductType().equals(productType)) {
                if (!names.contains(rule.getChineseName())) {
                    names.add(rule.getChineseName());
                }
            }
        }

        return names;
    }

    /**
     * 检查是否存在指定的映射
     *
     * @param productType 主商品类型
     * @return 如果存在映射返回 true，否则返回 false
     */
    public boolean hasMapping(String productType) {
        if (productType == null || productType.trim().isEmpty()) {
            return false;
        }

        return mappingRules.stream()
                .anyMatch(rule -> rule.getProductType().equals(productType));
    }

    /**
     * 获取所有映射关系（用于调试或展示）
     *
     * @return 所有映射规则的副本
     */
    public List<ProductChineseNameMappingRule> getAllMappings() {
        return new ArrayList<>(mappingRules);
    }

    /**
     * 产品中文名称映射规则
     */
    public static class ProductChineseNameMappingRule {
        private String ruleKey;
        private String productType;
        private String size;
        private String color;
        private String productVariable;
        private String chineseName;

        // Getters and Setters
        public String getRuleKey() {
            return ruleKey;
        }

        public void setRuleKey(String ruleKey) {
            this.ruleKey = ruleKey;
        }

        public String getProductType() {
            return productType;
        }

        public void setProductType(String productType) {
            this.productType = productType;
        }

        public String getSize() {
            return size;
        }

        public void setSize(String size) {
            this.size = size;
        }

        public String getColor() {
            return color;
        }

        public void setColor(String color) {
            this.color = color;
        }

        public String getProductVariable() {
            return productVariable;
        }

        public void setProductVariable(String productVariable) {
            this.productVariable = productVariable;
        }

        public String getChineseName() {
            return chineseName;
        }

        public void setChineseName(String chineseName) {
            this.chineseName = chineseName;
        }

        @Override
        public String toString() {
            return "ProductChineseNameMappingRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", productType='" + productType + '\'' +
                    ", size='" + size + '\'' +
                    ", color='" + color + '\'' +
                    ", productVariable='" + productVariable + '\'' +
                    ", chineseName='" + chineseName + '\'' +
                    '}';
        }
    }
}
