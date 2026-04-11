package com.pdfconverter.constant;

/**
 * 订单类型枚举
 * 定义所有支持的商品类型
 */
public enum OrderType {
    /**
     * 旧款袖扣
     */
    CUFFLINK("Cufflinks","袖扣"),

    /**
     * 领带夹
     */
    TIE_CLIP("Tie Clip","领带夹"),

    /**
     * 包装盒（通用）
     * 具体包装盒类型（椭圆形、方形、长方形、小方形）由产品变量区分
     */
    BOX("Box","包装盒"),

    /**
     * 狗牌
     */
    DOG_TAG("Dog Tag","狗牌"),

    /**
     * 狗牌配件（eg硅胶绑带）
     */
    DOG_TAG_HOLDER("Dog Tag Holder","狗牌配件"),

    /**
     * Pendant eg：FLOWER_HEART_BOX("花卉心形相盒吊坠")
     */
    PENDANT("Pendant","吊坠"),

    /**
     * Cable Chain   基础链  BASE_CHAIN("基础链")
     */
    CABLE_CHAIN("Cable Chain","基础链"),
    /**
     * Cufflinks and Tie Clip 组合商品
     */
    CUFFLINK_AND_TIE_CLIP("Cufflinks and Tie Clip","袖扣和领带夹"),
    /**
     * 生日石 add-on
     */
    ADDON_BIRTHSTONE("Addon Birthstone", "生日石 add-on"),
    /**
     * 翅膀 add-on
     */
    ADDON_WINGS("Addon Wings", "翅膀 add-on"),
    /**
     * 纪念饰品（骨灰罐）
     */
    MEMORIAL("Memorial", "纪念饰品"),
    /**
     * 未知商品类型
     */
    UNKNOWN("", "未知商品");

    private final String orderTypeCode;
    private final String displayName;

    OrderType(String orderTypeCode, String displayName) {
        this.orderTypeCode = orderTypeCode;
        this.displayName = displayName;
    }

    /**
     * 获取订单类型代码
     * @return 订单类型代码
     */
    public String getOrderTypeCode() {
        return orderTypeCode;
    }

    /**
     * 获取显示名称
     * @return 显示名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 获取名称代码（与 getOrderTypeCode 相同，用于统一命名规范）
     * @return 名称代码
     */
    public String getNamecode() {
        return orderTypeCode;
    }

    /**
     * 根据显示名称获取枚举值
     * @param orderTypeCode 产品类型代码
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static OrderType fromOrderTypeCode(String orderTypeCode) {
        if (orderTypeCode == null || orderTypeCode.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (OrderType type : OrderType.values()) {
            if (type.orderTypeCode.equals(orderTypeCode) ||
                type.name().equalsIgnoreCase(orderTypeCode) ||
                type.name().replace("_", " ").equalsIgnoreCase(orderTypeCode)) {
                return type;
            }
        }

        // 尝试模糊匹配
        String lowerOrderTypeCode = orderTypeCode.toLowerCase();
        for (OrderType type : OrderType.values()) {
            if (type.orderTypeCode.toLowerCase().contains(lowerOrderTypeCode) ||
                    lowerOrderTypeCode.contains(type.orderTypeCode.toLowerCase())) {
                return type;
            }
        }

        // 特殊处理：如果是box相关，统一返回BOX
        if (lowerOrderTypeCode.contains("box")) {
            return BOX;
        }

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
