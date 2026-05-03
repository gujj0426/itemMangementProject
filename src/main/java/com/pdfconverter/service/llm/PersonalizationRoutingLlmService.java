package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * 可选第一阶段：从杂乱 Personalization 中语义筛选「仅与本导出商品行相关」的买家表述，
 * 输出短 JSON；第二阶段 {@link PersonalizationIntentLlmService} 仍使用原有四维扁平 schema。
 */
@Service
public class PersonalizationRoutingLlmService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationRoutingLlmService.class);

    private static final String ROUTING_SYSTEM = """
            你是订单留言路由助手。**每次请求只针对单条导出商品行**，文本范围仅限本条；不得引用本订单其它商品的留言。
            客户 Personalization 格式自由，同一段内可能同时描述多件商品（仅限本条 PDF 区块内的描述）。
            你的任务：根据「当前导出商品行类型」与「刻录槽位说明」，从全文里裁剪出**仅与该行商品相关**的句子或段落，
            写入 buyerMessageForThisLineOnly。
            硬性要求：
            - **保留原文措辞与长度**：逐句摘录或拼接即可，禁止概括、禁止缩写、禁止省略号截断；刻录句子多长就保留多长（例如 "Big Brother"、"thank you for everything" 必须完整保留）。
            - 多条相关行可换行拼接；不要删掉括号内说明、不要合并无关商品的句子。
            - 若没有与该行相关的表述则 buyerMessageForThisLineOnly 填空字符串，禁止编造。
            confidence：high / medium / low（对本条摘录把握程度的自评）。
            仅输出 JSON（不要 markdown），字段：
            {"buyerMessageForThisLineOnly":"","confidence":"low"}
            """;

    @Resource
    private PersonalizationLlmProperties properties;

    @Resource
    private DeepSeekPersonalizationClient deepSeekClient;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * @return 收窄后的买家表述；不应使用时返回 null（调用方回落到单阶段）
     */
    public String narrowBuyerMessageForLine(ItemDetail item) {
        if (!properties.isEnabled() || !properties.isRoutingEnabled()) {
            return null;
        }
        String full = item.getPersonalization();
        if (full == null || full.trim().length() < properties.getRoutingMinPersonalizationChars()) {
            return null;
        }

        String user = buildRoutingUserPrompt(item);
        String model = properties.getRoutingModel();
        if (model == null || model.isBlank()) {
            model = null;
        }
        String raw = deepSeekClient.chatCompletionJson(
                ROUTING_SYSTEM,
                user,
                properties.getRoutingMaxTokens(),
                model);
        if (raw == null) {
            log.info("Personalization 路由阶段未返回内容，将使用单阶段抽取");
            return null;
        }
        try {
            JsonNode n = objectMapper.readTree(raw);
            String msg = textField(n,
                    "buyerMessageForThisLineOnly",
                    "buyer_message_for_this_line_only",
                    "focusedMessage",
                    "message");
            String conf = textField(n, "confidence");
            if (msg == null || msg.isBlank()) {
                log.debug("路由阶段 buyerMessageForThisLineOnly 为空 confidence={}", conf);
                return null;
            }
            log.info("Personalization 路由阶段完成 confidence={} 收窄长度={}", conf, msg.length());
            return msg.trim();
        } catch (Exception e) {
            log.warn("解析路由 JSON 失败: {}", truncate(raw, 180), e);
            return null;
        }
    }

    private static String buildRoutingUserPrompt(ItemDetail item) {
        StringBuilder sb = new StringBuilder();
        sb.append(PersonalizationLlmInputSanitizer.singleItemScopeBanner()).append('\n');
        if (item.getLlmSlotInstruction() != null && !item.getLlmSlotInstruction().isBlank()) {
            sb.append("【刻录登记槽位说明】\n").append(item.getLlmSlotInstruction().trim()).append("\n\n");
        }
        OrderType ot = item.getOrderType();
        if (ot != null && ot != OrderType.UNKNOWN) {
            sb.append("当前导出商品行类型：").append(ot.getDisplayName())
                    .append(" / ").append(ot.getOrderTypeCode()).append("\n\n");
        }
        sb.append("商品标题：\n").append(item.getItemTitle() != null ? item.getItemTitle() : "").append("\n\n");
        String dyn = PersonalizationLlmInputSanitizer.dynamicAttributesWithoutTrailingPersonalization(
                item.getDynamicAttributes(), item.getPersonalization());
        if (dyn.length() > 4000) {
            dyn = dyn.substring(0, 4000) + "\n...[truncated]";
        }
        sb.append("订购完全信息（仅本条商品属性区）：\n").append(dyn).append("\n\n");

        String excerpt = item.getPersonalizationTextForLlm();
        if (excerpt != null && !excerpt.isBlank()
                && item.getPersonalization() != null && !excerpt.equals(item.getPersonalization())) {
            sb.append("【已由规则收窄的片段】（优先以此为线索，但仍可对照全文纠错）：\n")
                    .append(excerpt).append("\n\n");
        }
        String fullPers = item.getPersonalization();
        sb.append("【客户 Personalization 全文】\n");
        if (fullPers != null && fullPers.length() > 8000) {
            sb.append(fullPers, 0, 8000).append("\n...[truncated]");
        } else {
            sb.append(fullPers != null ? fullPers : "");
        }
        return sb.toString();
    }

    private static String textField(JsonNode node, String... names) {
        for (String name : names) {
            JsonNode v = node.get(name);
            if (v != null && v.isTextual()) {
                return v.asText();
            }
        }
        return "";
    }

    private static String truncate(String s, int max) {
        if (s == null) {
            return "";
        }
        return s.length() <= max ? s : s.substring(0, max) + "...";
    }
}
