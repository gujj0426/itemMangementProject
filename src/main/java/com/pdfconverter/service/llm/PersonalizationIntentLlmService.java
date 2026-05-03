package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.util.StyleEngravingDefaultFontUtil;
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
    private PersonalizationRoutingLlmService personalizationRoutingLlmService;

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
        String fullPers = item.getPersonalization();
        String excerpt = item.getPersonalizationTextForLlm();
        boolean hasText = (fullPers != null && !fullPers.trim().isEmpty())
                || (excerpt != null && !excerpt.trim().isEmpty());
        if (!hasText) {
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
        String llmExcerpt = item.getPersonalizationTextForLlm();
        String primaryForModel = (llmExcerpt != null && !llmExcerpt.isBlank())
                ? llmExcerpt
                : fullPers;
        String routed = personalizationRoutingLlmService.narrowBuyerMessageForLine(item);
        if (routed != null && !routed.isBlank()) {
            primaryForModel = routed;
        }
        String userPrompt = buildUserPrompt(item, primaryForModel);

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

            ft = StyleEngravingDefaultFontUtil.applyIfEligible(ft, ds, eng,
                    item.getPersonalization(), item.getDynamicAttributes(), properties.isEnabled());
            ft = pickCanonical(ft, fontSet);

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
        sb.append("你是电商订单刻录意图抽取助手。**每次 API 调用只处理一条商品行的数据**，应用程序按顺序逐条发送；不得推断「其它商品」的留言内容。\n");
        sb.append("客户 Personalization 是自然语言留言，没有固定模板：可能出现缩写、口语、中英混写、换行随意、" +
                "同一含义多种说法（例如领带夹可能写作 tie clip / tieclip / clip / TC / 「夹」等）。你只能在语义上理解「客户想刻什么」，不要假设客户会按某个关键字才能解析。\n\n");
        sb.append("工作流程（在心中完成即可，不要输出过程）：\n");
        sb.append("1) **每一导出商品行彼此独立**：只解析「Personalization（本行抽取依据）」及附录上下文里**与该 Excel 行品类一致**的句子；禁止套用本订单其它商品行的结论。\n");
        sb.append("2) 根据「刻录登记槽位说明」与「当前导出商品行类型」，锁定本条是袖扣还是领带夹（或其它）；混写时仅采纳与客户意图匹配的片段。\n");
        sb.append("3) 将编号映射到允许列表：Style n / Sn / S n → designStyle；Font n → font；icon → icon #n。领带夹行里形如 tie clip: S4(JB)、clip S44- AT 等，**必须把该行对应的 Style 写入 designStyle**，不得留空（若能映射）。\n");
        sb.append("4) **默认字体**：若留言中出现「默认字体」「默認字體」「default font」且未指定其它 Font 编号（无 Font n / font n / F n 等），font 必须填 \"Font 3(no)\"。\n");
        sb.append("5) **Style + 刻录但未写字体**：若已解析出含 Style 的设计风格且 engravingContent 非空，但留言中无任何字体编号，font 填 \"Font 3(no)\"。\n");
        sb.append("6) 凡无法映射到允许列表的值填空字符串 \"\"。\n\n");
        sb.append("字段要求（输出仍是扁平 JSON，不要 markdown）：\n");
        sb.append("- designStyle: 必须从下列列表选一完全一致写法；无法判断填 \"\"。\n");
        sb.append("- font: 必须从下列列表选一完全一致写法；无法判断填 \"\"。\n");
        sb.append("- icon: 客户提及图标编号则映射为列表中的 \"icon #n\"；未提及填 \"\"。\n");
        sb.append("- engravingContent: **刻录正文须与客户意图完全一致**：保留完整短语与标点，禁止缩短（勿把 Brother→Broth）、勿省略后半句（勿丢掉 thank you for everything）、勿擅自改写；\n");
        sb.append("  去掉已成功归入 designStyle/font/icon 的纯编号标签后，剩余可读文案全部放入本字段（可很长、可多行）；确实无刻录字时再填 \"\"。\n\n");
        sb.append("示例（短字段仅为演示映射；真实订单刻录可能很长须完整输出）：\n");
        sb.append("- {\"designStyle\":\"Style 15\",\"font\":\"Font 3(no)\",\"icon\":\"icon #40\",\"engravingContent\":\"BG\"}\n");
        sb.append("- {\"designStyle\":\"Style 4\",\"font\":\"\",\"icon\":\"\",\"engravingContent\":\"JB\"}\n");
        sb.append("- {\"designStyle\":\"\",\"font\":\"Font 28\",\"icon\":\"\",\"engravingContent\":\"Dad, thank you for everything\"}\n\n");
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

    /**
     * @param personalizationPrimaryForModel 本行抽取主文本：规则收窄片段、路由收窄结果或全文
     */
    private static String buildUserPrompt(ItemDetail item, String personalizationPrimaryForModel) {
        StringBuilder sb = new StringBuilder();
        sb.append(PersonalizationLlmInputSanitizer.singleItemScopeBanner()).append('\n');
        if (item.getLlmSlotInstruction() != null && !item.getLlmSlotInstruction().isBlank()) {
            sb.append("【刻录登记槽位说明】\n");
            sb.append(item.getLlmSlotInstruction().trim()).append("\n\n");
        }
        OrderType ot = item.getOrderType();
        if (ot != null && ot != OrderType.UNKNOWN) {
            sb.append("当前导出商品行类型（以之为准，不要被标题里出现的其它品类词误导）：\n");
            sb.append(ot.getDisplayName()).append(" / ").append(ot.getOrderTypeCode()).append("\n\n");
        }
        sb.append("商品标题：\n");
        sb.append(item.getItemTitle() != null ? item.getItemTitle() : "").append("\n\n");
        sb.append("订购完全信息（仅本条商品的属性区；Personalization 正文见下方单独字段）：\n");
        String dyn = PersonalizationLlmInputSanitizer.dynamicAttributesWithoutTrailingPersonalization(
                item.getDynamicAttributes(), item.getPersonalization());
        if (dyn.length() > 6000) {
            dyn = dyn.substring(0, 6000) + "\n...[truncated]";
        }
        sb.append(dyn).append("\n\n");

        String persPrimary = personalizationPrimaryForModel != null ? personalizationPrimaryForModel : "";
        sb.append("Personalization（本行抽取依据）：\n");
        sb.append(persPrimary);
        String fullPers = item.getPersonalization();
        if (fullPers != null && !persPrimary.trim().equals(fullPers.trim())) {
            sb.append("\n\n【完整 Personalization 上下文】（用于核对长句与后半段，避免遗漏）\n");
            String ctx = fullPers;
            int cap = 12000;
            if (ctx.length() > cap) {
                ctx = ctx.substring(0, cap) + "\n...[truncated]";
            }
            sb.append(ctx);
        }
        return sb.toString();
    }
}
