package com.pdfconverter.model;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductVariable;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Set;

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
    // 初始序号（用于Excel文件中的序号起始值）
    private int initialIndex;
    // 领带夹 Style 6 标识：解析阶段通过 itemDetails 计算得出
    // true = 存在领带夹商品且其 personalization 含 S6/Style 6
    private boolean hasTieClipStyle6;

    // 静音狗牌 S码标识：解析阶段通过 itemDetails 计算得出
    // true = 存在静音狗牌商品且型号为 S（含S/M合并档）
    private boolean hasSilentDogTagS;

    // 无参构造器
    public PdfOrderData() {
        this.initialIndex = 1; // 默认值为1
    }

    // 全参构造器
    public PdfOrderData(String orderNumber, String customerName, String username, String shippingAddress,
                        LocalDate scheduledShippingDate, String shopName, LocalDate orderDate, String paymentMethod,
                        String shippingMethod, String packagingInfo, String trackingInfo, String trackingNumber,
                        String courierCompany, int totalItemQuantity, List<ItemDetail> itemDetails, String additionalNote) {
        this(orderNumber, customerName, username, shippingAddress, scheduledShippingDate, shopName, orderDate, paymentMethod,
                shippingMethod, packagingInfo, trackingInfo, trackingNumber, courierCompany, totalItemQuantity, itemDetails, additionalNote, 1);
    }

    public PdfOrderData(String orderNumber, String customerName, String username, String shippingAddress,
                        LocalDate scheduledShippingDate, String shopName, LocalDate orderDate, String paymentMethod,
                        String shippingMethod, String packagingInfo, String trackingInfo, String trackingNumber,
                        String courierCompany, int totalItemQuantity, List<ItemDetail> itemDetails, String additionalNote, int initialIndex) {
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
        this.initialIndex = initialIndex;
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

    public String getAdditionalNote() {
        return additionalNote;
    }

    public void setAdditionalNote(String additionalNote) {
        this.additionalNote = additionalNote;
    }

    public int getInitialIndex() {
        return initialIndex;
    }

    public void setInitialIndex(int initialIndex) {
        this.initialIndex = initialIndex;
    }

    public boolean isHasTieClipStyle6() {
        return hasTieClipStyle6;
    }

    public void setHasTieClipStyle6(boolean hasTieClipStyle6) {
        this.hasTieClipStyle6 = hasTieClipStyle6;
    }

    public boolean isHasSilentDogTagS() {
        return hasSilentDogTagS;
    }

    public void setHasSilentDogTagS(boolean hasSilentDogTagS) {
        this.hasSilentDogTagS = hasSilentDogTagS;
    }

    public List<ItemDetail> getItemDetails() {
        return itemDetails;
    }
    public void setItemDetails(List<ItemDetail> itemDetails) {
        this.itemDetails = itemDetails;
    }
    /**
     * 商品详情内部类
     */
    public static class ItemDetail {
        // 主商品标识
        private Boolean mainProductFlg;
        // 是否组合产品标识
        private Boolean isComposite;
        // listing标识（用于区分同一ProductName下的不同listing）
        private String listingId;
        // 商品标题
        private String itemTitle;
        // 商品数量
        private int itemQuantity;
        // 订单类型
        private OrderType orderType;
        // 商品名称
        private ProductName productName;
        // 商品尺寸
        private ProductSize productSize;
        // 商品颜色（英文）
        private ProductColor productColor;
        // 产品变量
        private ProductVariable productVariable;
        // 动态属性原始内容
        private String dynamicAttributes;
        // 个性化信息
        private String personalization;
        // 字体
        private String font;
        // 样式
        private String style;
        /** LLM 校验后的标准设计风格（允许列表内）；可选 */
        private String llmDesignStyle;
        /** LLM 校验后的标准字体（允许列表内）；可选 */
        private String llmFont;
        /** LLM 抽取的刻录正文 */
        private String llmEngravingContent;
        /** LLM 校验后的 Icon（icon #1 … icon #90）；可选 */
        private String llmIcon;
        /** true 表示刻录拆行/收窄已应用，Excel 不再按动态属性面数倍增 */
        private boolean engravingFanOutApplied;
        /** 传给 LLM 的 Personalization 片段；为 null 则用 {@link #personalization} 全文 */
        private String personalizationTextForLlm;
        /** 槽位说明（正反面 / 品类），写入 LLM user prompt */
        private String llmSlotInstruction;
        /**
         * 同一 PDF 订单内「Quantity 商品块」序号（主商品+附属+附加商品同属一块）。
         * 用于 LLM 按块只调用一次接口后在块内分发 llm* 字段。
         */
        private Integer sourceBlockIndex;
        /** 所属 Etsy 订单号（Order #），便于日志与 DeepSeek 调用定位 */
        private String orderNumber;
        // 无参构造器
        public ItemDetail() {
            this.mainProductFlg = false;
            this.isComposite = false;
            this.listingId = null;
            this.orderType = OrderType.UNKNOWN;
            this.productSize = ProductSize.UNKNOWN;
            this.productColor = ProductColor.UNKNOWN;
            this.productName = ProductName.UNKNOWN;
            this.productVariable = ProductVariable.UNKNOWN;
            this.font = "";
            this.style = "";
        }
        // Getter和Setter方法
        public String getItemTitle() {
            return itemTitle;
        }

        public void setItemTitle(String itemTitle) {
            this.itemTitle = itemTitle;
        }

        public Boolean getMainProductFlg() {
            return mainProductFlg;
        }

        public void setMainProductFlg(Boolean mainProductFlg) {
            this.mainProductFlg = mainProductFlg;
        }

        public Boolean getIsComposite() {
            return isComposite;
        }

        public void setIsComposite(Boolean isComposite) {
            this.isComposite = isComposite;
        }

        public int getItemQuantity() {
            return itemQuantity;
        }

        public void setItemQuantity(int itemQuantity) {
            this.itemQuantity = itemQuantity;
        }

        public ProductSize getProductSize() {
            return productSize;
        }

        public void setProductSize(ProductSize productSize) {
            this.productSize = productSize;
        }

        public ProductColor getProductColor() {
            return productColor;
        }

        public void setProductColor(ProductColor productColor) {
            this.productColor = productColor;
        }

        public String getDynamicAttributes() {
            return dynamicAttributes;
        }

        public void setDynamicAttributes(String dynamicAttributes) {
            this.dynamicAttributes = dynamicAttributes;
        }

        public String getPersonalization() {
            return personalization;
        }

        public void setPersonalization(String personalization) {
            this.personalization = personalization;
        }
        // 新增字段的Getter和Setter（原有字段的Getter/Setter不变）
        public OrderType getOrderType() {
            return orderType;
        }

        public void setOrderType(OrderType orderType) {
            this.orderType = orderType;
        }

        public String getFont() {
            return font;
        }

        public void setFont(String font) {
            this.font = font;
        }

        public ProductName getProductName() {
            return productName;
        }

        public void setProductName(ProductName productName) {
            this.productName = productName;
        }

        public ProductVariable getProductVariable() {
            return productVariable;
        }

        public void setProductVariable(ProductVariable productVariable) {
            this.productVariable = productVariable;
        }

        public String getStyle() {
            return style;
        }

        public void setStyle(String style) {
            this.style = style;
        }

        public String getLlmDesignStyle() {
            return llmDesignStyle;
        }

        public void setLlmDesignStyle(String llmDesignStyle) {
            this.llmDesignStyle = llmDesignStyle;
        }

        public String getLlmFont() {
            return llmFont;
        }

        public void setLlmFont(String llmFont) {
            this.llmFont = llmFont;
        }

        public String getLlmEngravingContent() {
            return llmEngravingContent;
        }

        public void setLlmEngravingContent(String llmEngravingContent) {
            this.llmEngravingContent = llmEngravingContent;
        }

        public String getLlmIcon() {
            return llmIcon;
        }

        public void setLlmIcon(String llmIcon) {
            this.llmIcon = llmIcon;
        }

        public boolean isEngravingFanOutApplied() {
            return engravingFanOutApplied;
        }

        public void setEngravingFanOutApplied(boolean engravingFanOutApplied) {
            this.engravingFanOutApplied = engravingFanOutApplied;
        }

        public String getPersonalizationTextForLlm() {
            return personalizationTextForLlm;
        }

        public void setPersonalizationTextForLlm(String personalizationTextForLlm) {
            this.personalizationTextForLlm = personalizationTextForLlm;
        }

        public String getLlmSlotInstruction() {
            return llmSlotInstruction;
        }

        public void setLlmSlotInstruction(String llmSlotInstruction) {
            this.llmSlotInstruction = llmSlotInstruction;
        }

        public Integer getSourceBlockIndex() {
            return sourceBlockIndex;
        }

        public void setSourceBlockIndex(Integer sourceBlockIndex) {
            this.sourceBlockIndex = sourceBlockIndex;
        }

        public String getOrderNumber() {
            return orderNumber;
        }

        public void setOrderNumber(String orderNumber) {
            this.orderNumber = orderNumber;
        }

        public String getListingId() {
            return listingId;
        }

        public void setListingId(String listingId) {
            this.listingId = listingId;
        }
    }

}