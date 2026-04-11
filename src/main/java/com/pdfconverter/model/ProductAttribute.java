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
    // 附加产品orderTypeCode列表（如["Tie Clip","Box"]），用于组装 ItemDetail.orderType
    private List<String> additionalProductNames;
    // 附加产品ProductName.nameCode列表（如["Tie Clip Duck Bill","Packaging Box"]），用于查产品清单
    private List<String> additionalProductNameCodes;
    // 附加产品包装盒变量列表（与additionalProductNames等长，非Box类型为空串），如 "Oval Box-椭圆形开窗木盒"
    private List<String> additionalBoxVariables;
    // 附加产品独立尺寸列表（与additionalProductNames等长，为空时继承主商品尺寸），如 "L"
    private List<String> additionalSizes;
    // 颜色
    private ProductColor color;
    // 型号
    private ProductSize size;
    //产品变量
    private String productVariable;
    // 片数（用于圆片吊坠等需要拆行的商品）
    private Integer pieceCount;
    // 样式
    private String style;
    // 字体
    private String font;
    // 原始 Personalization 内容（保留原始数据）
    private String originalPersonalization;
    // 硅胶绑带颜色（用于狗牌产品的附属商品）
    private ProductColor siliconeBandColor;

    // 无参构造器
    public ProductAttribute() {
        this.additionalProductNames = new ArrayList<>();
        this.additionalProductNameCodes = new ArrayList<>();
        this.additionalBoxVariables = new ArrayList<>();
        this.additionalSizes = new ArrayList<>();
        this.color = ProductColor.UNKNOWN;
        this.style = "";
        this.font = "";
        this.size = ProductSize.UNKNOWN;
        this.originalPersonalization = "";
        this.siliconeBandColor = ProductColor.UNKNOWN;
        this.pieceCount = null;
    }

    // Getter 和 Setter 方法
    public List<String> getAdditionalProductNames() {
        return additionalProductNames;
    }

    public void setAdditionalProductNames(List<String> additionalProductNames) {
        this.additionalProductNames = additionalProductNames;
    }

    public List<String> getAdditionalProductNameCodes() {
        return additionalProductNameCodes;
    }

    public void setAdditionalProductNameCodes(List<String> additionalProductNameCodes) {
        this.additionalProductNameCodes = additionalProductNameCodes;
    }

    public List<String> getAdditionalBoxVariables() {
        return additionalBoxVariables;
    }

    public void setAdditionalBoxVariables(List<String> additionalBoxVariables) {
        this.additionalBoxVariables = additionalBoxVariables;
    }

    public List<String> getAdditionalSizes() {
        return additionalSizes;
    }

    public void setAdditionalSizes(List<String> additionalSizes) {
        this.additionalSizes = additionalSizes;
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

    public Integer getPieceCount() {
        return pieceCount;
    }

    public void setPieceCount(Integer pieceCount) {
        this.pieceCount = pieceCount;
    }

    public ProductColor getSiliconeBandColor() {
        return siliconeBandColor;
    }

    public void setSiliconeBandColor(ProductColor siliconeBandColor) {
        this.siliconeBandColor = siliconeBandColor;
    }
}
