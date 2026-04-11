package com.pdfconverter.util;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ExtractUtil {

    public String getitemTitle(String block) {
        // 1. 提取商品标题：从块开头到 "Quantity:" 之前
        int qtyIndex = block.indexOf("Quantity:");
        if (qtyIndex == -1) {
            throw new IllegalArgumentException("Invalid block: missing 'Quantity:'");
        }
        String rawTitle = block.substring(0, qtyIndex).trim();

        // 2. 过滤 PDF 订单头部和其他噪音行，收集商品标题行
        //
        // 策略：从后往前倒扫（从最接近 Quantity: 的行开始），
        //       收集「连续非空行组」作为商品标题（最后一个段落），
        //       忽略前面所有的订单头部内容（地址、日期、Shop、Payment method 等）
        //
        // 同时对每一行做噪音过滤，确保即使「标题段」中混入噪音也能排除
        String[] allLines = rawTitle.split("\n", -1);

        // 倒扫找到最后一个「段落」（连续非空行组）= 商品标题
        java.util.List<String> titleLines = new java.util.ArrayList<>();
        boolean inLastParagraph = false;
        for (int i = allLines.length - 1; i >= 0; i--) {
            String line = allLines[i].trim();
            if (line.isEmpty()) {
                if (inLastParagraph) {
                    // 遇到空行，且已经收集了标题行 → 标题段结束，停止倒扫
                    break;
                }
                // 还没进入标题段，继续往前找
            } else {
                inLastParagraph = true;
                titleLines.add(0, line); // 保持正向顺序
            }
        }

        // 3. 对收集到的标题行逐行过滤噪音（以防万一标题段内有混入）
        // 注意：正常情况下商品块的「最后一个段落」就是纯商品标题，
        // 但对于第一个商品（块头包含 Ship to 地址等），可能没有空行把标题与头部分开，
        // 此时 inLastParagraph 会一路往前延伸，需要依靠行过滤来排除噪音。
        StringBuilder titleBuilder = new StringBuilder();
        for (String trimmed : titleLines) {
            // "Ship to" 可能和商品标题同行，如 "Ship to Custom Photo Round Disc Tie Clip..."
            if (trimmed.startsWith("Ship to ")) {
                String afterShipTo = trimmed.substring("Ship to ".length()).trim();
                if (!afterShipTo.isEmpty()) {
                    if (titleBuilder.length() > 0) titleBuilder.append(" ");
                    titleBuilder.append(afterShipTo);
                }
                continue;
            }
            if (trimmed.equals("Ship to")) continue;

            // 过滤纯地址行：以数字开头（街道号）
            if (trimmed.matches("^\\d+\\s+[A-Za-z].*")) continue;
            // 过滤城市/州/邮编行
            if (trimmed.matches(".*,\\s*[A-Z]{2}\\s+\\d{5}.*")) continue;
            // 过滤国家行
            if (trimmed.equalsIgnoreCase("United States") || trimmed.equalsIgnoreCase("Canada")) continue;
            // 过滤日期行（如 "Mar 27, 2026"）
            if (trimmed.matches("^[A-Za-z]{3}\\s+\\d{1,2},\\s+\\d{4}$")) continue;
            // 过滤支付方式值行（如 "Paid via Etsy Payments"）
            if (trimmed.startsWith("Paid via") || trimmed.startsWith("Shipped via")) continue;
            // 过滤左栏标记行
            if (trimmed.startsWith("Scheduled to ship by") || trimmed.startsWith("Shop") ||
                trimmed.startsWith("Order date") || trimmed.startsWith("Payment method") ||
                trimmed.startsWith("Shipping method")) {
                continue;
            }
            // 过滤 "Marked as gift X items" 行
            if (trimmed.startsWith("Marked as gift")) continue;

            if (titleBuilder.length() > 0) titleBuilder.append(" ");
            titleBuilder.append(trimmed);
        }
        return titleBuilder.toString().trim();
    }

    /**
     * 解析个性化信息
     *
     * <p>从 "Personalization:" 之后提取内容，遇到以下情况截断：
     * <ul>
     *   <li>独立的 "Shop" 行（PDF 左栏标记，后续是店名+下一个商品标题）</li>
     *   <li>"Order date" / "Payment method" 等左栏固定信息行</li>
     *   <li>"Do the green thing" 页脚内容</li>
     *   <li>"Custom Engraved Initials" 等下一个商品的标志性标题关键词</li>
     * </ul>
     * </p>
     */
    public String getPersonalization(String block) {
        String personalization = "";
        int personalizationStart = block.indexOf("Personalization:");
        if (personalizationStart != -1) {
            personalizationStart += "Personalization:".length();
            int nextItemStart = block.indexOf("Custom Engraved Initials", personalizationStart);
            int end = (nextItemStart == -1) ? block.length() : nextItemStart;
            personalization = block.substring(personalizationStart, end).trim();
            
            // 过滤噪音行，遇到左栏边界标记立即停止
            StringBuilder sb = new StringBuilder();
            for (String line : personalization.split("\n")) {
                String trimmed = line.trim();
                if (trimmed.isEmpty()) continue;
                // 过滤 Engraving Fee / Additional Add-on Engraving
                if (trimmed.matches("(?i).*[Ee]ngraving\\s+[Ff]ee.*")) continue;
                if (trimmed.matches("(?i).*[Aa]dditional\\s+[Aa]dd-[Oo]n.*[Ee]ngraving.*")) continue;
                // 过滤 Additional Engraving Options 行
                if (trimmed.matches("(?i).*[Aa]dditional\\s+[Ee]ngraving\\s+[Oo]ptions.*")) continue;
                // 过滤 Quantity 行（附加雕刻数量）
                if (trimmed.matches("(?i)^Quantity:\\s*\\d+$")) continue;
                // 遇到 PDF 左栏边界标记，停止（后续内容属于订单基本信息或下一商品标题）
                if (trimmed.equals("Shop") || trimmed.startsWith("Order date") ||
                    trimmed.startsWith("Payment method") || trimmed.startsWith("Shipping method") ||
                    trimmed.startsWith("Packaging") || trimmed.startsWith("Tracking") ||
                    trimmed.startsWith("Scheduled to ship by")) {
                    break;
                }
                // 过滤 Do the green thing 及之后的内容
                if (trimmed.contains("Do the green thing")) break;
                if (sb.length() > 0) sb.append("\n");
                sb.append(trimmed);
            }
            personalization = sb.toString();
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
        // 清理日期文本中的多余空格和特殊字符
        LocalDate date = LocalDate.parse(dateText.replaceAll("\\s+", " ").trim(),
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

        int quantity;
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
