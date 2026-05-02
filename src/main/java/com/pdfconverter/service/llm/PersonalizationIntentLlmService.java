package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 主商品 Personalization：调用大模型抽取设计风格、字体、刻录内容、Icon，并校验落在允许列表内。
 */
@Service
public class PersonalizationIntentLlmService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationIntentLlmService.class);

    @Resource
    private PersonalizationLlmProperties properties;

    @Resource
    private PersonalizationCanonicalLibraryService canonicalLibraryService;

    @Resource
    private DeepSeekPersonalizationClient deepSeekClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 仅填充 ItemDetail 的 llm* 字段，不修改规则引擎结果的 style/font。
     */
    public void enrichMainItemIfApplicable(ItemDetail item) {
        if (item == null) {
            return;
        }
        if (!properties.isEnabled()) {
            return;
        }
        String p = item.getPersonalization();
        if (p == null || p.trim().isEmpty()) {
            return;
        }

        List<String> styles = canonicalLibraryService.getDesignStyles();
        List<String> fonts = canonicalLibraryService.getFonts();
        List<String> icons = canonicalLibraryService.getIcons();
        if (styles.isEmpty() && fonts.isEmpty() && icons.isEmpty()) {
            log.warn("设计风格、字体与 Icon 允许列表均为空，跳过 LLM");
            return;
        }

        Set<String> styleSet = new LinkedHashSet<>(styles);
        Set<String> fontSet = new LinkedHashSet<>(fonts);
        Set<String> iconSet = new LinkedHashSet<>(icons);

        String systemPrompt = buildSystemPrompt(styleSet, fontSet, iconSet);
        String userPrompt = buildUserPrompt(item);

        String rawJson = deepSeekClient.chatCompletionJson(systemPrompt, userPrompt);
        if (rawJson == null) {
            log.info("DeepSeek 未返回有效内容，订单行跳过意图抽取");
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(rawJson);
            String ds = pickCanonical(textField(node, "designStyle", "design_style", "style"), styleSet);
            String ft = pickCanonical(textField(node, "font", "fontName", "font_name"), fontSet);
            String ic = pickCanonical(textField(node, "icon", "Icon"), iconSet);
            String eng = textField(node, "engravingContent", "engraving_content", "刻录内容");
            if (eng != null) {
                eng = eng.trim();
            } else {
                eng = "";
            }

            item.setLlmDesignStyle(ds);
            item.setLlmFont(ft);
            item.setLlmIcon(ic);
            item.setLlmEngravingContent(eng);
            log.info("Personalization DeepSeek 抽取已写入商品行: designStyle=[{}] font=[{}] icon=[{}] engravingContent 长度={}",
                    ds, ft, ic, eng.length());
        } catch (Exception e) {
            log.warn("解析模型 JSON 失败，原始片段: {}", truncate(rawJson, 200), e);
        }
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }

    private static String textField(JsonNode node, String... names) {
        for (String n : names) {
            JsonNode v = node.get(n);
            if (v != null && v.isTextual()) {
                return v.asText();
            }
        }
        return "";
    }

    private static String pickCanonical(String raw, Set<String> allowed) {
        if (raw == null || raw.isBlank() || allowed.isEmpty()) {
            return "";
        }
        String t = raw.trim();
        if (allowed.contains(t)) {
            return t;
        }
        for (String a : allowed) {
            if (a.equalsIgnoreCase(t)) {
                return a;
            }
        }
        return "";
    }

    private static String buildSystemPrompt(Set<String> styles, Set<String> fonts, Set<String> icons) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是电商订单刻录意图抽取助手。根据商品上下文和客户 Personalization 文本，输出一段 JSON（不要 markdown）。\n");
        sb.append("字段要求：\n");
        sb.append("- designStyle: 字符串，必须从下列「设计风格」中选一项完全一致的写法；无法判断则填 \"\"。\n");
        sb.append("- font: 字符串，必须从下列「字体」中选一项完全一致的写法；无法判断则填 \"\"。\n");
        sb.append("- icon: 字符串，若客户在 Personalization 中指定了图标编号，必须从下列「Icon」中选一项完全一致的写法（如 icon #3）；未提及图标则填 \"\"。\n");
        sb.append("- engravingContent: 字符串，客户希望刻录的正文（可含换行）；仅刻录文案，不要复述 Style/Font/Icon 说明；无法判断则填 \"\"。\n");
        sb.append("示例 JSON 输出：{\"designStyle\":\"Style 5\",\"font\":\"Font 10(no)\",\"icon\":\"icon #3\",\"engravingContent\":\"Love\"}\n\n");
        sb.append("设计风格允许值（逐字匹配）：\n");
        appendQuotedLines(sb, styles);
        sb.append("\n字体允许值（逐字匹配）：\n");
        appendQuotedLines(sb, fonts);
        sb.append("\nIcon 允许值（逐字匹配，无图标则输出空字符串）：\n");
        appendQuotedLines(sb, icons);
        return sb.toString();
    }

    private static void appendQuotedLines(StringBuilder sb, Set<String> lines) {
        for (String s : lines) {
            sb.append("- ").append(s).append('\n');
        }
    }

    private static String buildUserPrompt(ItemDetail item) {
        StringBuilder sb = new StringBuilder();
        sb.append("商品标题：\n");
        sb.append(item.getItemTitle() != null ? item.getItemTitle() : "").append("\n\n");
        sb.append("订购完全信息（节选属性与说明）：\n");
        String dyn = item.getDynamicAttributes();
        if (dyn != null && dyn.length() > 6000) {
            dyn = dyn.substring(0, 6000) + "\n...[truncated]";
        }
        sb.append(dyn != null ? dyn : "").append("\n\n");
        sb.append("Personalization 原文：\n");
        sb.append(item.getPersonalization() != null ? item.getPersonalization() : "");
        return sb.toString();
    }
}
