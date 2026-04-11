package com.pdfconverter.model;

import com.pdfconverter.constant.OrderType;
import lombok.Data;

@Data
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
    private OrderType orderType;
    /** 商品名称 **/
    private String productName;
    /** 字体 */
    private String font;
    /** 设计师 */
    private String designer;
    /** 尺寸 */
    private String productSize;
    /** 颜色 */
    private String productColor;
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
}