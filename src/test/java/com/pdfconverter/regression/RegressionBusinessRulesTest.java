package com.pdfconverter.regression;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.regression.golden.ExcelGoldenSnapshot;
import com.pdfconverter.service.PdfExtractorService;
import com.pdfconverter.service.export.ExcelWriterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Config-driven business rule assertions on top of parser + excel output.
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class RegressionBusinessRulesTest {

    @Autowired
    private PdfExtractorService pdfExtractorService;

    @Autowired
    private ExcelWriterService excelWriterService;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void businessRules_shouldMatchConfiguredAssertions() throws Exception {
        BusinessRuleConfig cfg = loadConfig("/regression/business-assertions.json");
        Path outDir = Path.of(System.getProperty("java.io.tmpdir"), "pdf-regression-out");

        for (BusinessRuleGroup group : cfg.groups) {
            cleanOutputDir(outDir);
            Path tempPdf = copyClasspathResourceToTemp(group.pdfClasspathPath);
            try {
                List<PdfOrderData> orders = pdfExtractorService.extractFromPdf(tempPdf.toAbsolutePath().toString());
                excelWriterService.writeOrders(orders);

                Path excel = findSingleExcelFile(outDir);
                ExcelGoldenSnapshot excelSnap = ExcelGoldenSnapshot.fromExcelFile(group.caseId, excel.toFile());
                Map<String, Integer> headerIndex = buildHeaderIndex(excelSnap.headers);
                Map<String, PdfOrderData> orderMap = buildOrderMap(orders);

                for (BusinessAssertion a : group.assertions) {
                    evalAssertion(group, a, orderMap, excelSnap, headerIndex);
                }
            } finally {
                Files.deleteIfExists(tempPdf);
            }
        }
    }

    private static BusinessRuleConfig loadConfig(String classpathPath) throws Exception {
        try (InputStream in = RegressionBusinessRulesTest.class.getResourceAsStream(classpathPath)) {
            assertNotNull(in, "Missing config resource: " + classpathPath);
            return MAPPER.readValue(in, BusinessRuleConfig.class);
        }
    }

    private static void evalAssertion(BusinessRuleGroup group, BusinessAssertion a,
                                      Map<String, PdfOrderData> orderMap,
                                      ExcelGoldenSnapshot excelSnap,
                                      Map<String, Integer> headerIndex) {
        String ctx = "[group=" + group.groupId + ", case=" + group.caseId + ", type=" + a.type + ", order=" + a.orderNumber + "]";
        switch (a.type) {
            case "order_total_item_quantity": {
                PdfOrderData order = orderMap.get(a.orderNumber);
                assertNotNull(order, ctx + " order not found");
                assertEquals(a.expectedInt, order.getTotalItemQuantity(), ctx + " totalItemQuantity mismatch");
                return;
            }
            case "excel_order_row_count": {
                int cnt = countOrderRows(excelSnap, headerIndex, a.orderNumber);
                assertEquals(a.expectedInt, cnt, ctx + " excel order row count mismatch");
                return;
            }
            case "excel_order_row_count_min": {
                int cnt = countOrderRows(excelSnap, headerIndex, a.orderNumber);
                assertTrue(cnt >= a.expectedInt, ctx + " excel order row count below min");
                return;
            }
            case "excel_rows_match": {
                List<List<String>> rows = filterRows(excelSnap, headerIndex, a.orderNumber, a.productName,
                        a.productVariable);
                assertEquals(a.expectedInt, rows.size(), ctx + " product row count mismatch");
                if (a.requiredColumns != null) {
                    for (List<String> row : rows) {
                        for (Map.Entry<String, String> e : a.requiredColumns.entrySet()) {
                            Integer idx = headerIndex.get(e.getKey());
                            assertNotNull(idx, ctx + " missing header: " + e.getKey());
                            assertEquals(e.getValue(), row.get(idx), ctx + " column mismatch for " + e.getKey());
                        }
                    }
                }
                return;
            }
            case "excel_quantity_distribution": {
                List<List<String>> rows = filterRows(excelSnap, headerIndex, a.orderNumber, a.productName,
                        a.productVariable);
                Integer qtyIdx = headerIndex.get("数量");
                assertNotNull(qtyIdx, ctx + " missing 数量 header");
                int filled = 0;
                int empty = 0;
                for (List<String> row : rows) {
                    String q = row.get(qtyIdx);
                    if (q == null || q.isEmpty()) {
                        empty++;
                    } else {
                        filled++;
                    }
                }
                assertEquals(a.expectedFilledQtyRows, filled, ctx + " filled quantity rows mismatch");
                assertEquals(a.expectedEmptyQtyRows, empty, ctx + " empty quantity rows mismatch");
                return;
            }
            default:
                throw new IllegalArgumentException(ctx + " unsupported assertion type: " + a.type);
        }
    }

    private static int countOrderRows(ExcelGoldenSnapshot snap, Map<String, Integer> idx, String orderNumber) {
        Integer orderIdx = idx.get("订单编号");
        if (orderIdx == null) return 0;
        int count = 0;
        for (List<String> row : snap.rows) {
            if (Objects.equals(orderNumber, row.get(orderIdx))) {
                count++;
            }
        }
        return count;
    }

    private static List<List<String>> filterRows(ExcelGoldenSnapshot snap, Map<String, Integer> idx,
                                                 String orderNumber, String productName,
                                                 String productVariableEquals) {
        Integer orderIdx = idx.get("订单编号");
        Integer productIdx = idx.get("产品名称");
        Integer variableIdx = idx.get("产品变量");
        List<List<String>> out = new ArrayList<>();
        for (List<String> row : snap.rows) {
            boolean okOrder = orderIdx != null && Objects.equals(orderNumber, row.get(orderIdx));
            boolean okProduct = productIdx != null && Objects.equals(productName, row.get(productIdx));
            boolean okVar = productVariableEquals == null || productVariableEquals.isBlank()
                    || (variableIdx != null && Objects.equals(productVariableEquals, row.get(variableIdx)));
            if (okOrder && okProduct && okVar) {
                out.add(row);
            }
        }
        return out;
    }

    private static Map<String, Integer> buildHeaderIndex(List<String> headers) {
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < headers.size(); i++) {
            map.put(headers.get(i), i);
        }
        return map;
    }

    private static Map<String, PdfOrderData> buildOrderMap(List<PdfOrderData> orders) {
        Map<String, PdfOrderData> map = new HashMap<>();
        for (PdfOrderData o : orders) {
            map.put(o.getOrderNumber(), o);
        }
        return map;
    }

    private static Path copyClasspathResourceToTemp(String classpathRelative) throws Exception {
        try (InputStream in = RegressionBusinessRulesTest.class.getResourceAsStream(classpathRelative)) {
            assertNotNull(in, "Missing classpath resource: " + classpathRelative);
            Path p = Files.createTempFile("regression-business-", ".pdf");
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

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BusinessRuleConfig {
        public List<BusinessRuleGroup> groups = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BusinessRuleGroup {
        public String groupId;
        public String caseId;
        public String pdfClasspathPath;
        public List<BusinessAssertion> assertions = new ArrayList<>();
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    static class BusinessAssertion {
        public String type;
        public String orderNumber;
        public String productName;
        /** 非空时仅保留「产品变量」列等于该值的行（用于区分同一订单多种包装盒） */
        public String productVariable;
        public Integer expectedInt;
        public Integer expectedFilledQtyRows;
        public Integer expectedEmptyQtyRows;
        public Map<String, String> requiredColumns;
    }
}
