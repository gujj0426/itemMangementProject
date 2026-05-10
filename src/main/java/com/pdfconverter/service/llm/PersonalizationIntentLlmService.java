package com.pdfconverter.service.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.config.PersonalizationLlmProperties;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.PdfOrderData.ItemDetail;
import com.pdfconverter.service.mapper.FontNameMappingService;
import com.pdfconverter.util.PersonalizationParserUtil;
import com.pdfconverter.util.PersonalizationSlotSplitter;
import com.pdfconverter.util.StyleEngravingDefaultFontUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PDF 商品块 Personalization：按「同一 Quantity 块」调用大模型一次，抽取设计风格、字体、刻录内容、Icon，
 * 并写回块内所有导出明细；校验落在允许列表内。
 */
@Service
public class PersonalizationIntentLlmService {

    private static final Logger log = LoggerFactory.getLogger(PersonalizationIntentLlmService.class);

    @Resource
    private PersonalizationLlmProperties properties;

    @Resource
    private PersonalizationCanonicalLibraryService canonicalLibraryService;

    @Resource
    private FontNameMappingService fontNameMappingService;

    @Resource
    private DeepSeekPersonalizationClient deepSeekClient;

    @Resource
    private PersonalizationRoutingLlmService personalizationRoutingLlmService;

    @Resource
    private ObjectMapper objectMapper;

    /**
     * 兼容入口：单条明细等价于仅含一行的商品块。
     */
    public void enrichMainItemIfApplicable(ItemDetail item) {
        if (item == null) {
            return;
        }
        enrichProductBlock(Collections.singletonList(item));
    }

    /**
     * 解析并拆行完成后调用：按 {@link ItemDetail#getSourceBlockIndex()} 分组，
     * 每组（同一 PDF Quantity 商品块）只路由+抽取一次，再把 llm* 写入组内每一行。
     */
    public void enrichOrderItemBlocksBySourceIndex(List<ItemDetail> orderLines) {
        if (orderLines == null || orderLines.isEmpty()) {
            return;
        }
        Map<Integer, List<ItemDetail>> grouped = new LinkedHashMap<>();
        List<ItemDetail> orphans = new ArrayList<>();
        for (ItemDetail d : orderLines) {
            Integer idx = d.getSourceBlockIndex();
            if (idx == null) {
                orphans.add(d);
            } else {
                grouped.computeIfAbsent(idx, k -> new ArrayList<>()).add(d);
            }
        }
        for (ItemDetail d : orphans) {
            enrichProductBlock(Collections.singletonList(d));
        }
        for (List<ItemDetail> block : grouped.values()) {
            enrichProductBlock(block);
        }
    }

    /**
     * 同一 PDF 商品块：Personalization 在块内相同；一次 DeepSeek（+可选路由）后填满块内各 {@link ItemDetail} 的 llm*。
     */
    public void enrichProductBlock(List<ItemDetail> blockLines) {
        if (blockLines == null || blockLines.isEmpty()) {
            return;
        }
        if (!properties.isEnabled()) {
            return;
        }
        ItemDetail rep = pickRepresentativeForBlock(blockLines);
        String fullPers = rep.getPersonalization() != null ? rep.getPersonalization().trim() : "";
        List<String> fbParts = PersonalizationSlotSplitter.trySplitFrontBack(fullPers);
        boolean hasText = blockLines.stream().anyMatch(d -> {
            String p = d.getPersonalization();
            String ex = d.getPersonalizationTextForLlm();
            return (p != null && !p.trim().isEmpty()) || (ex != null && !ex.trim().isEmpty());
        });
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
        // 已检出 Front/Back(Bar) 结构时不要用路由收窄正文，否则易丢掉背面（Back/Bar）段落
        String primaryForModel = fullPers;
        String routed = null;
        if (fbParts.size() < 2) {
            routed = personalizationRoutingLlmService.narrowBuyerMessageForLine(rep);
        }
        if (routed != null && !routed.isBlank()) {
            primaryForModel = routed;
        }
        String userPrompt = buildUserPrompt(rep, primaryForModel, blockLines, fbParts.size() >= 2);

        String rawJson = deepSeekClient.chatCompletionJson(systemPrompt, userPrompt);
        if (rawJson == null) {
            log.info("DeepSeek 未返回有效内容，商品块跳过意图抽取（块内 {} 行）", blockLines.size());
            return;
        }

        try {
            JsonNode node = objectMapper.readTree(rawJson);
            String ds = pickCanonical(textField(node, "designStyle", "design_style", "style"), styleSet);
            String ftRaw = pickCanonical(textField(node, "font", "fontName", "font_name"), fontSet);
            String ic = pickCanonical(textField(node, "icon", "Icon"), iconSet);
            String engFlat = textField(node, "engravingContent", "engraving_content", "刻录内容");
            if (engFlat != null) {
                engFlat = engFlat.trim();
            } else {
                engFlat = "";
            }

            String ftGlobal = StyleEngravingDefaultFontUtil.applyIfEligible(ftRaw, ds, engFlat,
                    rep.getPersonalization(), rep.getDynamicAttributes(), properties.isEnabled());
            ftGlobal = pickCanonical(ftGlobal, fontSet);

            List<ItemDetail> slotTargets = collectFaceFanOutSlotTargets(blockLines);
            String feOpt = textField(node, "frontEngravingContent", "front_engraving", "engravingFront");
            String beOpt = textField(node, "backEngravingContent", "back_engraving", "engravingBack");

            boolean dualFaceFilled = false;
            if (slotTargets.size() == 2 && fbParts.size() == 2) {
                dualFaceFilled = true;
                String rawFront = fbParts.get(0);
                String rawBack = fbParts.get(1);
                String e0 = !feOpt.isBlank() ? feOpt.trim() : slotPlainEngraving(rawFront);
                String e1 = !beOpt.isBlank() ? beOpt.trim() : slotPlainEngraving(rawBack);
                if (segmentLooksPhotoOnly(rawFront)) {
                    e0 = "";
                }
                String f0 = resolveFontForSegment(rawFront, ftGlobal, fontSet);
                String f1 = resolveFontForSegment(rawBack, ftGlobal, fontSet);
                // 勿拼接整块 dynamicAttributes：探头会先命中正面的 Font，背面误判成同一字体
                f0 = StyleEngravingDefaultFontUtil.applyIfEligible(f0, ds, e0, rawFront,
                        "", properties.isEnabled());
                f1 = StyleEngravingDefaultFontUtil.applyIfEligible(f1, ds, e1, rawBack,
                        "", properties.isEnabled());
                f0 = pickCanonical(f0, fontSet);
                f1 = pickCanonical(f1, fontSet);

                String dsFront = ds;
                if (segmentLooksPhotoOnly(rawFront) && !segmentLooksPhotoOnly(rawBack)) {
                    if (segmentLooksPetPhoto(rawFront)) {
                        String pet = pickCanonical("宠物头像(见附图)", styleSet);
                        if (!pet.isEmpty()) {
                            dsFront = pet;
                        }
                    } else {
                        String portrait = pickCanonical("人物头像(见附图)", styleSet);
                        if (!portrait.isEmpty()) {
                            dsFront = portrait;
                        }
                    }
                }

                ItemDetail s0 = slotTargets.get(0);
                ItemDetail s1 = slotTargets.get(1);
                s0.setLlmDesignStyle(dsFront);
                s0.setLlmFont(f0);
                s0.setLlmIcon(ic);
                s0.setLlmEngravingContent(e0);
                s1.setLlmDesignStyle(ds);
                s1.setLlmFont(f1);
                s1.setLlmIcon(ic);
                s1.setLlmEngravingContent(e1);
            }

            // 双面槽位已各自写 e0/e1 时，同块内其余行（附属/第二件等）仍用 engFlat；LLM 常只填
            // frontEngravingContent/backEngravingContent 而留空 engravingContent，故在 fbParts=2 时
            // 用 front/back 或原文片段合并填平 engFlat，避免同块后几行刻录列全空。
            if (fbParts.size() == 2 && (engFlat == null || engFlat.isBlank())) {
                String mergedFb = mergeFeBeFallbackFlatEng(feOpt, beOpt, fbParts);
                if (!mergedFb.isBlank()) {
                    engFlat = mergedFb;
                }
            }

            if (engFlat == null || engFlat.isBlank()) {
                String rawBackLine = extractTrailingBackLabelContent(rep.getPersonalization());
                if (!rawBackLine.isBlank()) {
                    String plain = slotPlainEngraving(rawBackLine);
                    engFlat = plain.isBlank() ? rawBackLine.trim() : plain;
                }
            }

            Set<ItemDetail> slotSet = dualFaceFilled ? new HashSet<>(slotTargets) : Collections.<ItemDetail>emptySet();
            for (ItemDetail item : blockLines) {
                if (dualFaceFilled && slotSet.contains(item)) {
                    continue;
                }
                item.setLlmDesignStyle(ds);
                item.setLlmFont(ftGlobal);
                item.setLlmIcon(ic);
                item.setLlmEngravingContent(engFlat);
            }
            log.info("Personalization DeepSeek 已写入商品块（{} 行，双面槽位={}）: designStyle=[{}] font=[{}] icon=[{}] engravingFlat 长度={}",
                    blockLines.size(), dualFaceFilled, ds, ftGlobal, ic, engFlat.length());
        } catch (Exception e) {
            log.warn("解析模型 JSON 失败，原始片段: {}", truncate(rawJson, 200), e);
        }
    }

    /**
     * 优先：主商品且数量&gt;0；否则第一条有个人信息的明细；再否则块首行。
     */
    private static ItemDetail pickRepresentativeForBlock(List<ItemDetail> blockLines) {
        for (ItemDetail d : blockLines) {
            if (Boolean.TRUE.equals(d.getMainProductFlg()) && d.getItemQuantity() > 0) {
                if (d.getPersonalization() != null && !d.getPersonalization().isBlank()) {
                    return d;
                }
            }
        }
        for (ItemDetail d : blockLines) {
            if (d.getPersonalization() != null && !d.getPersonalization().isBlank()) {
                return d;
            }
        }
        return blockLines.get(0);
    }

    private static List<ItemDetail> collectFaceFanOutSlotTargets(List<ItemDetail> blockLines) {
        List<ItemDetail> r = new ArrayList<>();
        for (ItemDetail d : blockLines) {
            if (!d.isEngravingFanOutApplied()) {
                continue;
            }
            String ex = d.getPersonalizationTextForLlm();
            if (ex != null && !ex.isBlank()) {
                r.add(d);
            }
        }
        return r;
    }

    /** 去掉槽位内的 Font/Style/icon 编号尾巴，得到纯刻录短语 */
    private static String slotPlainEngraving(String segment) {
        if (segment == null) {
            return "";
        }
        String s = segment.trim();
        s = s.replaceAll("(?is)\\s*[,，]?\\s*Font\\s*#?\\s*\\d+\\s*#?.*$", "");
        s = s.replaceAll("(?is)\\s*[,，]?\\s*Style\\s*#?\\s*\\d+.*$", "");
        s = s.replaceAll("(?is)\\s*[,，]?\\s*icon\\s*#?\\s*\\d+.*$", "");
        return s.trim();
    }

    private static boolean segmentLooksPetPhoto(String seg) {
        if (seg == null || seg.isBlank()) {
            return false;
        }
        String s = seg.toLowerCase();
        return s.contains("dog") || s.contains("cat") || s.contains("puppy") || s.contains("kitten")
                || s.contains("pet ") || s.startsWith("pet:") || seg.contains("宠物") || seg.contains("狗狗") || seg.contains("猫咪");
    }

    /**
     * 该槽位更像「附图/照片制图说明」而非可镌刻短语（短缩写如 TS/JB 不算）。
     */
    private static boolean segmentLooksPhotoOnly(String seg) {
        if (seg == null || seg.isBlank()) {
            return false;
        }
        String t = seg.trim();
        if (t.length() <= 4 && t.matches("(?is)[A-Z0-9.&]+")) {
            return false;
        }
        String s = t.toLowerCase();
        return s.contains("picture") || s.contains("photo") || s.contains("image") || s.contains("attached")
                || s.contains("附图") || s.contains("头像") || seg.contains("站姿")
                || Pattern.compile("(?i)standing\\s+picture").matcher(seg).find()
                || Pattern.compile("(?i)(picture|photo|image)\\s+in\\s+message").matcher(seg).find()
                || s.contains("snaggle") || s.contains("emphasize the");
    }

    /** 末行 {@code Back: …} 内容（仅背面刻录 listing 常用），取最后一处匹配。 */
    private static String extractTrailingBackLabelContent(String personalization) {
        if (personalization == null || personalization.isBlank()) {
            return "";
        }
        Matcher m = Pattern.compile("(?im)^\\s*Back:\\s*(.+)$").matcher(personalization);
        String last = null;
        while (m.find()) {
            last = m.group(1).trim();
        }
        return last != null ? last : "";
    }

    private static String mergeFeBeFallbackFlatEng(String feOpt, String beOpt, List<String> fbParts) {
        boolean fe = feOpt != null && !feOpt.isBlank();
        boolean be = beOpt != null && !beOpt.isBlank();
        if (fe && be) {
            return "Front: " + feOpt.trim() + "\nBack: " + beOpt.trim();
        }
        if (fe) {
            return feOpt.trim();
        }
        if (be) {
            return beOpt.trim();
        }
        if (fbParts == null || fbParts.size() != 2) {
            return "";
        }
        String e0 = slotPlainEngraving(fbParts.get(0));
        String e1 = slotPlainEngraving(fbParts.get(1));
        if (!e0.isEmpty() && !e1.isEmpty()) {
            return "Front: " + e0 + "\nBack: " + e1;
        }
        if (!e0.isEmpty()) {
            return e0;
        }
        if (!e1.isEmpty()) {
            return e1;
        }
        return "";
    }

    private String resolveFontForSegment(String segment, String globalFt, Set<String> fontSet) {
        if (segment == null || segment.isBlank()) {
            return pickCanonical(globalFt, fontSet);
        }
        String ext = PersonalizationParserUtil.extractLastFont(segment);
        if (ext == null || ext.isBlank()) {
            ext = PersonalizationParserUtil.extractFont(segment);
        }
        if (ext == null || ext.isBlank()) {
            return pickCanonical(globalFt, fontSet);
        }
        String std = fontNameMappingService.getStandardName(ext);
        String c = pickCanonical(std, fontSet);
        if (!c.isEmpty()) {
            return c;
        }
        return pickCanonical(ext, fontSet);
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
        sb.append("你是电商订单刻录意图抽取助手。**每次 API 调用处理同一 PDF Quantity 商品块**（主商品+附属+附加若干导出明细），块内 Personalization 相同；" +
                "输出 Style/Font/Icon 与刻录相关字段。若出现 **Front / Back / Bar（第二面）** 分区，**背面刻录与字体必须与正面区分**，禁止把背面内容丢弃或合并进正面。不得混入本订单其它商品块的留言。\n");
        sb.append("客户 Personalization 是自然语言留言，没有固定模板：可能出现缩写、口语、中英混写、换行随意、" +
                "同一含义多种说法（例如领带夹可能写作 tie clip / tieclip / clip / TC / 「夹」等）。你只能在语义上理解「客户想刻什么」，不要假设客户会按某个关键字才能解析。\n\n");
        sb.append("工作流程（在心中完成即可，不要输出过程）：\n");
        sb.append("1) **块内统一语义**：只解析本请求给出的 Personalization 与属性区；用户 prompt 会列出块内含哪些品类明细（主/附属），混写时采纳与主商品标题、属性一致的意图。\n");
        sb.append("2) 根据「刻录登记槽位说明」与「代表性导出行的品类」，锁定刻录意图（袖扣/领带夹等）；若用户 prompt 列出多块明细，仍以代表性行的品类为主锚点。\n");
        sb.append("3) 将编号映射到允许列表：Style n / Sn / S n → designStyle；Font n → font；icon → icon #n。领带夹行里形如 tie clip: S4(JB)、clip S44- AT 等，**必须把该行对应的 Style 写入 designStyle**，不得留空（若能映射）。**例外**：若命中下述「附图照片优先」则以该规则为准，不要求填写 Style n。\n");
        sb.append("4) **附图／照片／制图说明优先（极其重要）**：客户描述的若是「按附图/Message 里的图」「上传照片」「站姿图 standing picture」「强调五官细节（如 snaggle tooth、酒窝、眼神）」「宠物长相」等——意思是**让工坊参考图像来制图**，而不是提供要在产品上**镌刻的具体字母、单词或句子**：\n");
        sb.append("   • `engravingContent` **必须**填 `\"\"`。禁止把整句英文/中文制图说明（如 \"Standing picture in message. Please emphasize the snaggle tooth\"）当作刻录正文写入。\n");
        sb.append("   • `designStyle`：侧重**人物肖像／人像**（含 message 里站姿照、强调五官如虎牙 snaggle tooth、附图肖像）→ **`人物头像(见附图)`**；侧重**宠物**（dog/cat/pet/puppy/kitten/宠物/狗狗/猫咪）→ **`宠物头像(见附图)`**。若 **Front 槽位**为附图说明而 **Back 槽位**为文字刻录，Front 对应刻录为空且 Front 侧重人物时用 **`人物头像(见附图)`**（宠物侧重用 **`宠物头像(见附图)`**），Back 仍正常输出刻录句与 Font。\n");
        sb.append("   • `font`、`icon`：除非留言另有明确的 Font n / icon #n，否则填空 `\"\"`。双面时 **Back/Bar 面上的 Font n 只作用于该面**（例如 \"Bar: … Font 28\"）。\n");
        sb.append("5) **默认字体**：若留言中出现「默认字体」「默認字體」「default font」且未指定其它 Font 编号（无 Font n / font n / F n 等），font 必须填 \"Font 3(no)\"。\n");
        sb.append("6) **Style + 刻录但未写字体**：若已解析出含 Style 的设计风格且 engravingContent 非空，但留言中无任何字体编号，font 填 \"Font 3(no)\"。（附图类 engravingContent 为空时不强行填默认字体。）\n");
        sb.append("7) **订购数量与正文不一致**：若属性区 `Quantity:` 与客户文中的件数矛盾（例如正文写「all 4 clips」但 Quantity=5），**以属性区的 Quantity 为准**推断「多件／多套」语境，再解析每件对应的刻录；不得在 engravingContent 里死抠与客户自相矛盾的数字而放弃语义。\n");
        sb.append("8) 凡无法映射到允许列表的值填空字符串 \"\"。\n\n");
        sb.append("字段要求（输出仍是扁平 JSON，不要 markdown）：\n");
        sb.append("- designStyle: 必须从下列列表选一完全一致写法（含 `人物头像(见附图)`、`宠物头像(见附图)`）；无法判断填 \"\"。\n");
        sb.append("- font: 必须从下列列表选一完全一致写法；无法判断填 \"\"。\n");
        sb.append("- icon: 客户提及图标编号则映射为列表中的 \"icon #n\"；未提及填 \"\"。\n");
        sb.append("- engravingContent: **单面或无 Front/Back 分区时**填写刻录正文；**若存在 Front/Back(Bar) 分区**本字段可填合并摘要或仅一面，但 **frontEngravingContent / backEngravingContent 必须以双面为准**。\n");
        sb.append("- frontEngravingContent / backEngravingContent: **当且仅当** Personalization 出现 Front 与 Back 或 Bar 分区时 **必填**（可与 engravingContent 同时输出）；分别为正面、背面（Bar）**纯刻录文字**，不含 Font/Style/icon 标签。\n");
        sb.append("  • **普通句子类**：保留完整可读短语与标点，禁止无故缩短（勿把 Brother→Broth）、勿丢掉后半句（勿省略 thank you for everything）、勿擅自意译。\n");
        sb.append("  • **多件枚举／每件一物一字（重要）**：若出现 \"Initials for each are …\"、\"each … gets …\"、\"respectively\"、「每件」「分别刻」「依次为」等**仅为语法包装**，而真正刻录是其后**逗号或分行枚举的一段**，则 engravingContent **必须去掉此类英文/中文引导套话**，只保留**枚举本体**（例如客户写 CK, CK, CA, TT, and PC → 输出 `CK, CK, CA, TT, PC`，与客户字母顺序与标点风格保持一致）；**禁止**把整句 \"Initials for each are CK, …\" 当作最终刻录正文。\n");
        sb.append("  • **与导出分行对齐**：系统会将英文逗号/中文逗号/顿号分隔的枚举，按「片段数 = 订购数量×刻录面数（Excel 物理行数）」自动分配到各行。**枚举多件时不要加任何前缀或说明书式句子**（勿写「五件依次为」等），片段数量应与件数一致（若双面刻录且每件两面文案不同，则需两段×件数，少见；无法对齐时宁可输出一整段由人工拆）。\n");
        sb.append("  • 去掉已成功归入 designStyle/font/icon 的纯 Style／Font／icon 编号说明后的剩余内容归入本字段；确实无刻录字时再填 \"\"。\n\n");
        sb.append("示例（短字段仅为演示映射；真实订单刻录可能很长须完整输出）：\n");
        sb.append("- {\"designStyle\":\"Style 15\",\"font\":\"Font 3(no)\",\"icon\":\"icon #40\",\"engravingContent\":\"BG\"}\n");
        sb.append("- {\"designStyle\":\"Style 4\",\"font\":\"\",\"icon\":\"\",\"engravingContent\":\"JB\"}\n");
        sb.append("- {\"designStyle\":\"\",\"font\":\"Font 28\",\"icon\":\"\",\"engravingContent\":\"Dad, thank you for everything\"}\n");
        sb.append("- 多件领带夹 + Style 缩写 + 枚举缩写（Quantity 与文中 \"4 clips\" 矛盾时以 Quantity 为准）：\n");
        sb.append("  输入要点：Quantity: 5；Personalization 含 \"S7 style for all 4 clips. Initials for each are CK, CK, CA, TT, and PC\"\n");
        sb.append("  → {\"designStyle\":\"Style 7\",\"font\":\"Font 3(no)\",\"icon\":\"\",\"engravingContent\":\"CK, CK, CA, TT, PC\"}\n");
        sb.append("- 附图人像（无刻录字，勿把制图说明当刻录）：\n");
        sb.append("  Personalization: \"Standing picture in message. Please emphasize the snaggle tooth\"\n");
        sb.append("  → {\"designStyle\":\"人物头像(见附图)\",\"font\":\"\",\"icon\":\"\",\"engravingContent\":\"\"}\n");
        sb.append("- 双面英文（同行亦可）：\"Front: TS Back: Forever & Always\"\n");
        sb.append("  → {\"designStyle\":\"\",\"font\":\"\",\"icon\":\"\",\"engravingContent\":\"\",\"frontEngravingContent\":\"TS\",\"backEngravingContent\":\"Forever & Always\"}\n");
        sb.append("- 双面 Bar + 背面字体：Front 为附图说明 Back 为句子（示例）\n");
        sb.append("  \"Front: …picture… \\nBar: Forever your little girl, Font 28\"\n");
        sb.append("  → frontEngravingContent \"\"，backEngravingContent \"Forever your little girl\"，font 侧以 Font 28 为准（映射到允许列表），正面 designStyle 可用人物头像(见附图)\n\n");
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
     * @param personalizationPrimaryForModel 抽取主文本：路由收窄结果或全文 Personalization
     * @param blockPeers                     同块其它导出明细；null 或单行时不追加说明
     */
    private static String buildUserPrompt(ItemDetail item, String personalizationPrimaryForModel,
                                          List<ItemDetail> blockPeers, boolean structuredFrontBack) {
        StringBuilder sb = new StringBuilder();
        sb.append(PersonalizationLlmInputSanitizer.singleItemScopeBanner()).append('\n');
        if (structuredFrontBack) {
            sb.append("""
                    【双面结构】全文 Personalization 中已出现 Front（正面）与 Back 或 Bar（第二刻录面）分区。
                    你必须在 JSON 中同时给出 frontEngravingContent 与 backEngravingContent：
                    - 各字段只含该面上的「可镌刻正文」，去掉 Font n / Style n / icon #n 等工艺标签（工艺标签写入 font/designStyle/icon）。
                    - 附图/照片说明的一面刻录填 ""，designStyle 用人物或宠物头像(见附图) 规则仍适用。
                    - 禁止省略背面：不要把背面全文丢弃或合并进正面字段。
                    
                    """);
        }
        if (blockPeers != null && blockPeers.size() > 1) {
            sb.append("【商品块】下列导出明细共用同一 Personalization，本次 JSON 输出适用于所有这些明细：\n");
            int n = 1;
            for (ItemDetail p : blockPeers) {
                OrderType ot = p.getOrderType();
                String otLab = (ot != null && ot != OrderType.UNKNOWN)
                        ? ot.getDisplayName() + " / " + ot.getOrderTypeCode()
                        : "(未知品类)";
                sb.append(n++).append(") ").append(otLab);
                if (Boolean.TRUE.equals(p.getMainProductFlg())) {
                    sb.append("（主商品）");
                } else {
                    sb.append("（附属或其它导出明细）");
                }
                sb.append('\n');
            }
            sb.append('\n');
        }
        if (item.getLlmSlotInstruction() != null && !item.getLlmSlotInstruction().isBlank()) {
            sb.append("【刻录登记槽位说明】\n");
            sb.append(item.getLlmSlotInstruction().trim()).append("\n\n");
        }
        OrderType ot = item.getOrderType();
        if (ot != null && ot != OrderType.UNKNOWN) {
            sb.append("代表性导出行类型（锚点；不要被标题里出现的其它品类词误导）：\n");
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
