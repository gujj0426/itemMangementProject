package com.pdfconverter.model;

import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;

import java.util.ArrayList;
import java.util.List;

/**
 * 产品属性实体类，用于存放从 Personalization 中解析出来的附加产品信息
 * 包括附加产品名称、颜色、型号、样式、字体等属性
 */
public class ProductAttribute {
    // 附加产品名称列表（支持多个）
    private List<String> additionalProductNames;
    // 颜色
    private ProductColor color;
    // 型号
    private ProductSize size;
    //产品变量
    private String productVariable;
    // 样式
    private String style;
    // 字体
    private String font;
    // 原始 Personalization 内容（保留原始数据）
    private String originalPersonalization;

    // 无参构造器
    public ProductAttribute() {
        this.additionalProductNames = new ArrayList<>();
        this.color = ProductColor.UNKNOWN;
        this.style = "";
        this.font = "";
        this.size = ProductSize.UNKNOWN;
        this.originalPersonalization = "";
    }

    // Getter 和 Setter 方法
    public List<String> getAdditionalProductNames() {
        return additionalProductNames;
    }

    public void setAdditionalProductNames(List<String> additionalProductNames) {
        this.additionalProductNames = additionalProductNames;
    }

    // 添加单个产品名称的便捷方法
    public void addAdditionalProductName(String productName) {
        if (this.additionalProductNames == null) {
            this.additionalProductNames = new ArrayList<>();
        }
        this.additionalProductNames.add(productName);
    }
    public ProductColor getColor() {
        return color;
    }

    public void setColor(ProductColor color) {
        this.color = color;
    }

    public ProductSize getSize() {
        return size;
    }

    public void setSize(ProductSize size) {
        this.size = size;
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

    public String getOriginalPersonalization() {
        return originalPersonalization;
    }

    public void setOriginalPersonalization(String originalPersonalization) {
        this.originalPersonalization = originalPersonalization;
    }
    @Override
    public String toString() {
        return "ProductAttribute{" +
                "additionalProductNames=" + additionalProductNames +
                ", color=" + color +
                ", size='" + size + '\'' +
                ", style='" + style + '\'' +
                ", font='" + font + '\'' +
                ", originalPersonalization='" + originalPersonalization + '\'' +
                '}';
    }

    public String getProductVariable() {
        return productVariable;
    }

    public void setProductVariable(String productVariable) {
        this.productVariable = productVariable;
    }
}
