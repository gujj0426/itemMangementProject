package com.pdfconverter.regression;

import java.util.Arrays;
import java.util.List;

/**
 * Shared P0 PDF regression cases (classpath PDF + case id for golden files).
 */
final class RegressionPdfCases {

    record PdfCase(String caseId, String pdfClasspathPath) {}

    private RegressionPdfCases() {}

    static List<PdfCase> allP0() {
        return Arrays.asList(
                new PdfCase("baseline-3837329233", "/samples/pdf/baseline-3837329233.pdf"),
                new PdfCase("dogtag-batch-5-20260311", "/samples/pdf/dogtag-batch-5-20260311.pdf"),
                new PdfCase("urn-1-20260328", "/samples/pdf/urn-1-20260328.pdf"),
                new PdfCase("urn-1-20260412", "/samples/pdf/urn-1-20260412.pdf"),
                new PdfCase("cufflink-6-04062026-marked", "/samples/pdf/cufflink-6-04062026-marked.pdf"),
                new PdfCase("cufflink-9-03252026", "/samples/pdf/袖扣_订单_9单_03252026.pdf")
        );
    }
}
