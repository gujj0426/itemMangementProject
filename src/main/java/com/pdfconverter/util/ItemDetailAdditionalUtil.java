package com.pdfconverter.util;

import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.model.ProductAttribute;

/**
 * ItemDetail 组装工具类
 * 用于将 ProductAttribute 中的属性补充到 ItemDetail 中
 */
public class ItemDetailAdditionalUtil {

    /**
     * 组装 ItemDetail，使用 ProductAttribute 补充属性信息
     * 从 ProductAttribute 中提取型号、颜色、产品变量、设计风格、字体等信息，补充到 ItemDetail 中
     *
     * @param productAttribute 产品属性对象，包含已提取的属性
     * @param itemDetail 需要补充属性的商品详情对象
     * @return 补充属性后的 ItemDetail 对象
     */
    public static ItemDetail additionalDetail(ProductAttribute productAttribute, ItemDetail itemDetail) {
        if (productAttribute == null || itemDetail == null) {
            return itemDetail;
        }
        // 补充型号（如果 productAttribute 中有且 itemDetail 中为 UNKNOWN）
        if (productAttribute.getSize() != null
                && productAttribute.getSize() != ProductSize.UNKNOWN
                && itemDetail.getProductSize() == ProductSize.UNKNOWN) {
            itemDetail.setProductSize(productAttribute.getSize());
        }

        // 补充颜色（如果 productAttribute 中有且 itemDetail 中为 UNKNOWN）
        if (productAttribute.getColor() != null
                && productAttribute.getColor() != ProductColor.UNKNOWN
                && itemDetail.getProductColor() == ProductColor.UNKNOWN) {
            itemDetail.setProductColor(productAttribute.getColor());
        }

        // 补充产品变量（优先使用 productAttribute 中的）
        if (productAttribute.getProductVariable() != null
                && !productAttribute.getProductVariable().isEmpty()
                && itemDetail.getProductVariable() == ProductVariable.UNKNOWN) {
            ProductVariable variable = ProductVariable.fromDisplayName(productAttribute.getProductVariable());
            if (variable != null && variable != ProductVariable.UNKNOWN) {
                itemDetail.setProductVariable(variable);
            }
        }

        // 补充设计风格
        if (productAttribute.getStyle() != null
                && !productAttribute.getStyle().isEmpty()
                && itemDetail.getStyle().isEmpty()) {
            itemDetail.setStyle(productAttribute.getStyle());
        }

        // 补充字体
        if (productAttribute.getFont() != null
                && !productAttribute.getFont().isEmpty()
                && itemDetail.getFont().isEmpty()) {
            itemDetail.setFont(productAttribute.getFont());
        }

        return itemDetail;
    }
}
