package com.pdfconverter.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalizationParserUtil {

    /**
     * 从个性化文本中提取设计风格（Style）
     * @param personalization 个性化内容（如 "Style 27, AB" 或 "Style 27, Font 23"）
     * @return 提取到的设计风格，如 "Style 27"；未找到返回 null
     */
    public static String extractStyle(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }

        // 匹配 "Style XX" 或 "style XX" 格式（XX是1-2位数字）
        Matcher styleMatcher = Pattern.compile("(?i)style\\s+(\\d{1,2})\\b").matcher(personalization);
        if (styleMatcher.find()) {
            return "Style " + styleMatcher.group(1);
        }

        return null; // 未找到设计风格
    }

    /**
     * 从个性化文本中提取字体（Font）
     * @param personalization 个性化内容（如 "Font 23" 或 "Script"）
     * @return 提取到的字体名称，如 "Font 23"、"Script"、"Block"；未找到返回 null
     */
    public static String extractFont(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }

        // 方法1：匹配 "Font XX" 格式（XX是1-2位数字）
        Matcher fontNumMatcher = Pattern.compile("(?i)font\\s*#?\\s*(\\d{1,2})\\b").matcher(personalization);
        if (fontNumMatcher.find()) {
            return "Font " + fontNumMatcher.group(1);
        }

        // 方法2：匹配常见字体名称（如 Script, Block, Roman 等）
        Matcher fontNameMatcher = Pattern.compile("(?i)\\b(Script|Block|Roman|Times|Arial|Helvetica|Courier)\\b").matcher(personalization);
        if (fontNameMatcher.find()) {
            return fontNameMatcher.group(1);
        }

        return null; // 未找到字体
    }
}
