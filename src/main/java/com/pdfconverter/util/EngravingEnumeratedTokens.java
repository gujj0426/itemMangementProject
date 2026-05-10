package com.pdfconverter.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 将 LLM 返回的「多件依次枚举」式刻录（逗号/顿号分隔）映射到 Excel 多物理行：一行一件。
 */
public final class EngravingEnumeratedTokens {

    private static final Pattern ENUM_SPLIT = Pattern.compile("[,，、]\\s*");

    private EngravingEnumeratedTokens() {
    }

    /**
     * 按分隔符拆成片段；去掉英文枚举尾部常见的 {@code and } 前缀（如 {@code and PC}）。
     */
    public static List<String> splitEnumeratedTokens(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        String t = raw.trim();
        List<String> parts = new ArrayList<>();
        for (String seg : ENUM_SPLIT.split(t)) {
            String x = seg.trim();
            if (x.isEmpty()) {
                continue;
            }
            if (x.regionMatches(true, 0, "and ", 0, 4)) {
                x = x.substring(4).trim();
            }
            if (!x.isEmpty()) {
                parts.add(x);
            }
        }
        return parts;
    }

    /**
     * @param pieceIndex0 当前 Excel 物理行下标（0..totalPieces-1）
     * @param totalPieces 本 {@link com.pdfconverter.model.PdfOrderData.ItemDetail} 展开的总行数（件数×面数等）
     * @return 与该行对应的刻录片段；无法与行数对齐时退回整段原文，避免误拆长句
     */
    public static String engravingForPiece(String llmEngraving, int pieceIndex0, int totalPieces) {
        if (llmEngraving == null || llmEngraving.isBlank()) {
            return "";
        }
        String full = llmEngraving.trim();
        if (totalPieces <= 1) {
            return full;
        }
        int idx = pieceIndex0;
        if (idx < 0) {
            idx = 0;
        } else if (idx >= totalPieces) {
            idx = totalPieces - 1;
        }
        List<String> tokens = splitEnumeratedTokens(full);
        if (tokens.isEmpty()) {
            return full;
        }
        if (tokens.size() == totalPieces) {
            return tokens.get(idx).trim();
        }
        if (tokens.size() == 1) {
            return tokens.get(0).trim();
        }
        return full;
    }
}
