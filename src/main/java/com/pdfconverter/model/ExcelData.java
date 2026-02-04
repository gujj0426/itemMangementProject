package com.pdfconverter.model;

public class ExcelData {
    /** 日期 */
    private String date;
    /** 序号 */
    private String serialNumber;
    /** 用户名 */
    private String username;
    /** 订单编号 */
    private String orderNumber;
    /** 备用信息字段 */
    private String info;
    /** 订单类型 */
    private String orderType;
    /** 袖扣风格 */
    private String cufflinkStyle;
    /** 字体 */
    private String font;
    /** 领带风格 */
    private String tieStyle;
    /** 设计师 */
    private String designer;
    /** 尺寸 */
    private String size;
    /** 颜色 */
    private String color;
    /** 数量 */
    private String quantity;
    /** 包装盒 */
    private String packagingBox;
    /** 盒数量 */
    private String boxQuantity;
    /** 个性化设置 */
    private String personalization;
    /** 商品信息栏完全信息 */
    private String information;
    /** 商品标题 */
    private String itemTitle;
    /** 产品变量（用于存储产品的完整描述，如"Oval Box-椭圆形开窗木盒"）*/
    private String dynamicAttributes;
    /** 样式（从Personalization中提取的样式/字体信息）*/
    private String style;
    /** 商品图片字节数组（PNG格式）*/
    private byte[] imageBytes;


    // Getters and Setters
    public String getPackagingBox() { return packagingBox; }
    public void setPackagingBox(String packagingBox) { this.packagingBox = packagingBox; }
    public String getItemTitle() { return itemTitle; }
    public void setItemTitle(String itemTitle) { this.itemTitle = itemTitle; }
    public String getInformation() { return information; }
    public void setInformation(String information) { this.information = information; }
    public String getDate() { return date; }
    public void setDate(String date) { this.date = date; }

    public String getSerialNumber() { return serialNumber; }
    public void setSerialNumber(String serialNumber) { this.serialNumber = serialNumber; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getOrderNumber() { return orderNumber; }
    public void setOrderNumber(String orderNumber) { this.orderNumber = orderNumber; }

    public String getInfo() { return info; }
    public void setInfo(String info) { this.info = info; }

    public String getOrderType() { return orderType; }
    public void setOrderType(String orderType) { this.orderType = orderType; }

    public String getCufflinkStyle() { return cufflinkStyle; }
    public void setCufflinkStyle(String cufflinkStyle) { this.cufflinkStyle = cufflinkStyle; }

    public String getFont() { return font; }
    public void setFont(String font) { this.font = font; }

    public String getTieStyle() { return tieStyle; }
    public void setTieStyle(String tieStyle) { this.tieStyle = tieStyle; }

    public String getDesigner() { return designer; }
    public void setDesigner(String designer) { this.designer = designer; }

    public String getSize() { return size; }
    public void setSize(String size) { this.size = size; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getQuantity() { return quantity; }
    public void setQuantity(String quantity) { this.quantity = quantity; }

    public String getBoxQuantity() { return boxQuantity; }
    public void setBoxQuantity(String boxQuantity) { this.boxQuantity = boxQuantity; }

    public String getPersonalization() { return personalization; }
    public void setPersonalization(String personalization) { this.personalization = personalization; }

    public String getDynamicAttributes() { return dynamicAttributes; }
    public void setDynamicAttributes(String dynamicAttributes) { this.dynamicAttributes = dynamicAttributes; }

    public String getStyle() { return style; }
    public void setStyle(String style) { this.style = style; }

    public byte[] getImageBytes() { return imageBytes; }
    public void setImageBytes(byte[] imageBytes) { this.imageBytes = imageBytes; }
}