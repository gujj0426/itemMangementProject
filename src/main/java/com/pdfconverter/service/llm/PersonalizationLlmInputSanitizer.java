package com.pdfconverter.service.llm;

/**
 * LLM 调用前输入整理：保证每条 {@link com.pdfconverter.model.PdfOrderData.ItemDetail} 独立送入模型，避免重复与串台。
 */
public final class PersonalizationLlmInputSanitizer {

    private PersonalizationLlmInputSanitizer() {
    }

    /**
     * 写入 user prompt 顶部，约束模型勿跨商品推理。
     */
    public static String singleItemScopeBanner() {
        return """
                【数据范围（务必遵守）】
                本次请求仅包含「同一订单里的单条导出商品行」数据：下列 Personalization / 订购属性均只对应这一条商品在 PDF 里的区块。
                应用程序按商品顺序依次调用：请勿引用、猜测或合并本订单其它商品行的留言；其它行的内容不在上下文中。
                若当前片段缺少某字段所需信息，该项填空字符串即可。
                """;
    }

    /**
     * {@code dynamicAttributes} 常由 buildFullOrderInfo 在末尾拼上与 {@code personalization} 相同正文，送入模型会重复；
     * 去掉末尾与 personalization 完全一致的一段，仅保留属性区供模型参考。
     */
    public static String dynamicAttributesWithoutTrailingPersonalization(String dynamicAttributes, String personalization) {
        if (dynamicAttributes == null || dynamicAttributes.isBlank()) {
            return "";
        }
        if (personalization == null || personalization.isBlank()) {
            return dynamicAttributes.trim();
        }
        String d = dynamicAttributes.trim();
        String p = personalization.trim();
        if (d.endsWith(p)) {
            d = d.substring(0, d.length() - p.length()).trim();
            while (d.endsWith("\n")) {
                d = d.substring(0, d.length() - 1).trim();
            }
            return d;
        }
        return dynamicAttributes;
    }
}
