package com.pdfconverter.regression;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.regression.golden.GoldenSnapshotIO;
import com.pdfconverter.regression.golden.OrderGoldenSnapshot;
import com.pdfconverter.service.PdfExtractorService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Run manually to regenerate {@code src/test/resources/expected/orders/*.orders.json}:
 * <pre>
 *   mvn -q -Dtest=GoldenSnapshotGeneratorTest -DregenerateGolden=true test
 * </pre>
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class GoldenSnapshotGeneratorTest {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    @Test
    @EnabledIfSystemProperty(named = "regenerateGolden", matches = "true")
    void regenerateGoldenOrderSnapshots() throws Exception {
        for (RegressionPdfCases.PdfCase c : RegressionPdfCases.allP0()) {
            Path tempPdf = copyClasspathResourceToTemp(c.pdfClasspathPath());
            try {
                List<PdfOrderData> orders = pdfExtractorService.extractFromPdf(tempPdf.toAbsolutePath().toString());
                OrderGoldenSnapshot snap = OrderGoldenSnapshot.from(c.caseId(), orders);
                GoldenSnapshotIO.writeProjectGolden(c.caseId(), snap);
            } finally {
                Files.deleteIfExists(tempPdf);
            }
        }
    }

    private static Path copyClasspathResourceToTemp(String classpathRelative) throws Exception {
        try (InputStream in = GoldenSnapshotGeneratorTest.class.getResourceAsStream(classpathRelative)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + classpathRelative);
            }
            Path p = Files.createTempFile("golden-", ".pdf");
            Files.copy(in, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return p;
        }
    }
}
