package com.pdfconverter.constant;

/**
 * 商品尺寸枚举
 * 定义所有支持的尺寸规格
 */
public enum ProductSize {
    /**
     * 小号
     */
    S("S", "小号"),

    /**
     * 中号
     */
    M("M", "中号"),

    /**
     * 大号
     */
    L("L", "大号"),

    /**
     * 特大号
     */
    XL("XL", "特大号"),

    /**
     * 12毫米
     */
    MM_12("12mm", "12毫米"),

    /**
     * 15毫米
     */
    MM_15("15mm", "15毫米"),

    /**
     * 18毫米
     */
    MM_18("18mm", "18毫米"),

    /**
     * 20毫米
     */
    MM_20("20mm", "20毫米"),

    /**
     * 25毫米
     */
    MM_25("25mm", "25毫米"),

    /**
     * 30毫米
     */
    MM_30("30mm", "30毫米"),

    /**
     * 未知尺寸
     */
    UNKNOWN("UNKNOWN", "");

    private final String sizeCode;
    private final String displayName;

    ProductSize(String sizeCode, String displayName) {
        this.sizeCode = sizeCode;
        this.displayName = displayName;
    }

    public String getSizeCode() {
        return sizeCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据尺寸代码获取枚举值
     * @param sizeCode 尺寸代码（如"S"、"L"、"12mm"等）
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static ProductSize fromSizeCode(String sizeCode) {
        if (sizeCode == null || sizeCode.trim().isEmpty()) {
            return UNKNOWN;
        }

        String upperCode = sizeCode.toUpperCase().trim();

        for (ProductSize size : ProductSize.values()) {
            if (size.sizeCode.equalsIgnoreCase(upperCode)) {
                return size;
            }
        }

        return UNKNOWN;
    }

    /**
     * 从字符串中提取尺寸信息并转换为枚举
     * @param text 包含尺寸信息的文本
     * @return 提取的尺寸枚举
     */
    public static ProductSize extractFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return UNKNOWN;
        }

        String upperText = text.toUpperCase();

        // 检查毫米规格
        if (upperText.contains("30MM")) return MM_30;
        if (upperText.contains("25MM")) return MM_25;
        if (upperText.contains("20MM")) return MM_20;
        if (upperText.contains("18MM")) return MM_18;
        if (upperText.contains("15MM")) return MM_15;
        if (upperText.contains("12MM")) return MM_12;

        // 检查字母规格
        if (upperText.contains("XL")) return XL;
        if (upperText.contains("L")) return L;
        if (upperText.contains("M")) return M;
        if (upperText.contains("S")) return S;

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return sizeCode;
    }
}
