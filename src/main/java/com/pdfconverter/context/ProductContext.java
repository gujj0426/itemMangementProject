package com.pdfconverter.context;

import com.pdfconverter.service.*;
import lombok.Data;

import java.util.Map;
import java.util.Set;

/**
 * 产品解析上下文
 * 在解析过程中传递所有需要的配置和服务依赖
 */
@Data
public class ProductContext {

    // 服务依赖
    private AttributeExtractor attributeExtractor;
    private ColorMapperService colorMapper;
    private ProductVariableMapperService productVariableMapper;
    private AccessoryItemFactory accessoryFactory;
    // 商品标题识别服务
    private ProductTitleRecognitionService titleRecognition;

    // 动态属性
    private Map<String, String> dynamicAttrsMap;

    // 商品类型
    private Set<String> productTypes;

    // 商品标题
    private String itemTitle;

    // 商品数量
    private int quantity;

    // 定制信息
    private String personalization;

    // 原始动态属性文本
    private String dynamicSection;

    // 构造器
    public ProductContext() {
    }

    // Getter和Setter方法
    public AttributeExtractor getAttributeExtractor() {
        return attributeExtractor;
    }

    public void setAttributeExtractor(AttributeExtractor attributeExtractor) {
        this.attributeExtractor = attributeExtractor;
    }

    public ColorMapperService getColorMapper() {
        return colorMapper;
    }

    public void setColorMapper(ColorMapperService colorMapper) {
        this.colorMapper = colorMapper;
    }

    public ProductVariableMapperService getProductVariableMapper() {
        return productVariableMapper;
    }

    public void setProductVariableMapper(ProductVariableMapperService productVariableMapper) {
        this.productVariableMapper = productVariableMapper;
    }

    public AccessoryItemFactory getAccessoryFactory() {
        return accessoryFactory;
    }

    public void setAccessoryFactory(AccessoryItemFactory accessoryFactory) {
        this.accessoryFactory = accessoryFactory;
    }

    public ProductTitleRecognitionService getTitleRecognition() {
        return titleRecognition;
    }

    public void setTitleRecognition(ProductTitleRecognitionService titleRecognition) {
        this.titleRecognition = titleRecognition;
    }

    public Map<String, String> getDynamicAttrsMap() {
        return dynamicAttrsMap;
    }

    public void setDynamicAttrsMap(Map<String, String> dynamicAttrsMap) {
        this.dynamicAttrsMap = dynamicAttrsMap;
    }

    public Set<String> getProductTypes() {
        return productTypes;
    }

    public void setProductTypes(Set<String> productTypes) {
        this.productTypes = productTypes;
    }

    public String getItemTitle() {
        return itemTitle;
    }

    public void setItemTitle(String itemTitle) {
        this.itemTitle = itemTitle;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public String getPersonalization() {
        return personalization;
    }

    public void setPersonalization(String personalization) {
        this.personalization = personalization;
    }

    public String getDynamicSection() {
        return dynamicSection;
    }

    public void setDynamicSection(String dynamicSection) {
        this.dynamicSection = dynamicSection;
    }

    /**
     * 创建构建器
     */
    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private ProductContext context = new ProductContext();

        public Builder attributeExtractor(AttributeExtractor attributeExtractor) {
            context.setAttributeExtractor(attributeExtractor);
            return this;
        }

        public Builder colorMapper(ColorMapperService colorMapper) {
            context.setColorMapper(colorMapper);
            return this;
        }

        public Builder getProductVariableMapper(ProductVariableMapperService productVariableMapper) {
            context.setProductVariableMapper(productVariableMapper);
            return this;
        }

        public Builder accessoryFactory(AccessoryItemFactory accessoryFactory) {
            context.setAccessoryFactory(accessoryFactory);
            return this;
        }

        public Builder titleRecognition(ProductTitleRecognitionService titleRecognition) {
            context.setTitleRecognition(titleRecognition);
            return this;
        }

        public Builder dynamicAttrsMap(Map<String, String> dynamicAttrsMap) {
            context.setDynamicAttrsMap(dynamicAttrsMap);
            return this;
        }

        public Builder productTypes(Set<String> productTypes) {
            context.setProductTypes(productTypes);
            return this;
        }

        public Builder itemTitle(String itemTitle) {
            context.setItemTitle(itemTitle);
            return this;
        }

        public Builder quantity(int quantity) {
            context.setQuantity(quantity);
            return this;
        }

        public Builder personalization(String personalization) {
            context.setPersonalization(personalization);
            return this;
        }

        public Builder dynamicSection(String dynamicSection) {
            context.setDynamicSection(dynamicSection);
            return this;
        }

        public ProductContext build() {
            return context;
        }
    }
}
