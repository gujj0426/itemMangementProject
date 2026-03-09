package com.pdfconverter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 商品标题识别配置类
 * 用于从 product-title-recognition-rules.properties 文件加载商品标题识别规则
 */
@Component
public class ProductTitleRecognitionConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductTitleRecognitionConfig.class);

    // 商品标题识别规则列表
    private List<ProductTitleRecognitionRule> recognitionRules = new ArrayList<>();

    // 配置文件路径
    private static final String CONFIG_FILE = "product-title-recognition-rules.properties";

    @PostConstruct
    public void init() {
        loadConfig();
    }

    /**
     * 从配置文件加载识别规则
     */
    public void loadConfig() {
        try {
            recognitionRules.clear();

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

                // 解析商品标题识别规则
                ProductTitleRecognitionRule rule = parseRecognitionRule(key, value);
                if (rule != null) {
                    recognitionRules.add(rule);
                }
            }

            reader.close();
            log.info("成功加载商品标题识别配置: 识别规则 {} 条", recognitionRules.size());

        } catch (Exception e) {
            log.error("加载商品标题识别配置文件失败", e);
        }
    }

    /**
     * 解析商品标题识别规则
     * 格式: <关键字列表>|<产品名称>|<产品大类>|<是否组合产品标识>
     */
    private ProductTitleRecognitionRule parseRecognitionRule(String key, String value) {
        try {
            String[] parts = value.split("\\|");
            if (parts.length != 4) {
                log.warn("商品标题识别规则格式错误: {}", value);
                return null;
            }

            ProductTitleRecognitionRule rule = new ProductTitleRecognitionRule();
            rule.setRuleKey(key);

            // 解析关键字列表（用逗号分隔）
            String[] keywords = parts[0].split(",");
            List<String> keywordList = new ArrayList<>();
            for (String keyword : keywords) {
                keyword = keyword.trim();
                if (!keyword.isEmpty()) {
                    keywordList.add(keyword.toLowerCase()); // 转为小写以便匹配
                }
            }
            rule.setKeywords(keywordList);

            // 解析产品名称
            rule.setProductName(parts[1].trim());

            // 解析产品大类
            rule.setProductCategory(parts[2].trim());

            // 解析是否组合产品标识
            Boolean isComposite = Boolean.parseBoolean(parts[3].trim());
            rule.setIsComposite(isComposite);

            return rule;

        } catch (Exception e) {
            log.error("解析商品标题识别规则失败: {}", value, e);
            return null;
        }
    }

    /**
     * 根据商品标题匹配识别规则
     * 标题必须包含规则中的所有关键字
     */
    public ProductTitleRecognitionRule findMatchingRule(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        String titleLower = title.toLowerCase().trim();

        for (ProductTitleRecognitionRule rule : recognitionRules) {
            List<String> keywords = rule.getKeywords();

            // 检查标题中是否包含该规则的所有关键字
            boolean allKeywordsMatched = true;
            for (String keyword : keywords) {
                if (!titleLower.contains(keyword)) {
                    allKeywordsMatched = false;
                    break;
                }
            }

            if (allKeywordsMatched) {
                return rule;
            }
        }

        return null;
    }

    // Getter and Setter
    public List<ProductTitleRecognitionRule> getRecognitionRules() {
        return recognitionRules;
    }

    public void setRecognitionRules(List<ProductTitleRecognitionRule> recognitionRules) {
        this.recognitionRules = recognitionRules;
    }

    /**
     * 商品标题识别规则
     */
    public static class ProductTitleRecognitionRule {
        private String ruleKey;
        private List<String> keywords;
        private String productName;
        private String productCategory;
        private Boolean isComposite;

        // Getters and Setters
        public String getRuleKey() {
            return ruleKey;
        }
        public void setRuleKey(String ruleKey) {
            this.ruleKey = ruleKey;
        }

        public List<String> getKeywords() {
            return keywords;
        }
        public void setKeywords(List<String> keywords) {
            this.keywords = keywords;
        }

        public String getProductName() {
            return productName;
        }
        public void setProductName(String productName) {
            this.productName = productName;
        }

        public String getProductCategory() {
            return productCategory;
        }
        public void setProductCategory(String productCategory) {
            this.productCategory = productCategory;
        }

        public Boolean getIsComposite() {
            return isComposite;
        }
        public void setIsComposite(Boolean isComposite) {
            this.isComposite = isComposite;
        }

        @Override
        public String toString() {
            return "ProductTitleRecognitionRule{" +
                    "ruleKey='" + ruleKey + '\'' +
                    ", keywords=" + keywords +
                    ", productName='" + productName + '\'' +
                    ", productCategory='" + productCategory + '\'' +
                    ", isComposite=" + isComposite +
                    '}';
        }
    }
}
