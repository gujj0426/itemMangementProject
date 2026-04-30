package com.pdfconverter.regression;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.regression.golden.ExcelGoldenSnapshot;
import com.pdfconverter.regression.golden.ExcelGoldenSnapshotIO;
import com.pdfconverter.service.PdfExtractorService;
import com.pdfconverter.service.export.ExcelWriterService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

/**
 * Run manually to regenerate {@code src/test/resources/expected/excel/*.excel.json}:
 * <pre>
 *   mvn -q -Dtest=GoldenExcelSnapshotGeneratorTest -DregenerateExcelGolden=true test
 * </pre>
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class GoldenExcelSnapshotGeneratorTest {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    @Autowired
    private ExcelWriterService excelWriterService;

    @Test
    @EnabledIfSystemProperty(named = "regenerateExcelGolden", matches = "true")
    void regenerateGoldenExcelSnapshots() throws Exception {
        Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "pdf-regression-out");
        for (RegressionPdfCases.PdfCase c : RegressionPdfCases.allP0()) {
            cleanOutputDir(outDir);
            Path tempPdf = copyClasspathResourceToTemp(c.pdfClasspathPath());
            try {
                List<PdfOrderData> orders = pdfExtractorService.extractFromPdf(tempPdf.toAbsolutePath().toString());
                excelWriterService.writeOrders(orders);
                Path excel = findSingleExcelFile(outDir);
                ExcelGoldenSnapshot snap = ExcelGoldenSnapshot.fromExcelFile(c.caseId(), excel.toFile());
                ExcelGoldenSnapshotIO.writeProjectGolden(c.caseId(), snap);
            } finally {
                Files.deleteIfExists(tempPdf);
            }
        }
    }

    private static Path copyClasspathResourceToTemp(String classpathRelative) throws Exception {
        try (InputStream in = GoldenExcelSnapshotGeneratorTest.class.getResourceAsStream(classpathRelative)) {
            if (in == null) {
                throw new IllegalArgumentException("Missing classpath resource: " + classpathRelative);
            }
            Path p = Files.createTempFile("golden-excel-", ".pdf");
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
