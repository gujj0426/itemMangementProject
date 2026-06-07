package com.pdfconverter.service.llm;

import com.pdfconverter.model.PdfOrderData.ItemDetail;

/**
 * DeepSeek 调用上下文，用于日志关联订单号、商品块与调用目的。
 */
public final class LlmCallContext {

    private final String orderNumber;
    private final String purpose;
    private final Integer sourceBlockIndex;
    private final String listingId;

    private LlmCallContext(String orderNumber, String purpose, Integer sourceBlockIndex, String listingId) {
        this.orderNumber = blankToDash(orderNumber);
        this.purpose = purpose != null && !purpose.isBlank() ? purpose.trim() : "unknown";
        this.sourceBlockIndex = sourceBlockIndex;
        this.listingId = listingId;
    }

    public static LlmCallContext from(ItemDetail item, String purpose) {
        if (item == null) {
            return new LlmCallContext(null, purpose, null, null);
        }
        return new LlmCallContext(
                item.getOrderNumber(),
                purpose,
                item.getSourceBlockIndex(),
                item.getListingId());
    }

    public String orderNumber() {
        return orderNumber;
    }

    public String purpose() {
        return purpose;
    }

    public Integer sourceBlockIndex() {
        return sourceBlockIndex;
    }

    public String listingId() {
        return listingId;
    }

    /** 单行摘要，用于 INFO/WARN。 */
    public String summary() {
        return "order=" + orderNumber
                + " purpose=" + purpose
                + " blockIdx=" + (sourceBlockIndex != null ? sourceBlockIndex : "-")
                + " listingId=" + (listingId != null && !listingId.isBlank() ? listingId : "-");
    }

    private static String blankToDash(String s) {
        return s == null || s.isBlank() ? "-" : s.trim();
    }
}
