package com.pdfconverter.regression.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Load/write/compare golden snapshots as canonical JSON (sorted keys, stable formatting).
 */
public final class GoldenSnapshotIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private GoldenSnapshotIO() {}

    public static OrderGoldenSnapshot readClasspath(String classpathRelative) throws Exception {
        try (InputStream in = GoldenSnapshotIO.class.getResourceAsStream(classpathRelative)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + classpathRelative);
            }
            return MAPPER.readValue(in, OrderGoldenSnapshot.class);
        }
    }

    public static String serialize(OrderGoldenSnapshot snap) throws Exception {
        JsonNode tree = MAPPER.valueToTree(snap);
        return MAPPER.writeValueAsString(tree);
    }

    public static void assertJsonEquals(OrderGoldenSnapshot expected, OrderGoldenSnapshot actual, String context) throws Exception {
        JsonNode exp = MAPPER.valueToTree(expected);
        JsonNode act = MAPPER.valueToTree(actual);
        if (!exp.equals(act)) {
            String msg = context + "\n--- expected ---\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(exp)
                    + "\n--- actual ---\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(act);
            throw new AssertionError(msg);
        }
    }

    /** Optional: write regenerated golden to project path (run from module root). */
    public static void writeProjectGolden(String caseId, OrderGoldenSnapshot snap) throws Exception {
        Path out = Path.of("src/test/resources/expected/orders/" + caseId + ".orders.json");
        Files.createDirectories(out.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), snap);
    }
}
