package com.pdfconverter.constant;

/**
 * Excel生成常量类
 */
public class ExcelConstant {
    // Excel表头（与样例对齐）
    public static final String[] EXCEL_HEADERS = {
            "产品编号", "用户名", "订单编号", "产品名称", "型号", "颜色",
            "产品变量", "设计风格", "刻录信息", "字体", "icon", "是否派单",
            "设计师", "数量", "出库日期", "Personalization", "订购完全信息", "商品标题"
    };
    // 表头对应字段索引
    public static final int PRODUCT_CODE_INDEX = 0;
    public static final int USERNAME_INDEX = 1;
    public static final int ORDER_NUMBER_INDEX = 2;
    public static final int PRODUCT_NAME_INDEX = 3;
    public static final int MODEL_INDEX = 4;
    public static final int COLOR_INDEX = 5;
    public static final int PRODUCT_VARIABLE_INDEX = 6;
    public static final int DESIGN_STYLE_INDEX = 7;
    public static final int ENGRAVING_INFO_INDEX = 8;
    public static final int FONT_INDEX = 9;
    public static final int ICON_INDEX = 10;
    public static final int IS_ASSIGNED_INDEX = 11;
    public static final int DESIGNER_INDEX = 12;
    public static final int QUANTITY_INDEX = 13;
    public static final int OUTPUT_DATE_INDEX = 14;
    public static final int PERSONALIZATION_INDEX = 15;
    public static final int FULL_ORDER_INFO_INDEX = 16;
    public static final int ITEM_TITLE_INDEX = 17;
    // 默认值
    public static final String DEFAULT_EMPTY = "—";
    public static final String DEFAULT_FONT = "无";
    public static final String DEFAULT_TIE_STYLE = "—";
}