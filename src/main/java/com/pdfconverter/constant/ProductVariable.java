package com.pdfconverter.constant;

/**
 * 产品变量枚举
 * 定义所有支持的产品变量
 */
public enum ProductVariable {
    /**
     * 月份系列
     */
    MONTH_JANUARY("一月"),
    MONTH_FEBRUARY("二月"),
    MONTH_MARCH("三月"),
    MONTH_APRIL("四月"),
    MONTH_MAY("五月"),
    MONTH_JUNE("六月"),
    MONTH_JULY("七月"),
    MONTH_AUGUST("八月"),
    MONTH_SEPTEMBER("九月"),
    MONTH_OCTOBER("十月"),
    MONTH_NOVEMBER("十一月"),
    MONTH_DECEMBER("十二月"),

    /**
     * 孔型系列
     */
    HOLE_HORIZONTAL("横孔"),
    HOLE_VERTICAL("竖孔"),

    /**
     * 工艺系列
     */
    MOLD_OPEN("开模"),
    CUT("切割"),

    /**
     * 形状系列
     */
    SHAPE_ROUND("圆形"),

    /**
     * 链条系列
     */
    CHAIN_FULL("全链"),
    CHAIN_HALF("半链"),
    RING_SHINY("光圈"),
    RING_FLAT("平圈"),

    /**
     * 数字系列
     */
    DIGIT_0("数字0"),
    DIGIT_1("数字1"),
    DIGIT_2("数字2"),
    DIGIT_3("数字3"),
    DIGIT_4("数字4"),
    DIGIT_5("数字5"),
    DIGIT_6("数字6"),
    DIGIT_7("数字7"),
    DIGIT_8("数字8"),
    DIGIT_9("数字9"),

    /**
     * 包装盒系列
     */
    BOX_SMALL_SQUARE("小方形礼盒"),
    BOX_RECTANGLE("长方形礼盒"),
    BOX_LARGE_SQUARE("大方形礼盒"),
    WOOD_BOX_SQUARE_WOOD("Square Box-方形木盒"),
    WOOD_BOX_SQUARE_WINDOW("方形开窗木盒"),
    WOOD_BOX_RECTANGLE_WOOD("Box-长方形木盒"),
    WOOD_BOX_RECTANGLE_WINDOW("长方形开窗木盒"),
    WOOD_BOX_OVAL_WINDOW("Oval Box-椭圆形开窗木盒"),

    // 狗牌绑带
    SILICONE_BAND("硅胶绑带"),

    /**
     * 猫耳系列
     */
    CAT_EAR_ROUND("圆耳猫"),
    CAT_EAR_POINTED("尖耳猫"),
    CAT_EAR_FOLDED("翘耳猫"),
    CAT_PATTERN_BLACK_WHITE("黑白花纹猫"),
    CAT_HEAD_LARGE("大猫头"),
    CAT_PATTERN("花纹猫"),

    /**
     * 材质系列
     */
    MATERIAL_ALUMINUM_ALLOY("铝合金"),
    MATERIAL_STAINLESS_STEEL("不锈钢"),

    /**
     * 未知产品变量
     */
    UNKNOWN("");

    private final String displayName;

    ProductVariable(String displayName) {
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
    public static ProductVariable fromDisplayName(String displayName) {
        if (displayName == null || displayName.trim().isEmpty()) {
            return UNKNOWN;
        }

        for (ProductVariable variable : ProductVariable.values()) {
            if (variable.displayName.equals(displayName) ||
                variable.name().equalsIgnoreCase(displayName) ||
                variable.name().replace("_", " ").equalsIgnoreCase(displayName)) {
                return variable;
            }
        }

        // 尝试模糊匹配
        String lowerDisplayName = displayName.toLowerCase();
        for (ProductVariable variable : ProductVariable.values()) {
            if (variable.displayName.toLowerCase().equals(lowerDisplayName)) {
                return variable;
            }
        }

        return UNKNOWN;
    }

    /**
     * 从文本中提取产品变量
     * @param text 包含产品变量的文本
     * @return 提取的产品变量枚举
     */
    public static ProductVariable extractFromText(String text) {
        if (text == null || text.trim().isEmpty()) {
            return UNKNOWN;
        }

        String lowerText = text.toLowerCase();

        // 月份匹配（中文月份名）
        if (lowerText.contains("一月") || lowerText.contains("january")) return MONTH_JANUARY;
        if (lowerText.contains("二月") || lowerText.contains("february")) return MONTH_FEBRUARY;
        if (lowerText.contains("三月") || lowerText.contains("march")) return MONTH_MARCH;
        if (lowerText.contains("四月") || lowerText.contains("april")) return MONTH_APRIL;
        if (lowerText.contains("五月") || lowerText.contains("may")) return MONTH_MAY;
        if (lowerText.contains("六月") || lowerText.contains("june")) return MONTH_JUNE;
        if (lowerText.contains("七月") || lowerText.contains("july")) return MONTH_JULY;
        if (lowerText.contains("八月") || lowerText.contains("august")) return MONTH_AUGUST;
        if (lowerText.contains("九月") || lowerText.contains("september")) return MONTH_SEPTEMBER;
        if (lowerText.contains("十月") || lowerText.contains("october")) return MONTH_OCTOBER;
        if (lowerText.contains("十一月") || lowerText.contains("november")) return MONTH_NOVEMBER;
        if (lowerText.contains("十二月") || lowerText.contains("december")) return MONTH_DECEMBER;

        // 数字月份匹配（如 "10月"、"1月"、"5月"）
        java.util.regex.Matcher monthMatcher = java.util.regex.Pattern.compile("(\\d+)\\s*月").matcher(text);
        if (monthMatcher.find()) {
            int month = Integer.parseInt(monthMatcher.group(1));
            switch (month) {
                case 1: return MONTH_JANUARY;
                case 2: return MONTH_FEBRUARY;
                case 3: return MONTH_MARCH;
                case 4: return MONTH_APRIL;
                case 5: return MONTH_MAY;
                case 6: return MONTH_JUNE;
                case 7: return MONTH_JULY;
                case 8: return MONTH_AUGUST;
                case 9: return MONTH_SEPTEMBER;
                case 10: return MONTH_OCTOBER;
                case 11: return MONTH_NOVEMBER;
                case 12: return MONTH_DECEMBER;
            }
        }

        // 孔型匹配
        if (lowerText.contains("横孔")) return HOLE_HORIZONTAL;
        if (lowerText.contains("竖孔")) return HOLE_VERTICAL;

        // 工艺匹配
        if (lowerText.contains("开模")) return MOLD_OPEN;
        if (lowerText.contains("切割")) return CUT;

        // 形状匹配
        if (lowerText.contains("圆形")) return SHAPE_ROUND;

        // 链条匹配
        if (lowerText.contains("全链")) return CHAIN_FULL;
        if (lowerText.contains("半链")) return CHAIN_HALF;
        if (lowerText.contains("光圈")) return RING_SHINY;
        if (lowerText.contains("平圈")) return RING_FLAT;

        // 数字匹配
        if (lowerText.contains("数字0") || lowerText.contains("digit 0")) return DIGIT_0;
        if (lowerText.contains("数字1") || lowerText.contains("digit 1")) return DIGIT_1;
        if (lowerText.contains("数字2") || lowerText.contains("digit 2")) return DIGIT_2;
        if (lowerText.contains("数字3") || lowerText.contains("digit 3")) return DIGIT_3;
        if (lowerText.contains("数字4") || lowerText.contains("digit 4")) return DIGIT_4;
        if (lowerText.contains("数字5") || lowerText.contains("digit 5")) return DIGIT_5;
        if (lowerText.contains("数字6") || lowerText.contains("digit 6")) return DIGIT_6;
        if (lowerText.contains("数字7") || lowerText.contains("digit 7")) return DIGIT_7;
        if (lowerText.contains("数字8") || lowerText.contains("digit 8")) return DIGIT_8;
        if (lowerText.contains("数字9") || lowerText.contains("digit 9")) return DIGIT_9;

        // 包装盒匹配
        if (lowerText.contains("小方形礼盒")) return BOX_SMALL_SQUARE;
        if (lowerText.contains("长方形礼盒")) return BOX_RECTANGLE;
        if (lowerText.contains("大方形礼盒")) return BOX_LARGE_SQUARE;
        if (lowerText.contains("square box") || lowerText.contains("方形木盒")) return WOOD_BOX_SQUARE_WOOD;
        if (lowerText.contains("方形开窗木盒")) return WOOD_BOX_SQUARE_WINDOW;
        if (lowerText.contains("rectangle box") || lowerText.contains("长方形木盒")) return WOOD_BOX_RECTANGLE_WOOD;
        if (lowerText.contains("长方形开窗木盒")) return WOOD_BOX_RECTANGLE_WINDOW;
        if (lowerText.contains("oval box") || lowerText.contains("椭圆形开窗木盒")) return WOOD_BOX_OVAL_WINDOW;
        if (lowerText.contains("silicone band") || lowerText.contains("硅胶绑带") || lowerText.contains("dog tag holder")) return SILICONE_BAND;

        // 猫耳匹配
        if (lowerText.contains("圆耳猫")) return CAT_EAR_ROUND;
        if (lowerText.contains("尖耳猫")) return CAT_EAR_POINTED;
        if (lowerText.contains("翘耳猫")) return CAT_EAR_FOLDED;
        if (lowerText.contains("黑白花纹猫")) return CAT_PATTERN_BLACK_WHITE;
        if (lowerText.contains("大猫头")) return CAT_HEAD_LARGE;
        if (lowerText.contains("花纹猫")) return CAT_PATTERN;

        // 材质匹配
        if (lowerText.contains("铝合金")) return MATERIAL_ALUMINUM_ALLOY;
        if (lowerText.contains("不锈钢")) return MATERIAL_STAINLESS_STEEL;

        return UNKNOWN;
    }

    /**
     * 获取月份系列的所有枚举值
     */
    public static ProductVariable[] getMonthValues() {
        return new ProductVariable[]{
            MONTH_JANUARY, MONTH_FEBRUARY, MONTH_MARCH, MONTH_APRIL,
            MONTH_MAY, MONTH_JUNE, MONTH_JULY, MONTH_AUGUST,
            MONTH_SEPTEMBER, MONTH_OCTOBER, MONTH_NOVEMBER, MONTH_DECEMBER
        };
    }

    /**
     * 获取数字系列的所有枚举值
     */
    public static ProductVariable[] getDigitValues() {
        return new ProductVariable[]{
            DIGIT_0, DIGIT_1, DIGIT_2, DIGIT_3, DIGIT_4,
            DIGIT_5, DIGIT_6, DIGIT_7, DIGIT_8, DIGIT_9
        };
    }

    /**
     * 获取包装盒系列的所有枚举值
     */
    public static ProductVariable[] getBoxValues() {
        return new ProductVariable[]{WOOD_BOX_SQUARE_WOOD, WOOD_BOX_SQUARE_WINDOW, WOOD_BOX_RECTANGLE_WOOD,
                WOOD_BOX_RECTANGLE_WINDOW, WOOD_BOX_OVAL_WINDOW
        };
    }

    @Override
    public String toString() {
        return displayName;
    }
}
