package com.pdfconverter.constant;

/**
 * 订单类型枚举
 * 定义所有支持的商品类型
 */
public enum OrderType {
    /**
     * 旧款袖扣
     */
    CUFFLINK("袖扣"),

    /**
     * 领带夹
     */
    TIE_CLIP("领带夹"),

    /**
     * 包装盒（通用）
     * 具体包装盒类型（椭圆形、方形、长方形、小方形）由产品变量区分
     */
    BOX("包装盒"),

    /**
     * 狗牌
     */
    DOG_TAG("狗牌"),

    /**
     * 狗牌配件（硅胶绑带）
     */
    DOG_TAG_HOLDER("硅胶绑带"),

    /**
     * Pendant FLOWER_HEART_BOX("花卉心形相盒吊坠")
     */
    PENDANT("吊坠"),

    /**
     * Cable Chain   基础链  BASE_CHAIN("基础链")
     */
    CABLE_CHAIN("项链"),

    /**
     * 未知商品类型
     */
    UNKNOWN("");

    private final String displayName;

    OrderType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据显示名称获取枚举值
     * @param displayName 显示名称
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static OrderType fromDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (OrderType type : OrderType.values()) {
            if (type.displayName.equals(displayName) ||
                type.name().equalsIgnoreCase(displayName) ||
                type.name().replace("_", " ").equalsIgnoreCase(displayName)) {
                return type;
            }
        }

        // 尝试模糊匹配
        String lowerDisplayName = displayName.toLowerCase();
        for (OrderType type : OrderType.values()) {
            if (type.displayName.toLowerCase().contains(lowerDisplayName) ||
                lowerDisplayName.contains(type.displayName.toLowerCase())) {
                return type;
            }
        }

        // 特殊处理：如果是box相关，统一返回BOX
        if (lowerDisplayName.contains("box") || lowerDisplayName.contains("盒")) {
            return BOX;
        }

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
