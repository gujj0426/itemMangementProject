package com.pdfconverter.util;

/**
 * 根据订购完全信息 / 动态属性文本推断刻录面数（与 Excel 导出逻辑保持一致）。
 */
public final class EngravingFaceCountUtil {

    private EngravingFaceCountUtil() {
    }

    /**
     * @param dynamicAttributesOrFullOrderInfo 通常为 ItemDetail.dynamicAttributes（含属性 + Personalization）
     * @return 刻录面数，至少为 1，最多为 5
     */
    public static int countFaces(String dynamicAttributesOrFullOrderInfo) {
        String info = dynamicAttributesOrFullOrderInfo;
        if (info == null || info.isEmpty()) {
            return 1;
        }

        String lower = info.toLowerCase();

        java.util.regex.Matcher petMatcher = java.util.regex.Pattern.compile(
                        "customization option[^\\n]*?(\\d+)\\s*side", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(lower);
        if (petMatcher.find()) {
            int sides = Integer.parseInt(petMatcher.group(1));
            return Math.min(Math.max(sides, 1), 5);
        }

        java.util.regex.Matcher engravingOptionLine = java.util.regex.Pattern.compile(
                        "engraving\\s*options?\\s*:\\s*([^\\n\\r]+)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(info);
        if (engravingOptionLine.find()) {
            String optionValue = engravingOptionLine.group(1).trim().toLowerCase();
            if ((optionValue.contains("front") && optionValue.contains("back"))
                    || (optionValue.contains("round disc") && optionValue.contains("bar"))
                    || optionValue.contains("double-side")
                    || optionValue.contains("double side")
                    || (optionValue.contains("lid") && optionValue.contains("body"))) {
                return 2;
            }
            int ampersandCount = 0;
            for (char c : optionValue.toCharArray()) {
                if (c == '&') {
                    ampersandCount++;
                }
            }
            return Math.min(Math.max(ampersandCount + 1, 1), 5);
        }

        boolean hasEngravingSides = lower.contains("engraving sides")
                || lower.contains("engraving option")
                || lower.contains("engraving:");
        boolean isFrontBack = lower.contains("front") && lower.contains("back");
        boolean isRoundDiscBar = lower.contains("round disc") && lower.contains("bar");
        boolean isDoubleSide = lower.contains("double-side") || lower.contains("double side");
        boolean isLidBody = lower.contains("lid") && lower.contains("body");

        if (hasEngravingSides && (isFrontBack || isRoundDiscBar || isDoubleSide || isLidBody)) {
            return 2;
        }

        return 1;
    }
}
