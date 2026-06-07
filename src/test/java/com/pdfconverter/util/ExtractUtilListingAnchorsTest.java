package com.pdfconverter.util;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ExtractUtilListingAnchorsTest {

    private final ExtractUtil extractUtil = new ExtractUtil();

    @BeforeEach
    void setUp() {
        extractUtil.useDefaultAnchorsForTest();
    }

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

    @Test
    void getPersonalization_extractsTieClipEngraving_whenNoPersonalizationLabel() {
        String block = ""
                + "Lyndsey S (lyndseyspano) Custom Engraved Tie Clip with Woodbox- 2 Sides\n"
                + "Handwriting Tie clip-Father of the Groom Gift\n"
                + "Quantity: 1\n"
                + "Color Finish: Gold\n"
                + "Engraving Sides: Front & Back\n"
                + "TieClip Engraving: Enter custom details below: Front: JRS\n"
                + "back: Brother of the Bride 07.11.26\n"
                + "font: (S35)";

        assertEquals(
                "Front: JRS\nback: Brother of the Bride 07.11.26\nfont: (S35)",
                extractUtil.getPersonalization(block));

        ExtractUtil.ListingAnchors anchors = extractUtil.findLastListingAnchors(block);
        assertNotNull(anchors);
        int afterQty = anchors.quantityLabelStart + "Quantity:".length();
        String dynamic = block.substring(afterQty, anchors.personalizationLabelStart).trim();
        assertEquals("1\nColor Finish: Gold\nEngraving Sides: Front & Back", dynamic);
    }
}
