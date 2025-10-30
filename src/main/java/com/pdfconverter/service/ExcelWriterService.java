package com.pdfconverter.service;

import com.pdfconverter.model.ExcelData;
import com.pdfconverter.model.PdfOrderData;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExcelWriterService {

    @Value("${excel.output.path}")
    private String outputFolder;

    @Value("${excel.filename-prefix}")
    private String prefix;

    @Value("${excel.max-files-per-day}")
    private int maxFilesPerDay;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("MM/dd/yy");
    private final DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern("MMdd");

    private Workbook workbook;
    private Sheet sheet;
    private Row headerRow;
    private final List<ExcelData> currentData = new ArrayList<>();
    private String currentFilePath;

    @PostConstruct
    public void init() {
        new File(outputFolder).mkdirs();
    }

    public synchronized void writeOrders(List<PdfOrderData> orders) throws IOException {
        LocalDate now = LocalDate.now();
        String dateStr = now.format(fileDateFormatter);
        int seq = findNextSequenceNumber(dateStr);
        String fileName = String.format("%s%s%02d.xlsx", prefix, dateStr, seq);
        currentFilePath = Paths.get(outputFolder, fileName).toString();

        boolean isNewFile = !new File(currentFilePath).exists();
        if (isNewFile) {
            workbook = new XSSFWorkbook();
            sheet = workbook.createSheet("订单");
            createHeader();
        } else {
            workbook = WorkbookFactory.create(new File(currentFilePath));
            sheet = workbook.getSheetAt(0);
        }

        int startRow = sheet.getLastRowNum() + 1;
        if (startRow == 1 && isNewFile) startRow = 1;

        AtomicInteger rowIdx = new AtomicInteger(startRow);
        if (orders != null) {
            orders.forEach(order -> {
                if (order.getItemDetails() != null) {
                    order.getItemDetails().forEach(detail -> {
                        if (detail.getOrderType() != null) {
                            Arrays.stream(detail.getOrderType().split(",")).forEach(type -> {
                                ExcelData data = new ExcelData();
                                BeanUtils.copyProperties(order, data);
                                BeanUtils.copyProperties(detail, data);
                                data.setDate(now.format(dateFormatter));
                                data.setOrderType(type.trim());
                                data.setQuantity(String.valueOf(detail.getItemQuantity()));
                                data.setInformation(detail.getDynamicAttributes());

                                Row row = sheet.createRow(rowIdx.getAndIncrement());
                                fillRow(row, data);
                                currentData.add(data);
                            });
                        }
                    });
                }
            });
        }

        autoMergeCells();
        try (FileOutputStream fos = new FileOutputStream(currentFilePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHeader() {
        headerRow = sheet.createRow(0);
        String[] headers = {"日期","序号","用户名","订单编号","信息","订单类型","袖扣风格","字体","领带风格","设计师","尺寸","颜色","数量（袖扣单位：对；领带夹单位：个）","包装盒","盒数量"};
        for (int i = 0; i < headers.length; i++) {
            headerRow.createCell(i).setCellValue(headers[i]);
        }
    }

    private void fillRow(Row row, ExcelData data) {
        row.createCell(0).setCellValue(data.getDate());
        row.createCell(1).setCellValue(currentData.size() + 1);
        row.createCell(2).setCellValue(data.getUsername());
        row.createCell(3).setCellValue(data.getOrderNumber());
        row.createCell(4).setCellValue(data.getInfo() != null ? data.getInfo() : "");
        row.createCell(5).setCellValue(data.getOrderType());
        row.createCell(6).setCellValue(data.getCufflinkStyle() != null ? data.getCufflinkStyle() : "—");
        row.createCell(7).setCellValue(data.getFont() != null ? data.getFont() : "—");
        row.createCell(8).setCellValue(data.getTieStyle() != null ? data.getTieStyle() : "—");
        row.createCell(9).setCellValue(data.getDesigner() != null ? data.getDesigner() : "小如");
        row.createCell(10).setCellValue(data.getSize() != null ? data.getSize() : "—");
        row.createCell(11).setCellValue(data.getColor() != null ? data.getColor() : "—");
        row.createCell(12).setCellValue(data.getQuantity());
        row.createCell(13).setCellValue(data.getPackagingBox() != null ? data.getPackagingBox() : "—");
        row.createCell(14).setCellValue("1");
    }

    private void autoMergeCells() {
        int lastRow = sheet.getLastRowNum();
        if (lastRow <= 1) return;

        // 合并用户名
        mergeColumn(2, lastRow);
        // 合并订单编号
        mergeColumn(3, lastRow);
    }

    private void mergeColumn(int col, int lastRow) {
        String prev = null;
        int start = -1;
        for (int r = 1; r <= lastRow; r++) {
            Row row = sheet.getRow(r);
            if (row == null) continue;
            Cell cell = row.getCell(col);
            String val = cell != null ? cell.getStringCellValue() : "";
            if (!val.equals(prev)) {
                if (start != -1 && r - 1 > start) {
                    sheet.addMergedRegion(new CellRangeAddress(start, r - 1, col, col));
                }
                start = r;
            }
            prev = val;
        }
        if (start != -1 && lastRow > start) {
            sheet.addMergedRegion(new CellRangeAddress(start, lastRow, col, col));
        }
    }

    private int findNextSequenceNumber(String dateStr) {
        File dir = new File(outputFolder);
        if (!dir.exists()) return 1;
        File[] files = dir.listFiles((d, name) -> name.startsWith(prefix + dateStr) && name.endsWith(".xlsx"));
        if (files == null || files.length == 0) return 1;
        Set<Integer> used = new HashSet<>();
        for (File f : files) {
            String name = f.getName();
            try {
                int seq = Integer.parseInt(name.substring(name.length() - 6, name.length() - 5));
                used.add(seq);
            } catch (Exception e) { /* ignore */ }
        }
        for (int i = 1; i <= maxFilesPerDay; i++) {
            if (!used.contains(i)) return i;
        }
        return maxFilesPerDay;
    }
}