package com.pdfconverter.regression;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.regression.golden.ExcelGoldenSnapshot;
import com.pdfconverter.regression.golden.ExcelGoldenSnapshotIO;
import com.pdfconverter.service.PdfExtractorService;
import com.pdfconverter.service.export.ExcelWriterService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * P0 regression: parse real PDFs, export Excel, compare full-column snapshot.
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class RegressionExcelIntegrationTest {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    @Autowired
    private ExcelWriterService excelWriterService;

    static Stream<RegressionPdfCases.PdfCase> p0Cases() {
        return RegressionPdfCases.allP0().stream();
    }

    @ParameterizedTest
    @MethodSource("p0Cases")
    void exportExcel_matchesGoldenSnapshot(RegressionPdfCases.PdfCase pdfCase) throws Exception {
        Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "pdf-regression-out");
        cleanOutputDir(outDir);

        Path tempPdf = copyClasspathResourceToTemp(pdfCase.pdfClasspathPath());
        try {
            List<PdfOrderData> orders = pdfExtractorService.extractFromPdf(tempPdf.toAbsolutePath().toString());
            excelWriterService.writeOrders(orders);

            Path excel = findSingleExcelFile(outDir);
            ExcelGoldenSnapshot actual = ExcelGoldenSnapshot.fromExcelFile(pdfCase.caseId(), excel.toFile());
            ExcelGoldenSnapshot expected = ExcelGoldenSnapshotIO.readClasspath(
                    "/expected/excel/" + pdfCase.caseId() + ".excel.json");

            ExcelGoldenSnapshotIO.assertJsonEquals(expected, actual,
                    "Excel golden mismatch for case=" + pdfCase.caseId() + " pdf=" + pdfCase.pdfClasspathPath());
        } finally {
            Files.deleteIfExists(tempPdf);
        }
    }

    private static Path copyClasspathResourceToTemp(String classpathRelative) throws Exception {
        try (InputStream in = RegressionExcelIntegrationTest.class.getResourceAsStream(classpathRelative)) {
            assertNotNull(in, "Missing classpath resource: " + classpathRelative);
            Path p = Files.createTempFile("regression-excel-", ".pdf");
            Files.copy(in, p, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return p;
        }
    }

    private static void cleanOutputDir(Path outDir) throws Exception {
        Files.createDirectories(outDir);
        try (var stream = Files.list(outDir)) {
            stream.filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .forEach(p -> {
                        try {
                            Files.deleteIfExists(p);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    });
        }
    }

    private static Path findSingleExcelFile(Path outDir) throws Exception {
        try (var stream = Files.list(outDir)) {
            return stream
                    .filter(p -> p.getFileName().toString().endsWith(".xlsx"))
                    .max(Comparator.comparing(Path::toString))
                    .orElseThrow(() -> new IllegalStateException("No excel file generated under: " + outDir));
        }
    }
}
