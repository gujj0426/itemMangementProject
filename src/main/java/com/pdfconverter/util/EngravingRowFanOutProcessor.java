package com.pdfconverter.util;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 将同一 PDF 商品行拆成多条 {@link ItemDetail}（每条对应一个刻录意图槽位），
 * 以便在不改变 LLM 扁平 JSON 的前提下逐行调用 {@link com.pdfconverter.service.llm.PersonalizationIntentLlmService}。
 *
 * <p>策略简述：</p>
 * <ul>
 *   <li>领带夹：动态属性判定多面刻录且数量为 1 时，按面拆行（优先 Front/Back 文案切段）。</li>
 *   <li>袖扣：启发式判定「两只内容不同」且数量为 1 时拆两行。</li>
 *   <li>组合（同一段 Personalization 既含袖扣又含领带夹）：对相应 OrderType 的行收窄片段并加槽位说明（可不增加行数）。</li>
 * </ul>
 */
@Component
public class EngravingRowFanOutProcessor {

    private static final Logger log = LoggerFactory.getLogger(EngravingRowFanOutProcessor.class);

    /**
     * 对订单内商品列表逐条展开（保持顺序）。
     */
    public List<ItemDetail> expandOrderLines(List<ItemDetail> items) {
        if (items == null || items.isEmpty()) {
            return items;
        }
        List<ItemDetail> out = new ArrayList<>(items.size() + 2);
        for (ItemDetail item : items) {
            out.addAll(expandOne(item));
        }
        return out;
    }

    List<ItemDetail> expandOne(ItemDetail item) {
        if (item == null) {
            return Collections.emptyList();
        }
        String pers = item.getPersonalization();
        if (pers == null || pers.isBlank()) {
            return List.of(item);
        }

        OrderType ot = item.getOrderType();
        if (ot != OrderType.TIE_CLIP && ot != OrderType.CUFFLINK && ot != OrderType.CUFFLINK_AND_TIE_CLIP) {
            return List.of(item);
        }

        int qty = Math.max(item.getItemQuantity(), 1);
        String dyn = item.getDynamicAttributes() != null ? item.getDynamicAttributes() : "";
        int faces = EngravingFaceCountUtil.countFaces(dyn);

        // 数量与面数同时大于 1 时避免笛卡尔积歧义，保留单行由 Excel 按旧逻辑倍增（LLM 结论在各面相同）。
        if (qty > 1 && faces > 1) {
            log.debug("跳过刻录拆行: orderType={} qty={} faces={}", ot, qty, faces);
            return List.of(item);
        }

        boolean combo = PersonalizationSlotSplitter.mentionsCufflinkAndTieClip(pers);

        if (ot == OrderType.TIE_CLIP) {
            // 袖扣+领带夹共用一段 Personalization 时，dynamicAttributes 里仍可能含 Front & Back（listing 模板），
            // 不能据此对领带夹做多面拆行，否则会多插行且从未执行组合收窄，导致刻录意图错位。
            if (!combo) {
                List<ItemDetail> multiFace = tryFanOutTieClipFaces(item, faces, qty);
                if (multiFace.size() > 1) {
                    return multiFace;
                }
            }
            if (combo) {
                return List.of(withComboNarrowing(item, OrderType.TIE_CLIP));
            }
            return List.of(item);
        }

        if (ot == OrderType.CUFFLINK) {
            if (qty == 1 && PersonalizationSlotSplitter.shouldSplitCufflinkDualIntent(pers)) {
                List<ItemDetail> dual = fanOutCufflinkDual(item, pers);
                if (dual.size() > 1) {
                    return dual;
                }
            }
            if (combo) {
                return List.of(withComboNarrowing(item, OrderType.CUFFLINK));
            }
            return List.of(item);
        }

        // CUFFLINK_AND_TIE_CLIP：少见；尝试组合收窄
        if (combo) {
            return List.of(withComboNarrowing(item, ot));
        }
        return List.of(item);
    }

    private List<ItemDetail> tryFanOutTieClipFaces(ItemDetail base, int faces, int qty) {
        if (faces <= 1 || qty != 1) {
            return List.of(base);
        }
        String pers = base.getPersonalization().trim();
        List<String> segments = PersonalizationSlotSplitter.trySplitFrontBack(pers);
        if (segments.size() != faces && faces == 2) {
            segments = List.of(pers, pers);
        } else if (segments.size() != faces) {
            // 3～5 面：暂无可靠切段，复制全文由槽位说明区分
            segments = new ArrayList<>(faces);
            for (int i = 0; i < faces; i++) {
                segments.add(pers);
            }
        }

        List<ItemDetail> rows = new ArrayList<>(faces);
        for (int i = 0; i < segments.size(); i++) {
            String excerpt = segments.get(i);
            String label = faceLabel(i + 1, faces);
            ItemDetail row = copyForSlot(base, excerpt,
                    "本行登记领带夹第 " + (i + 1) + "/" + faces + " 面刻录意图（" + label + "）。只抽取与该面相关的 Style / Font / Icon / 刻录正文；无关信息忽略。",
                    i == 0 ? base.getItemQuantity() : 0);
            rows.add(row);
        }
        log.info("领带夹多面拆行: faces={} listingId={}", faces, base.getListingId());
        return rows;
    }

    private static String faceLabel(int index1Based, int total) {
        if (total == 2) {
            return index1Based == 1 ? "正面/Front" : "反面/Back";
        }
        return "第 " + index1Based + " 面";
    }

    private List<ItemDetail> fanOutCufflinkDual(ItemDetail base, String pers) {
        List<String> parts = PersonalizationSlotSplitter.trySplitCufflinkPair(pers);
        if (parts.size() < 2) {
            parts = List.of(pers, pers);
        }
        ItemDetail row1 = copyForSlot(base, parts.get(0),
                "本行登记袖扣「第一只 / 第一面」刻录意图；忽略第二只描述。", base.getItemQuantity());
        ItemDetail row2 = copyForSlot(base, parts.get(1),
                "本行登记袖扣「第二只 / 第二面」刻录意图；忽略第一只描述。", 0);
        log.info("袖扣双意图拆行: listingId={}", base.getListingId());
        return List.of(row1, row2);
    }

    private ItemDetail withComboNarrowing(ItemDetail base, OrderType ot) {
        String pers = base.getPersonalization();
        String excerpt;
        String instruction;
        if (ot == OrderType.TIE_CLIP) {
            excerpt = PersonalizationSlotSplitter.excerptForTieClip(pers);
            instruction = "本订单行商品为领带夹。Personalization 可能同时包含袖扣说明；请仅抽取领带夹相关的 Style / Font / Icon / 刻录正文。";
        } else if (ot == OrderType.CUFFLINK) {
            excerpt = PersonalizationSlotSplitter.excerptForCufflink(pers);
            instruction = "本订单行商品为袖扣。Personalization 可能同时包含领带夹说明；请仅抽取袖扣相关的 Style / Font / Icon / 刻录正文。";
        } else {
            excerpt = pers;
            instruction = "本订单行为袖扣+领带夹组合 listing 中的一行，请仅抽取与本行商品类型相关的刻录信息。";
        }
        if (excerpt == null || excerpt.isBlank()) {
            excerpt = pers;
        }
        return copyForSlot(base, excerpt, instruction, base.getItemQuantity());
    }

    private static ItemDetail copyForSlot(ItemDetail src, String personalizationForLlm,
                                         String slotInstruction, int itemQuantity) {
        ItemDetail d = shallowCopy(src);
        d.setPersonalization(src.getPersonalization());
        d.setPersonalizationTextForLlm(personalizationForLlm);
        d.setLlmSlotInstruction(slotInstruction);
        d.setEngravingFanOutApplied(true);
        d.setItemQuantity(itemQuantity);
        d.setLlmDesignStyle(null);
        d.setLlmFont(null);
        d.setLlmIcon(null);
        d.setLlmEngravingContent(null);
        return d;
    }

    /**
     * 浅拷贝可序列化字段（用于拆行，避免引用共享）。
     */
    static ItemDetail shallowCopy(ItemDetail src) {
        ItemDetail d = new ItemDetail();
        d.setMainProductFlg(src.getMainProductFlg());
        d.setIsComposite(src.getIsComposite());
        d.setListingId(src.getListingId());
        d.setItemTitle(src.getItemTitle());
        d.setItemQuantity(src.getItemQuantity());
        d.setOrderType(src.getOrderType());
        d.setProductName(src.getProductName());
        d.setProductSize(src.getProductSize());
        d.setProductColor(src.getProductColor());
        d.setProductVariable(src.getProductVariable());
        d.setDynamicAttributes(src.getDynamicAttributes());
        d.setPersonalization(src.getPersonalization());
        d.setFont(src.getFont());
        d.setStyle(src.getStyle());
        d.setLlmDesignStyle(src.getLlmDesignStyle());
        d.setLlmFont(src.getLlmFont());
        d.setLlmEngravingContent(src.getLlmEngravingContent());
        d.setLlmIcon(src.getLlmIcon());
        d.setEngravingFanOutApplied(src.isEngravingFanOutApplied());
        d.setPersonalizationTextForLlm(src.getPersonalizationTextForLlm());
        d.setLlmSlotInstruction(src.getLlmSlotInstruction());
        return d;
    }
}
