package com.pdfconverter.model;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;

import java.util.*;

/**
 * 产品项 - 统一表示主产品和附属产品
 * 增强版设计，支持属性继承和动态配置
 */
public class ProductItem {
    private String id;                      // 唯一标识
    private String sourceTitle;             // 原始标题
    private OrderType productType;          // 产品类型
    private ProductName productName;        // 产品名称（中文）
    private ProductSize size;               // 型号
    private ProductColor color;             // 颜色
    private ProductVariable variable;       // 产品变量
    private int quantity;                   // 数量
    private boolean isMainProduct;          // 是否主产品
    private String parentItemId;            // 父产品ID（附属产品用）
    private Map<String, String> dynamicAttributes; // 动态属性
    private String personalization;         // 个性化信息
    private String style;                   // 样式
    private String font;                    // 字体
    private Set<String> appliedRules;       // 已应用的规则ID
    private Date createTime;                // 创建时间

    public ProductItem() {
        this.quantity = 1;
        this.isMainProduct = true;
        this.dynamicAttributes = new HashMap<>();
        this.appliedRules = new HashSet<>();
        this.createTime = new Date();
        this.id = UUID.randomUUID().toString();
    }

    /**
     * 从父产品继承属性
     * @param parent 父产品项
     * @param inheritColor 是否继承颜色
     * @param inheritSize 是否继承型号
     * @param inheritVariable 是否继承产品变量
     */
    public void inheritAttributesFrom(ProductItem parent, boolean inheritColor, boolean inheritSize, boolean inheritVariable) {
        if (parent == null) {
            return;
        }

        // 继承颜色（如果配置允许且当前未设置）
        if (inheritColor && (this.color == null || this.color == ProductColor.UNKNOWN)) {
            this.color = parent.getColor();
        }

        // 继承型号（如果配置允许且当前未设置）
        if (inheritSize && (this.size == null || this.size == ProductSize.UNKNOWN)) {
            this.size = parent.getSize();
        }

        // 继承产品变量（如果配置允许且当前未设置）
        if (inheritVariable && (this.variable == null || this.variable == ProductVariable.UNKNOWN)) {
            this.variable = parent.getVariable();
        }

        // 记录继承关系
        this.parentItemId = parent.getId();
    }

    /**
     * 从父产品继承所有属性
     */
    public void inheritAllAttributesFrom(ProductItem parent) {
        inheritAttributesFrom(parent, true, true, true);
    }

    /**
     * 添加动态属性
     */
    public void putDynamicAttribute(String key, String value) {
        dynamicAttributes.put(key, value);
    }

    /**
     * 获取动态属性
     */
    public String getDynamicAttribute(String key) {
        return dynamicAttributes.get(key);
    }

    /**
     * 记录已应用的规则
     */
    public void addAppliedRule(String ruleId) {
        appliedRules.add(ruleId);
    }

    /**
     * 检查是否已应用指定规则
     */
    public boolean hasAppliedRule(String ruleId) {
        return appliedRules.contains(ruleId);
    }

    /**
     * 获取产品摘要信息
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(productName != null ? productName.getDisplayName() : "未知产品");
        if (size != null && size != ProductSize.UNKNOWN) {
            sb.append(" | ").append(size.getDisplayName());
        }
        if (color != null && color != ProductColor.UNKNOWN) {
            sb.append(" | ").append(color.getDisplayName());
        }
        if (variable != null && variable != ProductVariable.UNKNOWN) {
            sb.append(" | ").append(variable.getDisplayName());
        }
        sb.append(" x").append(quantity);
        if (!isMainProduct) {
            sb.append(" (附属)");
        }
        return sb.toString();
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getSourceTitle() {
        return sourceTitle;
    }

    public void setSourceTitle(String sourceTitle) {
        this.sourceTitle = sourceTitle;
    }

    public OrderType getProductType() {
        return productType;
    }

    public void setProductType(OrderType productType) {
        this.productType = productType;
    }

    public ProductName getProductName() {
        return productName;
    }

    public void setProductName(ProductName productName) {
        this.productName = productName;
    }

    public ProductSize getSize() {
        return size;
    }

    public void setSize(ProductSize size) {
        this.size = size;
    }

    public ProductColor getColor() {
        return color;
    }

    public void setColor(ProductColor color) {
        this.color = color;
    }

    public ProductVariable getVariable() {
        return variable;
    }

    public void setVariable(ProductVariable variable) {
        this.variable = variable;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public boolean isMainProduct() {
        return isMainProduct;
    }

    public void setMainProduct(boolean mainProduct) {
        isMainProduct = mainProduct;
    }

    public String getParentItemId() {
        return parentItemId;
    }

    public void setParentItemId(String parentItemId) {
        this.parentItemId = parentItemId;
    }

    public Map<String, String> getDynamicAttributes() {
        return dynamicAttributes;
    }

    public void setDynamicAttributes(Map<String, String> dynamicAttributes) {
        this.dynamicAttributes = dynamicAttributes;
    }

    public String getPersonalization() {
        return personalization;
    }

    public void setPersonalization(String personalization) {
        this.personalization = personalization;
    }

    public String getStyle() {
        return style;
    }

    public void setStyle(String style) {
        this.style = style;
    }

    public String getFont() {
        return font;
    }

    public void setFont(String font) {
        this.font = font;
    }

    public Set<String> getAppliedRules() {
        return appliedRules;
    }

    public void setAppliedRules(Set<String> appliedRules) {
        this.appliedRules = appliedRules;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "ProductItem{" +
                "id='" + id + '\'' +
                ", sourceTitle='" + sourceTitle + '\'' +
                ", productType=" + productType +
                ", productName=" + productName +
                ", size=" + size +
                ", color=" + color +
                ", variable=" + variable +
                ", quantity=" + quantity +
                ", isMainProduct=" + isMainProduct +
                ", parentItemId='" + parentItemId + '\'' +
                '}';
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ProductItem that = (ProductItem) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
