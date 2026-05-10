package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EngravingRowFanOutProcessorTest {

    private final EngravingRowFanOutProcessor processor = new EngravingRowFanOutProcessor();

    @Test
    void tieClipDoubleSide_splitsTwoRowsWithSlotInstructions() {
        ItemDetail item = new ItemDetail();
        item.setOrderType(OrderType.TIE_CLIP);
        item.setMainProductFlg(true);
        item.setItemQuantity(1);
        item.setProductName(ProductName.TIE_CLIP_DUCK_BILL_THICK);
        item.setPersonalization("Front: Style 5\nBack: Font 10");
        item.setDynamicAttributes("Engraving Sides: Front & Back\nPersonalization:\nFront: Style 5\nBack: Font 10");

        List<ItemDetail> rows = processor.expandOrderLines(List.of(item));
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isEngravingFanOutApplied());
        assertTrue(rows.get(1).isEngravingFanOutApplied());
        assertEquals(1, rows.get(0).getItemQuantity());
        assertEquals(0, rows.get(1).getItemQuantity());
        assertNotNull(rows.get(0).getLlmSlotInstruction());
        assertEquals("Style 5", rows.get(0).getPersonalizationTextForLlm().trim());
        assertEquals("Font 10", rows.get(1).getPersonalizationTextForLlm().trim());
    }

    @Test
    void cufflinkDualIntent_splitsTwoRows() {
        ItemDetail item = new ItemDetail();
        item.setOrderType(OrderType.CUFFLINK);
        item.setMainProductFlg(true);
        item.setItemQuantity(1);
        item.setPersonalization("First cufflink: A\nSecond cufflink: B");

        List<ItemDetail> rows = processor.expandOrderLines(List.of(item));
        assertEquals(2, rows.size());
        assertEquals(1, rows.get(0).getItemQuantity());
        assertEquals(0, rows.get(1).getItemQuantity());
    }

    @Test
    void comboMentionsBoth_narrowsExcerptWithoutExtraRows() {
        ItemDetail tie = new ItemDetail();
        tie.setOrderType(OrderType.TIE_CLIP);
        tie.setMainProductFlg(false);
        tie.setItemQuantity(1);
        tie.setPersonalization("cufflink: Icon 40\ntie clip: S4");

        List<ItemDetail> rows = processor.expandOrderLines(List.of(tie));
        assertEquals(1, rows.size());
        assertTrue(rows.get(0).isEngravingFanOutApplied());
        assertTrue(rows.get(0).getPersonalizationTextForLlm().toLowerCase().contains("tie"));
    }

    @Test
    void nonEngravableType_passThrough() {
        ItemDetail box = new ItemDetail();
        box.setOrderType(OrderType.BOX);
        box.setPersonalization("");
        assertEquals(1, processor.expandOrderLines(List.of(box)).size());
    }

    @Test
    void pendantRoundDiscAndBar_splitsTwoRows() {
        ItemDetail item = new ItemDetail();
        item.setOrderType(OrderType.PENDANT);
        item.setMainProductFlg(true);
        item.setItemQuantity(1);
        item.setListingId("pendant_round_bar");
        item.setPersonalization("Round Disc: Photo\nBar: Forever your little girl, Font 28");
        item.setDynamicAttributes("Engraving Sides: Double-Side\n"
                + "Personalization:\n"
                + "Round Disc: Photo\nBar: Forever your little girl, Font 28");

        List<ItemDetail> rows = processor.expandOrderLines(List.of(item));
        assertEquals(2, rows.size());
        assertTrue(rows.get(0).isEngravingFanOutApplied());
        assertTrue(rows.get(1).isEngravingFanOutApplied());
        assertEquals("Photo", rows.get(0).getPersonalizationTextForLlm().trim());
        assertTrue(rows.get(1).getPersonalizationTextForLlm().contains("Forever your little girl"));
    }

    @Test
    void comboPers_skipsTieClipMultiFaceEvenWhenDynamicSaysFrontBack() {
        ItemDetail tie = new ItemDetail();
        tie.setOrderType(OrderType.TIE_CLIP);
        tie.setMainProductFlg(false);
        tie.setItemQuantity(1);
        tie.setPersonalization("cufflink: Icon 40\nTie clip: S4(JB)");
        tie.setDynamicAttributes("Engraving Sides: Front & Back\nPersonalization:\ncufflink: Icon 40");

        List<ItemDetail> rows = processor.expandOrderLines(List.of(tie));
        assertEquals(1, rows.size(), "组合订单不应按双面领带夹拆行");
        assertTrue(rows.get(0).getPersonalizationTextForLlm().toLowerCase().contains("tie"));
    }
}
