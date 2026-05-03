package com.pdfconverter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 将 Personalization 按「面」或品类关键词切分为片段，供逐行 LLM 抽取。
 */
public final class PersonalizationSlotSplitter {

    private static final Pattern PAT_FRONT = Pattern.compile(
            "(?is)(?:^|\\n)\\s*(?:front|正面|正\\s*面)\\s*[:：]\\s*(.+?)(?=\\n\\s*(?:back|反面|反\\s*面)\\s*[:：]|\\z)");
    private static final Pattern PAT_BACK_TAIL = Pattern.compile(
            "(?is)\\n\\s*(?:back|反面|反\\s*面)\\s*[:：]\\s*(.+)\\z");

    private static final Pattern PAT_CUFF_FIRST_SECOND = Pattern.compile(
            "(?i)(first|1st|left)\\s*cufflink|cufflink\\s*[:：]?\\s*(?:1|one|left)|袖扣\\s*[:：]?\\s*[12一二]");
    private static final Pattern PAT_BOTH_PRODUCTS = Pattern.compile(
            "(?i)cufflink|袖扣");

    private static final Pattern PAT_TIE_CLIP = Pattern.compile(
            "(?i)tie\\s*clip|tieclip|领\\s*带\\s*夹|领带夹");

    private PersonalizationSlotSplitter() {
    }

    /**
     * 是否同时出现袖扣与领带夹相关描述（组合订单共用一段 Personalization）。
     */
    public static boolean mentionsCufflinkAndTieClip(String personalization) {
        if (personalization == null || personalization.isBlank()) {
            return false;
        }
        String p = personalization.toLowerCase(Locale.ROOT);
        boolean cuff = PAT_BOTH_PRODUCTS.matcher(p).find();
        boolean tie = PAT_TIE_CLIP.matcher(personalization).find();
        return cuff && tie;
    }

    /**
     * 启发式：袖扣一对是否需要拆成两行（两面刻录内容不同）。
     */
    public static boolean shouldSplitCufflinkDualIntent(String personalization) {
        if (personalization == null || personalization.length() < 12) {
            return false;
        }
        // 组合订单共用留言里的 front/back 多指领带夹双面，勿在此拆成「两只袖扣」
        if (mentionsCufflinkAndTieClip(personalization)) {
            return false;
        }
        String p = personalization.toLowerCase(Locale.ROOT);
        if (PAT_CUFF_FIRST_SECOND.matcher(personalization).find()) {
            return true;
        }
        if (p.contains("respectively")) {
            return true;
        }
        if ((p.contains("one cufflink") || p.contains("first cufflink")) && p.contains("other")) {
            return true;
        }
        if (p.contains("first says") && p.contains("second")) {
            return true;
        }
        // 明确双面不同文案：Front / Back 且出现 cufflink 语境
        if (p.contains("cufflink") && p.contains("front") && p.contains("back")) {
            return true;
        }
        if (personalization.contains("袖扣") && personalization.contains("正面") && personalization.contains("反面")) {
            return true;
        }
        return false;
    }

    /**
     * 尝试拆成「正面 / 反面」两段；失败返回空列表。
     */
    public static List<String> trySplitFrontBack(String personalization) {
        List<String> out = new ArrayList<>(2);
        if (personalization == null || personalization.isBlank()) {
            return out;
        }
        Matcher mFront = PAT_FRONT.matcher(personalization);
        Matcher mBack = PAT_BACK_TAIL.matcher(personalization);
        String front = null;
        String back = null;
        if (mFront.find()) {
            front = mFront.group(1).trim();
        }
        if (mBack.find()) {
            back = mBack.group(1).trim();
        }
        if (front != null && !front.isEmpty() && back != null && !back.isEmpty()) {
            out.add(front);
            out.add(back);
        }
        return out;
    }

    /**
     * 袖扣两行时的启发式拆分；失败则返回两段均为全文（由调用方改为「第一只/第二只」说明）。
     */
    public static List<String> trySplitCufflinkPair(String personalization) {
        List<String> fb = trySplitFrontBack(personalization);
        if (fb.size() == 2) {
            return fb;
        }
        List<String> pair = new ArrayList<>(2);
        Pattern splitPat = Pattern.compile(
                "(?is)\\n\\s*(?:and|&|\\+|以及)\\s*\\n|;\\s*(?=.)");
        String[] chunks = splitPat.split(personalization, 3);
        if (chunks.length >= 2 && chunks[0].trim().length() > 3 && chunks[1].trim().length() > 3) {
            pair.add(chunks[0].trim());
            pair.add(chunks[1].trim());
            return pair;
        }
        return pair;
    }

    /**
     * 从组合留言中提取更可能描述袖扣的片段。
     */
    public static String excerptForCufflink(String personalization) {
        if (personalization == null || personalization.isBlank()) {
            return "";
        }
        if (!mentionsCufflinkAndTieClip(personalization)) {
            return personalization.trim();
        }
        return extractLinesPreferringKeywords(personalization,
                "(?i)cufflink|cufflinks|袖扣");
    }

    /**
     * 从组合留言中提取更可能描述领带夹的片段。
     */
    public static String excerptForTieClip(String personalization) {
        if (personalization == null || personalization.isBlank()) {
            return "";
        }
        if (!mentionsCufflinkAndTieClip(personalization)) {
            return personalization.trim();
        }
        return extractLinesPreferringKeywords(personalization,
                "(?i)tie\\s*clip|tieclip|领\\s*带\\s*夹|领带夹");
    }

    private static String extractLinesPreferringKeywords(String text, String keywordRegex) {
        String[] lines = text.split("\\r?\\n");
        StringBuilder hit = new StringBuilder();
        Pattern kw = Pattern.compile(keywordRegex);
        for (String line : lines) {
            String t = line.trim();
            if (t.isEmpty()) {
                continue;
            }
            if (kw.matcher(t).find()) {
                if (hit.length() > 0) {
                    hit.append('\n');
                }
                hit.append(t);
            }
        }
        if (hit.length() > 0) {
            return hit.toString().trim();
        }
        return text.trim();
    }
}
