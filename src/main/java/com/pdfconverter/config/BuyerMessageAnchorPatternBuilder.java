package com.pdfconverter.config;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将配置中的标签名编译为 PDF 行首锚点正则（{@code ^Label\\s*:}，大小写不敏感）。
 */
final class BuyerMessageAnchorPatternBuilder {

    private BuyerMessageAnchorPatternBuilder() {
    }

    static List<Pattern> compile(List<String> labels, List<String> excludePrefixes) {
        List<Pattern> patterns = new ArrayList<>();
        if (labels == null) {
            return patterns;
        }
        for (String label : labels) {
            if (label == null || label.isBlank()) {
                continue;
            }
            String trimmed = label.trim();
            if (shouldSkipLabel(trimmed, excludePrefixes)) {
                continue;
            }
            String escaped = Pattern.quote(trimmed);
            patterns.add(Pattern.compile("^" + escaped + "\\s*:", Pattern.MULTILINE | Pattern.CASE_INSENSITIVE));
        }
        return patterns;
    }

    private static boolean shouldSkipLabel(String label, List<String> excludePrefixes) {
        if (excludePrefixes == null) {
            return false;
        }
        for (String ex : excludePrefixes) {
            if (ex != null && !ex.isBlank() && label.equalsIgnoreCase(ex.trim())) {
                return true;
            }
        }
        return false;
    }
}
