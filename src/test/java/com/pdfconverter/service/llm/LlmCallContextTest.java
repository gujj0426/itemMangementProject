package com.pdfconverter.service.llm;

import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmCallContextTest {

    @Test
    void fromItem_includesOrderAndBlock() {
        ItemDetail d = new ItemDetail();
        d.setOrderNumber("4052251679");
        d.setSourceBlockIndex(2);
        d.setListingId("tie_clip_2sides");

        LlmCallContext ctx = LlmCallContext.from(d, "intent-extract");
        assertEquals("4052251679", ctx.orderNumber());
        assertEquals("intent-extract", ctx.purpose());
        assertEquals(2, ctx.sourceBlockIndex());
        assertEquals("tie_clip_2sides", ctx.listingId());
        assertTrue(ctx.summary().contains("order=4052251679"));
        assertTrue(ctx.summary().contains("blockIdx=2"));
    }

    @Test
    void fromNullItem_usesDash() {
        LlmCallContext ctx = LlmCallContext.from(null, "routing");
        assertEquals("-", ctx.orderNumber());
        assertTrue(ctx.summary().contains("order=-"));
    }
}
