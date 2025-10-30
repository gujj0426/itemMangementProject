package com.pdfconverter.util;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class PersonalizationParser {

    public static String extractFont(String personalization) {
        if (personalization == null || personalization.isEmpty()) return "";
        Pattern pattern = Pattern.compile("Font\\s*(\\d+)", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(personalization);
        return matcher.find() ? "Font " + matcher.group(1) : "";
    }
}