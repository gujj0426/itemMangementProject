package com.pdfconverter.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.InputStream;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 产品属性标签配置
 * 从 product-attribute-labels.json 加载配置
 * 支持两种索引方式：
 * 1. listingId -> 属性标签列表（推荐，用于区分同一ProductName下的不同listing）
 * 2. productName -> 属性标签列表（向后兼容）
 */
@Component
public class ProductAttributeConfig {

    private static final Logger log = LoggerFactory.getLogger(ProductAttributeConfig.class);

    private static final String CONFIG_FILE = "product-attribute-labels.json";

    // listingId -> 属性标签列表（新索引方式）
    private Map<String, List<AttributeLabel>> listingLabelMap;

    // 产品名称 -> 属性标签列表（旧索引方式，向后兼容）
    private Map<String, List<AttributeLabel>> productLabelMap;

    // 标签名称 -> 标签配置（用于快速查找）
    private Map<String, AttributeLabel> labelConfigMap;
    
    @PostConstruct
    public void init() {
        loadConfig();
    }
    
    /**
     * 加载配置文件
     */
    public void loadConfig() {
        long startTime = System.currentTimeMillis();
        log.info("开始加载产品属性标签配置：{}", CONFIG_FILE);

        try {
            // 初始化Map
            listingLabelMap = new ConcurrentHashMap<>();
            productLabelMap = new ConcurrentHashMap<>();
            labelConfigMap = new ConcurrentHashMap<>();

            // 读取JSON文件
            ClassPathResource resource = new ClassPathResource(CONFIG_FILE);
            if (!resource.exists()) {
                log.error("配置文件不存在：{}", CONFIG_FILE);
                return;
            }

            ObjectMapper mapper = new ObjectMapper();
            mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            try (InputStream is = resource.getInputStream()) {
                JsonConfig config = mapper.readValue(is, JsonConfig.class);

                // 支持新旧两种JSON结构
                // 新结构: listingAttributes -> listingId -> attributeLabels
                // 旧结构: productAttributeLabels -> productName -> attributeLabels

                int listingCount = 0;
                int productCount = 0;

                // 解析新结构：listingAttributes
                if (config.listingAttributes != null && !config.listingAttributes.isEmpty()) {
                    for (ListingAttribute la : config.listingAttributes) {
                        String listingId = la.listingId;
                        List<AttributeLabel> labels = la.attributeLabels;

                        if (listingId != null && !listingId.isEmpty() &&
                            labels != null && !labels.isEmpty()) {
                            listingLabelMap.put(listingId, labels);
                            log.debug("加载 listing [{}] 的属性标签配置，共 {} 个标签",
                                     listingId, labels.size());

                            // 添加到标签配置映射
                            for (AttributeLabel label : labels) {
                                String labelKey = buildLabelKey(listingId, label.labelName);
                                labelConfigMap.put(labelKey, label);
                            }
                            listingCount++;
                        }
                    }
                }

                // 解析旧结构：productAttributeLabels（向后兼容）
                if (config.productAttributeLabels != null && !config.productAttributeLabels.isEmpty()) {
                    for (ProductAttributeLabels pal : config.productAttributeLabels) {
                        String productName = pal.productName;
                        List<AttributeLabel> labels = pal.attributeLabels;

                        if (productName == null || labels == null || labels.isEmpty()) {
                            log.warn("无效的产品配置：productName={}", productName);
                            continue;
                        }

                        // 添加到产品-标签映射
                        productLabelMap.put(productName, labels);
                        log.debug("加载产品 [{}] 的属性标签配置，共 {} 个标签", productName, labels.size());

                        // 添加到标签配置映射
                        for (AttributeLabel label : labels) {
                            String labelKey = buildLabelKey(productName, label.labelName);
                            labelConfigMap.put(labelKey, label);
                        }
                        productCount++;
                    }
                }

                long endTime = System.currentTimeMillis();
                log.info("产品属性标签配置加载完成，listing配置 {} 个，product配置 {} 个，耗时 {}ms",
                         listingCount, productCount, endTime - startTime);
            }

        } catch (Exception e) {
            log.error("加载配置文件失败：" + CONFIG_FILE, e);
            throw new RuntimeException("Failed to load product attribute config", e);
        }
    }
    
    /**
     * 获取指定listing对应的属性标签列表（优先使用listingId索引）
     *
     * @param listingId listing标识
     * @param productName 产品名称（用于向后兼容兜底）
     * @return 属性标签列表，如果未找到返回空列表
     */
    public List<AttributeLabel> getLabelsForListing(String listingId, String productName) {
        // 优先从 listingLabelMap 查找
        if (listingId != null && !listingId.isEmpty()) {
            List<AttributeLabel> labels = listingLabelMap.get(listingId);
            if (labels != null && !labels.isEmpty()) {
                return labels;
            }
        }

        // 兜底：从 productLabelMap 查找（向后兼容）
        return productLabelMap.getOrDefault(productName, Collections.emptyList());
    }

    /**
     * 获取产品对应的属性标签列表（旧接口，向后兼容）
     *
     * @param productName 产品名称
     * @return 属性标签列表，如果未找到返回空列表
     */
    public List<AttributeLabel> getLabelsForProduct(String productName) {
        return productLabelMap.getOrDefault(productName, Collections.emptyList());
    }
    
    /**
     * 获取产品的所有必需属性标签
     * 
     * @param productName 产品名称
     * @return 必需的属性标签列表
     */
    public List<AttributeLabel> getRequiredLabelsForProduct(String productName) {
        List<AttributeLabel> allLabels = getLabelsForProduct(productName);
        List<AttributeLabel> requiredLabels = new ArrayList<>();
        
        for (AttributeLabel label : allLabels) {
            if (label.required) {
                requiredLabels.add(label);
            }
        }
        
        return requiredLabels;
    }
    
    /**
     * 检查产品是否配置了指定的属性标签
     * 
     * @param productName 产品名称
     * @param labelName 标签名称
     * @return true如果配置了该标签
     */
    public boolean hasLabel(String productName, String labelName) {
        List<AttributeLabel> labels = getLabelsForProduct(productName);
        for (AttributeLabel label : labels) {
            if (label.labelName.equals(labelName)) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 获取指定产品的标签配置
     * 
     * @param productName 产品名称
     * @param labelName 标签名称
     * @return 标签配置，如果未找到返回null
     */
    public AttributeLabel getLabelConfig(String productName, String labelName) {
        String labelKey = buildLabelKey(productName, labelName);
        return labelConfigMap.get(labelKey);
    }
    
    /**
     * 构建标签Key（用于快速查找）
     */
    private String buildLabelKey(String productName, String labelName) {
        return productName + "::" + labelName;
    }
    
    /**
     * 重新加载配置
     */
    public void reloadConfig() {
        log.info("重新加载产品属性标签配置...");
        loadConfig();
    }
    
    /**
     * 获取已加载的产品数量
     */
    public int getLoadedProductCount() {
        return productLabelMap != null ? productLabelMap.size() : 0;
    }
    
    /**
     * JSON配置结构
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class JsonConfig {
        public List<ListingAttribute> listingAttributes;
        public List<ProductAttributeLabels> productAttributeLabels;
    }

    /**
     * Listing属性配置（新结构）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ListingAttribute {
        public String listingId;
        public List<AttributeLabel> attributeLabels;
    }

    /**
     * 产品属性配置（旧结构，向后兼容）
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class ProductAttributeLabels {
        public String productName;
        public List<AttributeLabel> attributeLabels;
    }
    
    /**
     * 属性标签类
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class AttributeLabel {
        public String labelName;
        public String comment;
        public String attributeType;
        public boolean required;
        public String extractionPattern;
        public String valuePattern;
        public Map<String, String> groupMapping;
        /** ACCESSORY_ITEMS 类型：分隔符列表，如 ["+", ","] */
        public List<String> separators;
        /** ACCESSORY_ITEMS 类型：商品名称 -> OrderType.orderTypeCode 映射 */
        public Map<String, String> itemTypeMapping;
        /** ACCESSORY_ITEMS 类型：OrderType.orderTypeCode -> ProductName.nameCode 映射（用于查产品清单）*/
        public Map<String, String> productNameMapping;
        /** ACCESSORY_ITEMS / COLOR_WITH_ACCESSORY 类型：附属商品原始名称 -> 包装盒产品变量（CSV产品变量列），仅Box类型用 */
        public Map<String, String> boxVariableMapping;
        /**
         * COLOR_WITH_ACCESSORY 类型：当值中不含 "+" 时的降级默认礼盒产品变量。
         * 例如 PDF 中某些订单 "Color Finish: Gold"（无礼盒部分），
         * 此时仍需要输出礼盒行，使用此默认变量（如 "Oval Box-椭圆形开窗木盒"）。
         */
        public String defaultBoxVariable;
        
        @Override
        public String toString() {
            return "AttributeLabel{" +
                   "labelName='" + labelName + '\'' +
                   ", attributeType='" + attributeType + '\'' +
                   ", required=" + required +
                   '}';
        }
    }
}
