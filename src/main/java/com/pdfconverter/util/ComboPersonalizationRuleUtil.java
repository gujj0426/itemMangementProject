package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 袖扣+领带夹组合留言的确定性解析（不依赖 LLM），覆盖 PDF 断行后的常见句式。
 */
public final class ComboPersonalizationRuleUtil {

    private static final Pattern TIE_CLIP_LM_FONT = Pattern.compile(
            "(?is)(?:#\\s*(\\d+)\\s*font|font\\s*#?\\s*(\\d+))[^\\n]{0,80}?\\b([A-Za-z]{1,6})\\b\\s+for\\s+tie\\s*clip");
    private static final Pattern TIE_CLIP_PLAIN_LM = Pattern.compile(
            "(?is)\\b([A-Za-z]{1,6})\\b\\s+for\\s+tie\\s*clip");
    private static final Pattern CUFFLINK_PICTURE = Pattern.compile(
            "(?is)picture|photo|附图|头像");

    private ComboPersonalizationRuleUtil() {
    }

    /**
     * @return true 表示已写入 {@code llm*}，调用方可跳过 DeepSeek
     */
    public static boolean tryApplyRuleBasedIntent(ItemDetail line) {
        if (line == null || !line.isEngravingFanOutApplied()) {
            return false;
        }
        String excerpt = line.getPersonalizationTextForLlm();
        if (excerpt == null || excerpt.isBlank()) {
            excerpt = PersonalizationSlotSplitter.normalizePersonalizationLineBreaks(
                    line.getPersonalization() != null ? line.getPersonalization() : "");
        } else {
            excerpt = PersonalizationSlotSplitter.normalizePersonalizationLineBreaks(excerpt);
        }
        if (excerpt.isBlank()) {
            return false;
        }
        OrderType ot = line.getOrderType();
        if (ot == OrderType.TIE_CLIP) {
            return applyTieClipRule(line, excerpt);
        }
        if (ot == OrderType.CUFFLINK) {
            return applyCufflinkRule(line, excerpt);
        }
        return false;
    }

    private static boolean applyTieClipRule(ItemDetail line, String excerpt) {
        Matcher m = TIE_CLIP_LM_FONT.matcher(excerpt);
        if (m.find()) {
            String fontNum = m.group(1) != null ? m.group(1) : m.group(2);
            String eng = m.group(3).trim();
            line.setLlmDesignStyle("");
            line.setLlmFont(mapFontNumber(fontNum));
            line.setLlmIcon("");
            line.setLlmEngravingContent(eng);
            return true;
        }
        Matcher plain = TIE_CLIP_PLAIN_LM.matcher(excerpt);
        if (plain.find()) {
            line.setLlmDesignStyle("");
            line.setLlmFont("");
            line.setLlmIcon("");
            line.setLlmEngravingContent(plain.group(1).trim());
            return true;
        }
        return false;
    }

    private static boolean applyCufflinkRule(ItemDetail line, String excerpt) {
        if (!CUFFLINK_PICTURE.matcher(excerpt).find()) {
            return false;
        }
        line.setLlmDesignStyle("人物头像(见附图)");
        line.setLlmFont("");
        line.setLlmIcon("");
        line.setLlmEngravingContent("");
        return true;
    }

    private static String mapFontNumber(String num) {
        if (num == null || num.isBlank()) {
            return "";
        }
        return "Font " + num.trim() + "(no)";
    }
}
