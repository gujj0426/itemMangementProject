package com.pdfconverter.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExtractUtilListingAnchorsTest {

    private final ExtractUtil extractUtil = new ExtractUtil();

    @Test
    void getPersonalization_usesLastPersonalizationLabel_whenBlockContainsTwoListingsWorthOfFields() {
        String block = ""
                + "Line A title\n"
                + "Quantity: 1\n"
                + "Size and Color: Silver_S\n"
                + "Personalization: cufflink: Icon 40+ S15(BG)\n"
                + "tie clip: S4(JB)\n"
                + "Quantity: 1\n"
                + "Size and Color: Silver_S\n"
                + "Personalization: cufflink: Icon70 + S15(JL)\n"
                + "tie clip: S4(EJ)\n";

        assertEquals(
                "cufflink: Icon70 + S15(JL)\ntie clip: S4(EJ)",
                extractUtil.getPersonalization(block));

        ExtractUtil.ListingAnchors anchors = extractUtil.findLastListingAnchors(block);
        assertNotNull(anchors);
        assertEquals(1, extractUtil.extractLineAfter(block.substring(anchors.quantityLabelStart), "Quantity:"));

        int afterQty = anchors.quantityLabelStart + "Quantity:".length();
        String dynamic = block.substring(afterQty, anchors.personalizationLabelStart).trim();
        assertEquals("1\nSize and Color: Silver_S", dynamic);
    }
}
