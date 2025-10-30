package com.pdfconverter.constant;

/**
 * Excel生成常量类
 */
public class ExcelConstant {
    // Excel表头（与样例对齐）
    public static final String[] EXCEL_HEADERS = {
            "日期", "序号", "用户名", "订单编号", "信息", "订单类型",
            "袖扣风格", "字体", "领带风格", "设计师", "尺寸", "颜色",
            "数量（袖扣单位：对；领带夹单位：个）", "包装盒", "盒数量"
    };
    // 表头对应字段索引
    public static final int DATE_INDEX = 0;
    public static final int SERIAL_NUMBER_INDEX = 1;
    public static final int USERNAME_INDEX = 2;
    public static final int ORDER_NUMBER_INDEX = 3;
    public static final int INFO_INDEX = 4;
    public static final int ORDER_TYPE_INDEX = 5;
    public static final int CUFFLINK_STYLE_INDEX = 6;
    public static final int FONT_INDEX = 7;
    public static final int TIE_STYLE_INDEX = 8;
    public static final int DESIGNER_INDEX = 9;
    public static final int SIZE_INDEX = 10;
    public static final int COLOR_INDEX = 11;
    public static final int QUANTITY_INDEX = 12;
    public static final int PACKAGING_BOX_INDEX = 13;
    public static final int BOX_QUANTITY_INDEX = 14;
    // 默认值
    public static final String DEFAULT_EMPTY = "—";
    public static final String DEFAULT_FONT = "无";
    public static final String DEFAULT_TIE_STYLE = "—";
}