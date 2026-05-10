package com.pdfconverter.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalizationParserUtil {

    /**
     * 从个性化文本中提取设计风格（Style）
     * @param personalization 个性化内容（如 "Style 27, AB" 或 "Style 27, Font 23"）
     * @return 提取到的标准设计风格，如 "Style 27"；超出 1-48 范围或未找到返回 null
     */
    public static String extractStyle(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }

        // 匹配 "Style XX" 或 "SXX" 格式（XX 是 1-2 位数字）
        Matcher styleMatcher = Pattern.compile("(?i)\\bS(tyle)?\\s*(\\d{1,2})\\b").matcher(personalization);
        if (styleMatcher.find()) {
            int num = Integer.parseInt(styleMatcher.group(2));
            if (num >= 1 && num <= 48) {
                return "Style " + num;
            }
        }

        return null;
    }

    /**
     * 从个性化文本中提取字体（Font）
     * 支持格式：
     *   - "Font 1" / "Font1" / "Font 1#" / "Font#1"
     *   - "F1" / "F 1"（单独的大写 F + 数字，数字不超过 2 位）
     *   - 常见字体名称：Script / Block / Roman 等
     * @param personalization 个性化内容
     * @return 提取到的字体标识，如 "Font 23"；未找到返回 null
     */
    public static String extractFont(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }

        // 匹配 "Font XX" / "FontXX" / "Font#XX" / "Font XX#" 等格式
        Matcher fontNumMatcher = Pattern.compile("(?i)font\\s*#?\\s*(\\d{1,2})\\s*#?").matcher(personalization);
        if (fontNumMatcher.find()) {
            return "Font " + fontNumMatcher.group(1);
        }

        // 匹配单独的 "F1" / "F23" / "F 1"（大写 F + 数字，不含 Font 关键字）
        Matcher fNumMatcher = Pattern.compile("(?i)(?<![a-zA-Z])F\\s*(\\d{1,2})\\b").matcher(personalization);
        if (fNumMatcher.find()) {
            return "Font " + fNumMatcher.group(1);
        }

        // 匹配常见字体名称
        Matcher fontNameMatcher = Pattern.compile("(?i)\\b(Script|Block|Roman|Times|Arial|Helvetica|Courier)\\b").matcher(personalization);
        if (fontNameMatcher.find()) {
            return fontNameMatcher.group(1);
        }

        return null;
    }

    /**
     * 与 {@link #extractFont} 规则一致，但取<strong>最后一次</strong>命中（双面分段或尾部 Font 更可信）。
     */
    public static String extractLastFont(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }
        String last = null;
        Matcher fontNumMatcher = Pattern.compile("(?i)font\\s*#?\\s*(\\d{1,2})\\s*#?").matcher(personalization);
        while (fontNumMatcher.find()) {
            last = "Font " + fontNumMatcher.group(1);
        }
        if (last != null) {
            return last;
        }
        Matcher fNumMatcher = Pattern.compile("(?i)(?<![a-zA-Z])F\\s*(\\d{1,2})\\b").matcher(personalization);
        while (fNumMatcher.find()) {
            last = "Font " + fNumMatcher.group(1);
        }
        if (last != null) {
            return last;
        }
        Matcher fontNameMatcher = Pattern.compile("(?i)\\b(Script|Block|Roman|Times|Arial|Helvetica|Courier)\\b").matcher(personalization);
        while (fontNameMatcher.find()) {
            last = fontNameMatcher.group(1);
        }
        return last;
    }

    /**
     * 客户是否明确要求「默认字体」（未单独写 Font n 时由上层结合 {@link #extractFont} 使用）。
     */
    public static boolean mentionsDefaultFont(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        return Pattern.compile("(?iu)(默认字体|默認字體|default\\s+font)").matcher(text).find();
    }
}
