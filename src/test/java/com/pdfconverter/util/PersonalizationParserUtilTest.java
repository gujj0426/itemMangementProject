package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class PersonalizationParserUtilTest {

    @Test
    void extractLastFont_prefersTrailingNumber() {
        assertEquals("Font 38", PersonalizationParserUtil.extractLastFont("Front TS Font 33 Back Forever Font 38"));
    }

    @Test
    void extractLastFont_singleMatchSameAsExtractFont() {
        String s = "Forever, Font 28";
        assertEquals(PersonalizationParserUtil.extractFont(s), PersonalizationParserUtil.extractLastFont(s));
    }

    @Test
    void extractLastFont_emptyReturnsNull() {
        assertNull(PersonalizationParserUtil.extractLastFont(""));
        assertNull(PersonalizationParserUtil.extractLastFont("   "));
    }
}
