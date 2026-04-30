package com.pdfconverter.regression;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Iteration A baseline smoke test:
 * validates the regression sample directory skeleton exists.
 */
class RegressionBaselineSmokeTest {

    @Test
    void regressionSampleDirectoriesShouldExist() {
        assertTrue(Files.isDirectory(Path.of("src/test/resources/samples/pdf")));
        assertTrue(Files.isDirectory(Path.of("src/test/resources/expected/orders")));
        assertTrue(Files.isDirectory(Path.of("src/test/resources/expected/excel")));
    }
}
