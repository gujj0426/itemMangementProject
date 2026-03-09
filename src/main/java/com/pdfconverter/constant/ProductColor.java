package com.pdfconverter.constant;

/**
 * 商品颜色枚举
 * 定义所有支持的颜色（英文显示）
 */
public enum ProductColor {
    /**
     * 银色
     */
    SILVER("Silver", "银色"),

    /**
     * 金色
     */
    GOLD("Gold", "金色"),

    /**
     * 玫瑰金
     */
    ROSE_GOLD("Rose Gold", "玫瑰金"),

    /**
     * 黑色
     */
    BLACK("Black", "黑色"),

    /**
     * 灰色
     */
    GRAY("Gray", "灰色"),

    /**
     * 白色
     */
    WHITE("White", "白色"),

    /**
     * 黄色
     */
    YELLOW("Yellow", "黄色"),

    /**
     * 浅粉色
     */
    LIGHT_PINK("Light Pink", "浅粉色"),

    /**
     * 红色
     */
    RED("Red", "红色"),

    /**
     * 蓝色
     */
    BLUE("Blue", "蓝色"),

    /**
     * 霓虹粉
     */
    NEON_PINK("Hot Pink", "霓虹粉"),

    /**
     * 彩虹色
     */
    RAINBOW("Rainbow", "彩虹色"),

    /**
     * 紫色
     */
    PURPLE("Purple", "紫色"),

    /**
     * 未知颜色
     */
    UNKNOWN("Unknown", "");

    private final String colorCode;
    private final String displayName;

    ProductColor(String colorCode, String displayName) {
        this.colorCode = colorCode;
        this.displayName = displayName;
    }

    public String getColorCode() {
        return colorCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    /**
     * 根据颜色代码获取枚举值
     * @param colorCode 颜色代码（英文）
     * @return 对应的枚举值，如果未找到返回UNKNOWN
     */
    public static ProductColor fromColorCode(String colorCode) {
        if (colorCode == null || colorCode.trim().isEmpty()) {
            return UNKNOWN;
        }

        String upperCode = colorCode.trim();

        for (ProductColor color : ProductColor.values()) {
            if (color.colorCode.equalsIgnoreCase(upperCode)) {
                return color;
            }
        }

        return UNKNOWN;
    }

    /**
     * 从文本中提取颜色信息并转换为枚举
     * @param text 包含颜色信息的文本
     * @return 提取的颜色枚举
     */
    public static ProductColor extractFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return UNKNOWN;
        }

        String lowerText = text.toLowerCase();

        // 颜色关键词匹配
        if (lowerText.contains("rainbow")) return RAINBOW;
        if (lowerText.contains("neon") && lowerText.contains("pink")) return NEON_PINK;
        if (lowerText.contains("purple")) return PURPLE;
        if (lowerText.contains("blue")) return BLUE;
        if (lowerText.contains("red")) return RED;
        if (lowerText.contains("light") && lowerText.contains("pink")) return LIGHT_PINK;
        if (lowerText.contains("yellow")) return YELLOW;
        if (lowerText.contains("white")) return WHITE;
        if (lowerText.contains("gray")) return GRAY;
        if (lowerText.contains("black")) return BLACK;
        if (lowerText.contains("rose") && lowerText.contains("gold")) return ROSE_GOLD;
        if (lowerText.contains("gold")) return GOLD;
        if (lowerText.contains("silver")) return SILVER;

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return colorCode;
    }
}
