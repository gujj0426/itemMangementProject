package com.pdfconverter.service;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.model.ProductAttribute;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 4061991479：Pet Portrait combo listing 的 PDF 使用 Color and Size: Gold_S，
 * 配置侧为 Size and Color，应解析为 S 码而非 Excel 兜底 L。
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class AttributeRuleEngineColorSizeLabelTest {

    @Autowired
    private AttributeRuleEngine attributeRuleEngine;

    @Test
    void cuffPetStainlessCombo_colorAndSizeLabel_extractsGoldSmall() {
        Map<String, String> dynamicAttrs = new LinkedHashMap<>();
        dynamicAttrs.put("Color and Size", "Gold_S");
        dynamicAttrs.put("Item Options", "Cufflinks+Oval Box");

        ProductAttribute attribute = attributeRuleEngine.extractAttributes(
                "cuff_pet_stainless_combo", "Cufflink", dynamicAttrs);

        assertNotNull(attribute);
        assertEquals(ProductSize.S, attribute.getSize());
        assertEquals(ProductColor.GOLD, attribute.getColor());
    }
}
