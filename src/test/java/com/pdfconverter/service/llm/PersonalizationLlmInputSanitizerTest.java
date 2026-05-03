package com.pdfconverter.service.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PersonalizationLlmInputSanitizerTest {

    @Test
    void stripsTrailingPersonalizationDuplicate() {
        String pers = "cufflink: A\ntie clip: B";
        String dyn = "Color: Silver\n" + pers;
        assertEquals("Color: Silver",
                PersonalizationLlmInputSanitizer.dynamicAttributesWithoutTrailingPersonalization(dyn, pers));
    }

    @Test
    void bannerNonBlank() {
        assertFalse(PersonalizationLlmInputSanitizer.singleItemScopeBanner().isBlank());
        assertTrue(PersonalizationLlmInputSanitizer.singleItemScopeBanner().contains("单条"));
    }
}
