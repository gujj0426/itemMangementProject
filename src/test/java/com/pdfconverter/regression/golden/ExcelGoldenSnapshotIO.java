package com.pdfconverter.regression.golden;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Load/write/compare Excel golden snapshots.
 */
public final class ExcelGoldenSnapshotIO {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT)
            .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);

    private ExcelGoldenSnapshotIO() {}

    public static ExcelGoldenSnapshot readClasspath(String classpathRelative) throws Exception {
        try (InputStream in = ExcelGoldenSnapshotIO.class.getResourceAsStream(classpathRelative)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + classpathRelative);
            }
            return MAPPER.readValue(in, ExcelGoldenSnapshot.class);
        }
    }

    public static void writeProjectGolden(String caseId, ExcelGoldenSnapshot snap) throws Exception {
        Path out = Path.of("src/test/resources/expected/excel/" + caseId + ".excel.json");
        Files.createDirectories(out.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), snap);
    }

    public static void assertJsonEquals(ExcelGoldenSnapshot expected, ExcelGoldenSnapshot actual, String context) throws Exception {
        JsonNode exp = MAPPER.valueToTree(expected);
        JsonNode act = MAPPER.valueToTree(actual);
        if (!exp.equals(act)) {
            String msg = context + "\n--- expected ---\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(exp)
                    + "\n--- actual ---\n" + MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(act);
            throw new AssertionError(msg);
        }
    }
}
