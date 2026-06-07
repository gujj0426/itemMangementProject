package com.pdfconverter.service.core;

import com.pdfconverter.model.PdfOrderData;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * PDF 标注服务 — 统一入口。
 *
 * 所有 PDF 标注在此注册，检测逻辑统一在 PdfExtractorService.parseOrder() 计算标志，
 * 写入逻辑统一在此服务中完成。新增标注只需：
 *   1. PdfOrderData 加布尔字段 + getter/setter
 *   2. PdfExtractorService.parseOrder() 加一行判断设置该字段
 *   3. 本文件 MARKS 注册表加一行 MarkDefinition
 *
 * 使用方式（PdfProcessingScheduler Phase 3）：
 *   Map<String, Set<Integer>> marksPages = pdfMarkService.detectMarks(orders);
 *   pdfMarkService.markAll(pdf, marksPages);
 */
@Service
public class PdfMarkService {

    private static final Logger log = LoggerFactory.getLogger(PdfMarkService.class);

    // ── 标注注册表 ───────────────────────────────────────────────────────────────────
    /**
     * markId: 唯一标识，与 PdfOrderData 的 hasXxx 字段名对应（去掉 has 前缀，首字母小写）
     * text:    标注显示文字
     * testChar: 中文字体检测字符（选取标注中任意一个中文）
     * fontSize: AWT 渲染字号
     * color:   渲染颜色
     * needScan: 是否需要在 PDF 内容中二次验证（Style6 需要；其余仅靠 order 字段即可）
     */
    private static final List<MarkDefinition> MARKS = List.of(
        new MarkDefinition(
            "tieClipStyle6",
            "*领带夹Style 6 刻录时不加坐标点",
            '\u523B',    // 刻
            16f,
            Color.RED,
            true          // needScan = true：需要验证页面上真的有 TIE CLIP
        ),
        new MarkDefinition(
            "silentDogTagS",
            "*S号每行均分",
            '\u53F7',    // 号
            16f,
            Color.RED,
            false         // needScan = false：order 字段已足够，无需二次扫描
        )
    );

    // ── 标注定义 ─────────────────────────────────────────────────────────────────────
    public record MarkDefinition(
        String markId,       // 唯一标识
        String text,         // 标注文字
        char testChar,       // 中文字体检测字符
        float fontSize,      // AWT 渲染字号
        Color color,         // 渲染颜色
        boolean needScan     // 是否需要扫描 PDF 内容二次验证
    ) {}

    // ── 页码上下文 ───────────────────────────────────────────────────────────────────
    private static class PageContext {
        int pageIdx;
        float footerY = 0;
        float pageWidth = 612;
        PageContext(int pageIdx) { this.pageIdx = pageIdx; }
    }

    // ── 对外接口 ────────────────────────────────────────────────────────────────────

    /**
     * 从订单列表中检测需要标注的 markId → 页码集合。
     * 仅按 order 字段判断，needScan=false 的标注走此路径。
     *
     * @param orders 所有解析出的订单
     * @return markId → Set{页码0基}，仅含触发标注的项
     */
    public Map<String, Set<Integer>> detectMarks(List<PdfOrderData> orders) {
        Map<String, Set<Integer>> result = new LinkedHashMap<>();
        for (MarkDefinition def : MARKS) {
            result.put(def.markId(), new LinkedHashSet<>());
        }
        for (PdfOrderData order : orders) {
            for (MarkDefinition def : MARKS) {
                if (!def.needScan() && isMarkTriggered(order, def.markId())) {
                    // 页码范围在 PdfProcessingScheduler 中通过 extractFromPdf 传入，这里只记录触发
                    result.get(def.markId()).add(null); // 占位，由 scheduler 填充实际页码
                }
            }
        }
        // 清理空占位
        result.entrySet().removeIf(e -> e.getValue().isEmpty() ||
            e.getValue().stream().allMatch(Objects::isNull));
        return result;
    }

    /**
     * 写入所有标注到指定 PDF。
     *
     * @param pdfPath      PDF 文件路径
     * @param marksPages   markId → 页码集合（由 scheduler 从 extractFromPdf 收集）
     */
    public void markAll(String pdfPath, Map<String, Set<Integer>> marksPages) throws IOException {
        if (marksPages == null || marksPages.isEmpty()) return;

        // 按 markId 分组，每组渲染一次图片
        Map<String, BufferedImage> imagesByMark = new LinkedHashMap<>();
        for (MarkDefinition def : MARKS) {
            if (marksPages.containsKey(def.markId()) && !marksPages.get(def.markId()).isEmpty()) {
                imagesByMark.put(def.markId(), renderTextToImage(def.text(), def.fontSize(), def.color(), def.testChar()));
            }
        }
        if (imagesByMark.isEmpty()) return;

        log.info("开始标注 PDF: {}, 标注项: {}", pdfPath, marksPages.keySet());

        try (PDDocument document = PDDocument.load(new File(pdfPath))) {
            // 预扫描所有需要标注的页面（一次性加载，避免重复 open PDF）
            Set<Integer> allPages = marksPages.values().stream()
                .flatMap(Set::stream)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

            // 页码 → PageContext（只扫描 needScan=true 的页面）
            Map<Integer, PageContext> pageContexts = new LinkedHashMap<>();
            for (int pageIdx : allPages) {
                if (pageIdx >= 0 && pageIdx < document.getNumberOfPages()) {
                    PageContext ctx = new PageContext(pageIdx);
                    ctx.pageWidth = document.getPage(pageIdx).getMediaBox().getWidth();
                    ctx.footerY = findFooterY(document, pageIdx);
                    pageContexts.put(pageIdx, ctx);
                }
            }

            // 逐个 mark 写入
            for (Map.Entry<String, Set<Integer>> entry : marksPages.entrySet()) {
                String markId = entry.getKey();
                Set<Integer> pageSet = entry.getValue();
                if (pageSet == null || pageSet.isEmpty()) continue;

                MarkDefinition def = findDef(markId);
                if (def == null) {
                    log.warn("未找到 markId={} 的定义，跳过", markId);
                    continue;
                }

                BufferedImage img = imagesByMark.get(markId);
                if (img == null) continue;

                // needScan=true → 需要扫描验证；false → 直接写入
                if (def.needScan()) {
                    // Style 6：过滤出页面上确实有 TIE CLIP 的页
                    Set<Integer> verifiedPages = verifyPages(document, markId, pageSet);
                    writeMarkToPages(document, verifiedPages, img, pageContexts);
                    log.info("markId={} 标注完成，验证前={}, 验证后={}", markId, pageSet.size(), verifiedPages.size());
                } else {
                    // 静音狗牌 S：无需验证，直接写入
                    Set<Integer> filtered = pageSet.stream()
                        .filter(Objects::nonNull)
                        .collect(Collectors.toSet());
                    writeMarkToPages(document, filtered, img, pageContexts);
                    log.info("markId={} 标注完成，页数={}", markId, filtered.size());
                }
            }

            saveDocumentSafely(document, pdfPath);
        }
    }

    /**
     * 先写入同目录临时文件再替换目标，避免只读 PDF（如 {@code -r--r--r--}）原地 save 报 Permission denied。
     */
    private void saveDocumentSafely(PDDocument document, String pdfPath) throws IOException {
        File target = new File(pdfPath);
        File parent = target.getParentFile();
        if (parent != null && !parent.exists() && !parent.mkdirs()) {
            throw new IOException("无法创建 PDF 目录: " + parent.getAbsolutePath());
        }
        File temp = File.createTempFile("pdf-mark-", ".pdf", parent != null ? parent : target.getAbsoluteFile().getParentFile());
        try {
            document.save(temp);
            if (target.exists()) {
                if (!target.canWrite() && !target.setWritable(true)) {
                    Files.delete(target.toPath());
                }
            }
            Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            log.info("PDF 标注已保存: {}", pdfPath);
        } catch (IOException e) {
            if (temp.exists() && !temp.delete()) {
                log.warn("清理临时 PDF 失败: {}", temp.getAbsolutePath());
            }
            throw e;
        } finally {
            if (temp.exists()) {
                temp.delete();
            }
        }
    }

    // ── 检测逻辑 ────────────────────────────────────────────────────────────────────

    private MarkDefinition findDef(String markId) {
        return MARKS.stream().filter(d -> d.markId().equals(markId)).findFirst().orElse(null);
    }

    /**
     * 根据 PdfOrderData 字段判断标注是否触发。
     * 字段名约定：hasTieClipStyle6 → isTieClipStyle6()
     */
    private boolean isMarkTriggered(PdfOrderData order, String markId) {
        if (order == null) return false;
        switch (markId) {
            case "tieClipStyle6"  -> { return order.isHasTieClipStyle6(); }
            case "silentDogTagS"  -> { return order.isHasSilentDogTagS(); }
            default -> {
                // 通用反射：字段名驼峰转 getter
                String fieldName = toFieldName(markId);
                try {
                    var method = PdfOrderData.class.getMethod("is" + capitalize(fieldName));
                    return Boolean.TRUE.equals(method.invoke(order));
                } catch (Exception e) {
                    log.warn("无法通过反射调用 PdfOrderData.{}: {}", "is" + capitalize(fieldName), e.getMessage());
                    return false;
                }
            }
        }
    }

    private String toFieldName(String markId) {
        // silentDogTagS → SilentDogTagS → isSilentDogTagS()
        return markId.substring(0, 1).toUpperCase() + markId.substring(1);
    }

    private String capitalize(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase() + s.substring(1);
    }

    // ── 页脚定位 ────────────────────────────────────────────────────────────────────

    private float findFooterY(PDDocument document, int pageIdx) throws IOException {
        TextPositionCollector collector = new TextPositionCollector();
        collector.setStartPage(pageIdx + 1);
        collector.setEndPage(pageIdx + 1);
        collector.getText(document);
        // 拼接相邻 token 为"行"，以 Y 坐标差超过阈值判断换行
        // 直接逐 token 查：若某 token 的文字（单独或拼接前后 token）包含 "DO THE GREEN"
        List<Token> tokens = collector.getTokens();
        for (int i = 0; i < tokens.size(); i++) {
            // 尝试将当前及后面 3 个 token 拼接，覆盖 "Do the green thing" 被拆开的情况
            StringBuilder sb = new StringBuilder();
            for (int j = i; j < Math.min(i + 4, tokens.size()); j++) {
                sb.append(tokens.get(j).text).append(" ");
            }
            if (sb.toString().toUpperCase().contains("DO THE GREEN")) {
                return tokens.get(i).y;
            }
        }
        return 0;
    }

    /**
     * 验证 markId=style6：过滤出页面上确实有 TIE CLIP + Style6 关键词的页。
     * 验证 markId=其他（needScan=true）：子类可 override 此方法。
     */
    private Set<Integer> verifyPages(PDDocument document, String markId, Set<Integer> pages) throws IOException {
        if ("tieClipStyle6".equals(markId)) {
            return verifyStyle6Pages(document, pages);
        }
        return pages; // 默认：不做过滤
    }

    private static final Pattern STYLE_6_PATTERN = Pattern.compile("(?i)\\b(S6|Style\\s*6)\\b");

    private Set<Integer> verifyStyle6Pages(PDDocument document, Set<Integer> pageSet) throws IOException {
        Set<Integer> verified = new LinkedHashSet<>();
        for (int pageIdx : pageSet) {
            if (pageIdx < 0 || pageIdx >= document.getNumberOfPages()) continue;
            if (hasTieClipStyle6OnPage(document, pageIdx)) {
                verified.add(pageIdx);
            }
        }
        return verified;
    }

    private boolean hasTieClipStyle6OnPage(PDDocument document, int pageIdx) throws IOException {
        // 用 PDFTextStripper 直接提取整页文本（按行），避免 writeString 按词拆分导致 "TIE CLIP" 被拆成两个 token 的问题
        PDFTextStripper lineStripper = new PDFTextStripper();
        lineStripper.setStartPage(pageIdx + 1);
        lineStripper.setEndPage(pageIdx + 1);
        String pageText = lineStripper.getText(document);

        boolean inItem = false;
        boolean inPerso = false;
        StringBuilder fieldBuf = new StringBuilder();
        boolean foundTieClip = false;
        boolean foundStyle6 = false;

        for (String rawLine : pageText.split("\\r?\\n")) {
            String line = rawLine.trim();
            String upper = line.toUpperCase();

            // 进入领带夹商品块：标题行包含 TIE CLIP（且不是纯木盒行）
            if (!inItem && (upper.contains("TIE CLIP") || upper.contains("TIECLIP"))) {
                // 排除纯木盒行（如 "Wooden Case: Tie Clip Box" 类似情况，不太可能，保守保留）
                inItem = true;
                foundTieClip = true;
                continue;
            }

            if (!inItem) continue;

            // Quantity: 开始下一个商品，重置 inPerso
            if (upper.startsWith("QUANTITY:")) {
                // 如果 inPerso 还没结束，做最后一次 Style6 检查
                if (inPerso && STYLE_6_PATTERN.matcher(fieldBuf).find()) foundStyle6 = true;
                inPerso = false;
                fieldBuf.setLength(0);
                continue;
            }

            if (upper.startsWith("PERSONALIZATION:") || upper.startsWith("DESIGN OPTIONS:")) {
                inPerso = true;
                fieldBuf.setLength(0);
                String val = line.contains(":") ? line.substring(line.indexOf(':') + 1).trim() : "";
                fieldBuf.append(val).append("\n");
                if (STYLE_6_PATTERN.matcher(val).find()) foundStyle6 = true;
                continue;
            }

            if (inPerso) {
                // 遇到新属性行（非空且以属性关键词开头）→ 结束 personalization 收集并检查
                if (!line.isEmpty() && isNewProperty(line)) {
                    if (STYLE_6_PATTERN.matcher(fieldBuf).find()) foundStyle6 = true;
                    inPerso = false;
                    fieldBuf.setLength(0);
                } else {
                    // 继续累积（含空行，保留 sub-field 如 "Front style: S6"）
                    fieldBuf.append(line).append("\n");
                }
            }

            // 页脚标志，退出商品块
            if (upper.startsWith("DO THE GREEN") || upper.startsWith("REUSE THIS")) {
                // 最后一次检查未关闭的 inPerso
                if (inPerso && STYLE_6_PATTERN.matcher(fieldBuf).find()) foundStyle6 = true;
                inItem = false;
            }
        }
        // 扫描结束后再做一次兜底检查
        if (inPerso && STYLE_6_PATTERN.matcher(fieldBuf).find()) foundStyle6 = true;

        log.debug("hasTieClipStyle6 page={}: foundTieClip={}, foundStyle6={}", pageIdx + 1, foundTieClip, foundStyle6);
        return foundTieClip && foundStyle6;
    }

    private boolean isNewProperty(String line) {
        String upper = line.toUpperCase().trim();
        return upper.startsWith("QUANTITY:") || upper.startsWith("COLOR") ||
               upper.startsWith("SIZE") || upper.startsWith("SHOP") ||
               upper.startsWith("ORDER DATE") || upper.startsWith("PAYMENT") ||
               upper.startsWith("ORDER #") || upper.startsWith("SHIP TO") ||
               upper.startsWith("FONT") || upper.startsWith("STYLE") ||
               upper.startsWith("DESIGN") || upper.startsWith("ITEM OPTIONS") ||
               upper.startsWith("ENGRAVING") || upper.startsWith("GIFT") ||
               upper.startsWith("WOODBOX") || upper.startsWith("ENGRAVING SIDES");
    }

    // ── 写入 ────────────────────────────────────────────────────────────────────────

    private static final float MARK_LINE_GAP = 30f; // 标注与页脚的间距（PDF 坐标）
    private static final int RENDER_PADDING = 16;

    private void writeMarkToPages(PDDocument document, Set<Integer> pages,
                                  BufferedImage img, Map<Integer, PageContext> pageContexts) throws IOException {
        float imgW = (float) img.getWidth();
        float imgH = (float) img.getHeight();

        for (int pageIdx : pages) {
            PageContext ctx = pageContexts.get(pageIdx);
            if (ctx == null) continue;

            PDPage page = document.getPage(pageIdx);
            float pageHeight = page.getMediaBox().getHeight();

            float markY = ctx.footerY > 0
                    ? ctx.footerY - MARK_LINE_GAP
                    : pageHeight - 100;
            float x = (ctx.pageWidth - imgW) / 2;

            PDImageXObject pdImage = LosslessFactory.createFromImage(document, img);
            try (PDPageContentStream cs = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.APPEND, true, true)) {
                cs.drawImage(pdImage, x, markY, imgW, imgH);
            }

            log.debug("  页面 {} 标注完成，markY={}", pageIdx + 1, markY);
        }
    }

    // ── AWT 图片渲染 ────────────────────────────────────────────────────────────────

    private BufferedImage renderTextToImage(String text, float fontSize, Color color, char testChar) {
        Font font = findChineseFont(testChar).deriveFont(Font.BOLD, fontSize);

        BufferedImage img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = img.createGraphics();
        g2.setFont(font);
        FontMetrics fm = g2.getFontMetrics();
        Rectangle2D bounds = fm.getStringBounds(text, g2);
        g2.dispose();

        int width  = (int) Math.ceil(bounds.getWidth())  + RENDER_PADDING * 2;
        int height = (int) Math.ceil(bounds.getHeight()) + RENDER_PADDING * 2;

        BufferedImage textImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2d = textImage.createGraphics();
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2d.setBackground(new Color(0, 0, 0, 0));
        g2d.clearRect(0, 0, width, height);
        g2d.setFont(font);
        g2d.setColor(color);
        float textX = RENDER_PADDING - (float) bounds.getX();
        float textY = RENDER_PADDING - (float) bounds.getY();
        g2d.drawString(text, textX, textY);
        g2d.dispose();

        log.debug("AWT 渲染标注: \"{}\", 尺寸 {}x{}", text, width, height);
        return textImage;
    }

    private Font findChineseFont(char testChar) {
        String[] preferred = {
            "PingFang SC", "PingFang TC", "STSong", "STSongti-SC-Regular",
            "Apple SD Gothic Neo",
            "Microsoft YaHei", "SimHei", "SimSun",
        };
        GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (String name : preferred) {
            for (Font f : ge.getAllFonts()) {
                if (f.getFontName().equals(name) && f.canDisplay(testChar)) {
                    log.info("AWT 选中字体: {}", f.getFontName());
                    return f;
                }
            }
        }
        for (Font f : ge.getAllFonts()) {
            if (f.canDisplay(testChar)) {
                log.info("AWT 回退字体: {}", f.getFontName());
                return f;
            }
        }
        log.warn("AWT 未找到中文字体（检测字符 \\u{:04x}），使用 Dialog logical font", (int) testChar);
        return new Font("Dialog", Font.PLAIN, 16);
    }

    // ── 内部类 ─────────────────────────────────────────────────────────────────────

    private static class TextPositionCollector extends PDFTextStripper {
        private final List<Token> tokens = new ArrayList<>();
        TextPositionCollector() throws IOException { super(); }

        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (text == null || text.trim().isEmpty() || positions.isEmpty()) return;
            TextPosition first = positions.get(0);
            tokens.add(new Token(text, first.getX(), first.getY()));
        }

        List<Token> getTokens() { return tokens; }
    }

    private static class Token {
        final String text;
        final float x, y;
        Token(String text, float x, float y) {
            this.text = text; this.x = x; this.y = y;
        }
    }
}
