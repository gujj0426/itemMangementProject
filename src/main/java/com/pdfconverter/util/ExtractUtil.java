package com.pdfconverter.util;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ExtractUtil {

    public String getitemTitle(String block) {
        // 1. 提取商品标题：从块开头到 "Quantity:" 之前
        int qtyIndex = block.indexOf("Quantity:");
        if (qtyIndex == -1) {
            throw new IllegalArgumentException("Invalid block: missing 'Quantity:'");
        }
        return block.substring(0, qtyIndex).trim();
    }

    /**
     * 解析个性化信息
     */
    public String getPersonalization(String block) {
        String personalization = "";
        int personalizationStart = block.indexOf("Personalization:");
        if (personalizationStart != -1) {
            personalizationStart += "Personalization:".length();
            int nextItemStart = block.indexOf("Custom Engraved Initials", personalizationStart);
            int end = (nextItemStart == -1) ? block.length() : nextItemStart;
            personalization = block.substring(personalizationStart, end).trim();
        }
        return personalization;
    }

    public String extractBetween(String text, String startRegex, String endRegex) {
        Pattern pattern = Pattern.compile(startRegex + "(.*?)" + endRegex, Pattern.DOTALL);
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1) : "";
    }

    public String extractAfter(String text, String marker) {
        int index = text.indexOf(marker);
        return index != -1 ? text.substring(index).trim() : "";
    }

    /**
     * 日期文本转换
     */
    public LocalDate cleanDateText(String dateText) {
        LocalDate date = null;
        // 清理日期文本中的多余空格和特殊字符
        date = LocalDate.parse(dateText.replaceAll("\\s+", " ").trim(),
                    DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
        return date;
    }

    /**
     * 提取商品数量 Quantity
     */
    public int extractLineAfter(String text, String keyword) {
        int index = text.indexOf(keyword);
        if (index == -1) return 0;
        index += keyword.length();
        int eol = text.indexOf("\n", index);

        int quantity = 1;
        try {
            quantity = Integer.parseInt(eol == -1 ? text.substring(index).trim() : text.substring(index, eol).trim().split("\\s+")[0]);
        } catch (Exception e) {
            quantity = 1; // 默认
        }
        return quantity;
    }
    /**
     * 从动态属性文本中解析属性Map
     * 用于将多行动态属性文本转换为key-value格式
     *
     * @param lines 动态属性文本行数组
     * @return 属性Map
     */
    public java.util.Map<String, String> parseDynamicAttributes(String[] lines) {
        java.util.Map<String, String> attrs = new java.util.HashMap<>();

        if (lines == null || lines.length == 0) {
            return attrs;
        }

        for (String line : lines) {
            line = line.trim();
            if (line.isEmpty()) {
                continue;
            }

            // 尝试按冒号分割
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                attrs.put(key, value);
            } else {
                // 如果没有冒号，整行作为value
                attrs.put(line, line);
            }
        }

        return attrs;
    }
}
