package com.pdfconverter.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalizationParser {

    // 定义常见的字体关键词映射（可扩展）
    private static final Pattern FONT_PATTERN = Pattern.compile(
            "(?i)\\b(style|font|design|type)\\s*(?:[:\\s])?\\s*(\\d+|[A-Za-z][A-Za-z\\s]*(?=,|\\)|$))"
    );

    // 更直接匹配如 "Script", "Block", "Roman" 等常见字体名
    private static final Pattern DIRECT_FONT_PATTERN = Pattern.compile(
            "(?i)(?:style|font|design)\\s*[:\\s]\\s*([A-Za-z][A-Za-z\\s]*?)(?=,|\\(|\\n|$)"
    );

    /**
     * 从个性化文本中提取字体名称
     * @param personalization 个性化内容（如 "Cufflinks: style 27, style 15 ED"）
     * @return 提取到的字体名称，如 "style 27" 或 "Script"；未找到返回 null
     */
    public static String extractFont(String personalization) {
        if (personalization == null || personalization.trim().isEmpty()) {
            return null;
        }

        // 方法1：尝试匹配 "style XXX" 或 "font Block"
        Matcher matcher = DIRECT_FONT_PATTERN.matcher(personalization);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }

        // 方法2：备用规则：查找常见字体关键词
        // 示例：我们也可以维护一个常见字体列表
        String[] commonFonts = {"Script", "Block", "Roman", "Italic", "Gothic", "Serif", "Sans", "Monospace"};
        String lower = personalization.toLowerCase();

        for (String font : commonFonts) {
            if (lower.contains(font.toLowerCase())) {
                return font;
            }
        }

        // 方法3：如果包含 "style XX" 数字形式
        Matcher numMatcher = Pattern.compile("(?i)style\\s+(\\d+)").matcher(personalization);
        if (numMatcher.find()) {
            return "style " + numMatcher.group(1);
        }

        return null; // 未识别
    }
}