package com.pdfconverter.model;

/**
 * 产品动态属性实体类
 * 用于存储产品的基本属性信息
 */
public class ProductAttribute {

    /**
     * 产品名称
     */
    private String productName;

    /**
     * 型号
     */
    private String size;

    /**
     * 颜色
     */
    private String color;

    /**
     * 产品变量
     * 用于存储产品的完整描述或变量信息，如"Oval Box-椭圆形开窗木盒"
     */
    private String productVariable;

    /**
     * 无参构造器
     */
    public ProductAttribute() {
    }

    /**
     * 全参构造器
     */
    public ProductAttribute(String productName, String model, String color, String productVariable) {
        this.productName = productName;
        this.size = size;
        this.color = color;
        this.productVariable = productVariable;
    }

    // Getter 和 Setter 方法

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
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

    @Override
    public String toString() {
        return "ProductAttribute{" +
                "productName='" + productName + '\'' +
                ", size='" + size + '\'' +
                ", color='" + color + '\'' +
                ", productVariable='" + productVariable + '\'' +
                '}';
    }
}
