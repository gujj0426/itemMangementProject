package com.pdfconverter.config;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuyerMessageAnchorConfigTest {

    @Test
    void compile_matchesConfiguredLabelAtLineStart() {
        List<java.util.regex.Pattern> patterns = BuyerMessageAnchorConfig.compilePatterns(
                List.of("Custom Engraving Text"),
                List.of());
        String block = "Quantity: 1\nCustom Engraving Text: Hello\n";
        boolean found = false;
        for (java.util.regex.Pattern p : patterns) {
            if (p.matcher(block).find()) {
                found = true;
                break;
            }
        }
        assertTrue(found);
    }

    @Test
    void lineStartsWithLabel_distinguishesEngravingFromEngravingSides() {
        assertTrue(BuyerMessageAnchorConfig.lineStartsWithLabel("Engraving: ABC", "Engraving"));
        assertFalse(BuyerMessageAnchorConfig.lineStartsWithLabel("Engraving Sides: Front & Back", "Engraving"));
    }

    @Test
    void load_readsExternalJsonFile() throws Exception {
        Path tmp = Files.createTempFile("buyer-message-anchor-labels", ".json");
        Files.writeString(tmp, """
                {
                  "buyerMessageLabels": ["Shop Note", "Personalization"],
                  "excludePrefixes": []
                }
                """);
        BuyerMessageAnchorConfig config = new BuyerMessageAnchorConfig();
        setField(config, "configFile", tmp.toAbsolutePath().toString());
        config.load();
        assertEquals(List.of("Shop Note", "Personalization"), config.getBuyerMessageLabels());
        assertTrue(config.lineStartsWithAnchorLabel("Shop Note: please engrave JRS"));
    }

    private static void setField(Object target, String name, String value) throws Exception {
        var f = BuyerMessageAnchorConfig.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(target, value);
    }
}
