package com.pdfconverter.model;

import java.time.LocalDate;
import java.util.List;

/**
 * 订单数据模型类，仅适配3837329233-download-2025-10-23.pdf
 * 补充商品Size、Color字段，支持存储从动态属性中拆分的尺寸和颜色信息
 */
public class PdfOrderData {
    // 订单编号（对应PDF中"Order #3837329233"）{insert\_element\_0\_}
    private String orderNumber;
    // 客户姓名（对应PDF中"Alanayah McClain (alanayahmcclain)"括号外内容）{insert\_element\_1\_}
    private String customerName;
    // 用户名（对应PDF中"Ship to"下第一行"Alanayah McClain"）{insert\_element\_2\_}
    private String username;
    // 邮寄地址（对应PDF中"Ship to"下剩余内容"2918 Highwood Ct BESSEMER, AL 35023 United States"）{insert\_element\_3\_}
    private String shippingAddress;
    // 计划发货日期（对应PDF中"Scheduled to ship by Oct 25, 2025"）{insert\_element\_4\_}
    private LocalDate scheduledShippingDate;
    // 店铺名称（对应PDF中"Shop"下一行"TheVoro"）{insert\_element\_5\_}
    private String shopName;
    // 下单日期（对应PDF中"Order date Oct 23, 2025"）{insert\_element\_6\_}
    private LocalDate orderDate;
    // 支付方式（对应PDF中"Payment method"下一行"Paid via Etsy Payments"）{insert\_element\_7\_}
    private String paymentMethod;
    // 物流方式（对应PDF中"Shipping method"下一行"USPS First-Class Mail"）{insert\_element\_8\_}
    private String shippingMethod;
    // 包装信息（对应PDF中"Packaging"下一行"Package/Thick Envelope (5 x 5 x 5 in, 10oz)"）{insert\_element\_9\_}
    private String packagingInfo;
    // 物流跟踪信息（对应PDF中"Tracking"下一行"9400109206094334708108 via USPS"）{insert\_element\_10\_}
    private String trackingInfo;
    // 物流单号（拆分自跟踪信息）{insert\_element\_11\_}
    private String trackingNumber;
    // 物流公司（拆分自跟踪信息）{insert\_element\_12\_}
    private String courierCompany;
    // 商品总数量（动态获取自PDF中"5 items"的数字值）{insert\_element\_13\_}
    private int totalItemQuantity;
    // 商品详情列表（含Size、Color字段，与totalItemQuantity一致）
    private List<ItemDetail> itemDetails;
    // 附加备注（对应PDF底部"Do the green thing Reuse this paper..."）
    private String additionalNote;

    // 无参构造器
    public PdfOrderData() {
    }

    // 全参构造器
    public PdfOrderData(String orderNumber, String customerName, String username, String shippingAddress,
                        LocalDate scheduledShippingDate, String shopName, LocalDate orderDate, String paymentMethod,
                        String shippingMethod, String packagingInfo, String trackingInfo, String trackingNumber,
                        String courierCompany, int totalItemQuantity, List<ItemDetail> itemDetails, String additionalNote) {
        this.orderNumber = orderNumber;
        this.customerName = customerName;
        this.username = username;
        this.shippingAddress = shippingAddress;
        this.scheduledShippingDate = scheduledShippingDate;
        this.shopName = shopName;
        this.orderDate = orderDate;
        this.paymentMethod = paymentMethod;
        this.shippingMethod = shippingMethod;
        this.packagingInfo = packagingInfo;
        this.trackingInfo = trackingInfo;
        this.trackingNumber = trackingNumber;
        this.courierCompany = courierCompany;
        this.totalItemQuantity = totalItemQuantity;
        this.itemDetails = itemDetails;
        this.additionalNote = additionalNote;
    }

    // Getter和Setter方法（所有字段）
    public String getOrderNumber() {
        return orderNumber;
    }

    public void setOrderNumber(String orderNumber) {
        this.orderNumber = orderNumber;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getShippingAddress() {
        return shippingAddress;
    }

    public void setShippingAddress(String shippingAddress) {
        this.shippingAddress = shippingAddress;
    }

    public LocalDate getScheduledShippingDate() {
        return scheduledShippingDate;
    }

    public void setScheduledShippingDate(LocalDate scheduledShippingDate) {
        this.scheduledShippingDate = scheduledShippingDate;
    }

    public String getShopName() {
        return shopName;
    }

    public void setShopName(String shopName) {
        this.shopName = shopName;
    }

    public LocalDate getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDate orderDate) {
        this.orderDate = orderDate;
    }

    public String getPaymentMethod() {
        return paymentMethod;
    }

    public void setPaymentMethod(String paymentMethod) {
        this.paymentMethod = paymentMethod;
    }

    public String getShippingMethod() {
        return shippingMethod;
    }

    public void setShippingMethod(String shippingMethod) {
        this.shippingMethod = shippingMethod;
    }

    public String getPackagingInfo() {
        return packagingInfo;
    }

    public void setPackagingInfo(String packagingInfo) {
        this.packagingInfo = packagingInfo;
    }

    public String getTrackingInfo() {
        return trackingInfo;
    }

    public void setTrackingInfo(String trackingInfo) {
        this.trackingInfo = trackingInfo;
    }

    public String getTrackingNumber() {
        return trackingNumber;
    }

    public void setTrackingNumber(String trackingNumber) {
        this.trackingNumber = trackingNumber;
    }

    public String getCourierCompany() {
        return courierCompany;
    }

    public void setCourierCompany(String courierCompany) {
        this.courierCompany = courierCompany;
    }

    public int getTotalItemQuantity() {
        return totalItemQuantity;
    }

    public void setTotalItemQuantity(int totalItemQuantity) {
        this.totalItemQuantity = totalItemQuantity;
    }

    public List<ItemDetail> getItemDetails() {
        return itemDetails;
    }

    public void setItemDetails(List<ItemDetail> itemDetails) {
        this.itemDetails = itemDetails;
    }

    public String getAdditionalNote() {
        return additionalNote;
    }

    public void setAdditionalNote(String additionalNote) {
        this.additionalNote = additionalNote;
    }

    /**
     * 商品详情内部类（仅适配3837329233-download-2025-10-23.pdf）
     * 补充Size、Color字段，从动态属性中拆分；保留动态属性原始内容用于追溯
     */
    public static class ItemDetail {
        // 商品标题（单个商品从开始到"Quantity:"前的所有字符，如PDF中"Custom Engraved Initials Cufflinks Set-4 colors Groomsman Cufflinks-Groom Gift for Wedding Day"）{insert\_element\_14\_}
        private String itemTitle;
        // 商品数量（固定字段，对应"Quantity:"后的值，PDF中均为1）{insert\_element\_15\_}
        private int itemQuantity;
        // 商品尺寸（从动态属性中拆分，如PDF中"Gold_L"里的"L"、"Gold_S"里的"S"）{insert\_element\_16\_}
        private String size;
        //字体（Personalization中拆分）
        private String font;
        // 商品颜色（从动态属性中拆分，如PDF中"Gold_L"里的"Gold"、"Gold_S"里的"Gold"）{insert\_element\_17\_}
        private String color;
        // 商品动态属性原始内容（非固定字段，如"Size and Color: Gold_L | Item: Cufflink+TieClip+Box"）{insert\_element\_18\_}、{insert\_element\_19\_}
        private String dynamicAttributes;
        // 商品定制信息（固定字段，对应"Personalization:"后的值）{insert\_element\_20\_}
        private String itemPersonalization;
        // 新增字段：微调2-订单类型（袖扣/领带夹/宠物头像）
        private String orderType;
        // 新增字段：微调4-包装盒（存储含Box的词组）
        private String packagingBox;
        // 新增字段：信息（与ExcelData的information匹配）
        private String information;
        // 无参构造器（新增字段初始化）
        public ItemDetail() {
            this.orderType = ""; // 默认空字符串
            this.packagingBox = ""; // 默认空字符串
            this.font = "";
            this.information = "";
        }
        // 全参构造器（补充新增字段）
        public ItemDetail(String itemTitle, int itemQuantity, String size, String color,
                          String dynamicAttributes, String itemPersonalization,
                          String orderType, String packagingBox, String font, String information){
            this.itemTitle = itemTitle;
            this.itemQuantity = itemQuantity;
            this.size = size;
            this.color = color;
            this.dynamicAttributes = dynamicAttributes;
            this.itemPersonalization = itemPersonalization;
            this.orderType = orderType;
            this.packagingBox = packagingBox;
            this.font = font;
            this.information = information;
        }

        // Getter和Setter方法
        public String getItemTitle() {
            return itemTitle;
        }

        public void setItemTitle(String itemTitle) {
            this.itemTitle = itemTitle;
        }

        public int getItemQuantity() {
            return itemQuantity;
        }

        public void setItemQuantity(int itemQuantity) {
            this.itemQuantity = itemQuantity;
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

        public String getDynamicAttributes() {
            return dynamicAttributes;
        }

        public void setDynamicAttributes(String dynamicAttributes) {
            this.dynamicAttributes = dynamicAttributes;
        }

        public String getItemPersonalization() {
            return itemPersonalization;
        }

        public void setItemPersonalization(String itemPersonalization) {
            this.itemPersonalization = itemPersonalization;
        }
        // 新增字段的Getter和Setter（原有字段的Getter/Setter不变）
        public String getOrderType() {
            return orderType;
        }

        public void setOrderType(String orderType) {
            this.orderType = orderType;
        }
        // 新增字段的Getter和Setter
        public String getPackagingBox() {
            return packagingBox;
        }

        public void setPackagingBox(String packagingBox) {
            this.packagingBox = packagingBox;
        }

        public String getFont() {
            return font;
        }

        public void setFont(String font) {
            this.font = font;
        }

        public String getInformation() {
            return information;
        }

        public void setInformation(String information) {
            this.information = information;
        }
    }

}