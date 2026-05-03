package com.pdfconverter.util;

import java.util.Locale;

/**
 * 业务规则：留言里出现 Style 且有刻录正文、但未指定字体编号时，默认字体为 {@link #DEFAULT_FONT_DISPLAY};
 * 若客户写明「默认字体」等，意图上也映射为同一字体。
 */
public final class StyleEngravingDefaultFontUtil {

    /** Excel / LLM 允许列表中的标准写法（与 font-name-mapping、canonical 列表一致） */
    public static final String DEFAULT_FONT_DISPLAY = "Font 3(no)";

    private StyleEngravingDefaultFontUtil() {
    }

    /**
     * @param mergedFontFromRulesAndLlm 规则引擎与 LLM 合并后的字体（非空则直接采用）
     * @param effectiveDesignStyle       最终生效的设计风格列取值
     * @param effectiveEngravingContent  最终生效的刻录信息（多为 LLM engravingContent）
     * @param personalization           客户 Personalization 全文
     * @param dynamicAttributes          订购属性区原文（与留言合并探测是否出现 Font n）
     * @param llmPersonalizationEnabled  false 时不应用本默认（无 LLM 刻录列时通常无刻录正文）
     */
    public static String applyIfEligible(String mergedFontFromRulesAndLlm,
                                         String effectiveDesignStyle,
                                         String effectiveEngravingContent,
                                         String personalization,
                                         String dynamicAttributes,
                                         boolean llmPersonalizationEnabled) {
        String merged = mergedFontFromRulesAndLlm != null ? mergedFontFromRulesAndLlm.trim() : "";
        if (!merged.isEmpty()) {
            return merged;
        }
        if (!llmPersonalizationEnabled) {
            return "";
        }
        String probe = concatProbeText(personalization, dynamicAttributes);
        if (PersonalizationParserUtil.extractFont(probe) != null) {
            return "";
        }
        if (PersonalizationParserUtil.mentionsDefaultFont(probe)) {
            return DEFAULT_FONT_DISPLAY;
        }
        String style = effectiveDesignStyle != null ? effectiveDesignStyle : "";
        if (!containsStyleKeyword(style)) {
            return "";
        }
        String eng = effectiveEngravingContent != null ? effectiveEngravingContent.trim() : "";
        if (eng.isEmpty()) {
            return "";
        }
        return DEFAULT_FONT_DISPLAY;
    }

    private static boolean containsStyleKeyword(String style) {
        return style.toLowerCase(Locale.ROOT).contains("style");
    }

    private static String concatProbeText(String personalization, String dynamicAttributes) {
        StringBuilder sb = new StringBuilder();
        if (personalization != null && !personalization.isBlank()) {
            sb.append(personalization.trim());
        }
        if (dynamicAttributes != null && !dynamicAttributes.isBlank()) {
            if (sb.length() > 0) {
                sb.append('\n');
            }
            sb.append(dynamicAttributes.trim());
        }
        return sb.toString();
    }
}
