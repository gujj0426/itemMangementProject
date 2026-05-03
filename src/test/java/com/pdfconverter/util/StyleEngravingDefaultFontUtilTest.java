package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StyleEngravingDefaultFontUtilTest {

    @Test
    void returnsMergedFontWhenAlreadySet() {
        assertEquals("Font 10(no)",
                StyleEngravingDefaultFontUtil.applyIfEligible("Font 10(no)", "Style 15", "BG",
                        "cufflink: S15(BG)", "", true));
    }

    @Test
    void appliesFont3WhenStyleAndEngravingAndNoFontInProbe() {
        assertEquals(StyleEngravingDefaultFontUtil.DEFAULT_FONT_DISPLAY,
                StyleEngravingDefaultFontUtil.applyIfEligible("", "Style 15", "BG",
                        "cufflink: Icon 40+ S15(BG)\ntie clip: S4(JB)",
                        "Size and Color: Silver_S", true));
    }

    @Test
    void appliesFont3WhenMentionsDefaultFontEvenWithoutEngravingInProbe() {
        assertEquals(StyleEngravingDefaultFontUtil.DEFAULT_FONT_DISPLAY,
                StyleEngravingDefaultFontUtil.applyIfEligible("", "Style 5", "",
                        "默认字体，刻 ABC", "", true));
    }

    @Test
    void skipsWhenBuyerSpecifiedFontNumber() {
        assertEquals("",
                StyleEngravingDefaultFontUtil.applyIfEligible("", "Style 48", "Big Brother",
                        "S48-font 38: Big Brother", "", true));
    }

    @Test
    void skipsWhenLlmDisabled() {
        assertEquals("",
                StyleEngravingDefaultFontUtil.applyIfEligible("", "Style 15", "BG",
                        "cufflink: S15(BG)", "", false));
    }
}
