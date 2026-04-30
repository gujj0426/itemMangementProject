package com.pdfconverter.regression.golden;

import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * Canonical Excel snapshot for full-row/full-column regression assertion.
 */
public class ExcelGoldenSnapshot {

    public static final String DYNAMIC_TODAY_TOKEN = "__TODAY__";

    public int schemaVersion = 1;
    public String caseId;
    public List<String> headers = new ArrayList<>();
    public List<List<String>> rows = new ArrayList<>();

    public static ExcelGoldenSnapshot fromExcelFile(String caseId, File file) throws Exception {
        try (Workbook workbook = WorkbookFactory.create(file)) {
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();

            ExcelGoldenSnapshot snap = new ExcelGoldenSnapshot();
            snap.caseId = caseId;

            Row headerRow = sheet.getRow(0);
            int colCount = headerRow == null ? 0 : headerRow.getLastCellNum();
            for (int c = 0; c < colCount; c++) {
                snap.headers.add(formatter.formatCellValue(headerRow.getCell(c)));
            }

            int outputDateCol = snap.headers.indexOf("出库日期");
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                List<String> values = new ArrayList<>();
                boolean allEmpty = true;
                for (int c = 0; c < colCount; c++) {
                    String value = formatter.formatCellValue(row.getCell(c));
                    if (outputDateCol >= 0 && c == outputDateCol && !value.isEmpty()) {
                        // Keep regression stable across days while still enforcing column presence.
                        value = DYNAMIC_TODAY_TOKEN;
                    }
                    if (!value.isEmpty()) {
                        allEmpty = false;
                    }
                    values.add(value);
                }
                if (!allEmpty) {
                    snap.rows.add(values);
                }
            }
            return snap;
        }
    }
}
