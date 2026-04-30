package com.pdfconverter.regression;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.regression.golden.GoldenSnapshotIO;
import com.pdfconverter.regression.golden.OrderGoldenSnapshot;
import com.pdfconverter.service.PdfExtractorService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P0 regression: parse real PDFs and compare with golden JSON under {@code classpath:/expected/orders/}.
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class RegressionPdfIntegrationTest {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    static Stream<RegressionPdfCases.PdfCase> p0Cases() {
        return RegressionPdfCases.allP0().stream();
    }

    @ParameterizedTest
    @MethodSource("p0Cases")
    void extractOrders_matchesGoldenSnapshot(RegressionPdfCases.PdfCase pdfCase) throws Exception {
        Path tempPdf = copyClasspathResourceToTemp(pdfCase.pdfClasspathPath());
        try {
            List<PdfOrderData> orders = pdfExtractorService.extractFromPdf(tempPdf.toAbsolutePath().toString());
            assertNotNull(orders);
            assertFalse(orders.isEmpty());

            OrderGoldenSnapshot actual = OrderGoldenSnapshot.from(pdfCase.caseId(), orders);
            OrderGoldenSnapshot expected = GoldenSnapshotIO.readClasspath(
                    "/expected/orders/" + pdfCase.caseId() + ".orders.json");
            GoldenSnapshotIO.assertJsonEquals(expected, actual,
                    "Golden mismatch for case=" + pdfCase.caseId() + " pdf=" + pdfCase.pdfClasspathPath());
        } finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    private static Path copyClasspathResourceToTemp(String classpathRelative) throws Exception {
        try (InputStream in = RegressionPdfIntegrationTest.class.getResourceAsStream(classpathRelative)) {
            assertNotNull(in, "Missing classpath resource: " + classpathRelative);
            Path p = Files.createTempFile("regression-", ".pdf");
            Files.copy(in, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return p;
        }
    }
}
