package com.pdfconverter.service;

import com.pdfconverter.constant.OrderType;
import com.pdfconverter.model.ExcelData;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.util.FontNameMapper;
import com.pdfconverter.util.StyleNameMapper;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static com.pdfconverter.constant.ExcelConstant.*;

@Service
public class ExcelWriterService {
    private static final Logger log = LoggerFactory.getLogger(ExcelWriterService.class);

    @Resource
    private StyleNameMapper styleNameMapper;

    @Resource
    private FontNameMapper fontNameMapper;

    @Value("${app.excel.output-folder}")
    private String outputFolder;

    @Value("${app.excel.filename-prefix}")
    private String prefix;

    @Value("${app.excel.max-files-per-day}")
    private int maxFilesPerDay;

    @Value("${app.excel.sheet-name}")
    private String sheetName;

    @Value("${app.excel.initial-index}")
    private int initialIndex;

    private final DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy年MM月dd日");
    private final DateTimeFormatter fileDateFormatter = DateTimeFormatter.ofPattern("yyMMdd");

    private Workbook workbook;
    private Sheet sheet;
    private Row headerRow;
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
            sheet = workbook.createSheet(sheetName);
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
                        if (detail.getOrderType() != null && detail.getOrderType() != OrderType.UNKNOWN) {
                            // 直接使用每个 ItemDetail，不再按逗号分割（已在 parseItemDetail 中拆分）
                            ExcelData data = new ExcelData();
                            BeanUtils.copyProperties(order, data);
                            BeanUtils.copyProperties(detail, data);
                            data.setDate(now.format(dateFormatter));
                            data.setQuantity(String.valueOf(detail.getItemQuantity()));
                            data.setInformation(detail.getDynamicAttributes());
                            // 设置产品变量（用于包装盒等需要标准名称的情况）
                            data.setDynamicAttributes(detail.getProductVariable().getDisplayName());
                            int quantity = detail.getItemQuantity();
                            for (int i = 0; i < quantity; i++) {
                                ExcelData rowData = new ExcelData();
                                BeanUtils.copyProperties(data, rowData);
                                rowData.setQuantity("1");
                                rowData.setSerialNumber(String.valueOf(initialIndex));
                                Row row = sheet.createRow(rowIdx.getAndIncrement());
                                fillRow(row, rowData);
                            }
                        }
                    });
                }
                initialIndex++;
            });
        }

        try (FileOutputStream fos = new FileOutputStream(currentFilePath)) {
            workbook.write(fos);
        }
        workbook.close();
    }

    private void createHeader() {
        headerRow = sheet.createRow(0);
        for (int i = 0; i < EXCEL_HEADERS.length; i++) {
            headerRow.createCell(i).setCellValue(EXCEL_HEADERS[i]);
        }
    }

    private void fillRow(Row row, ExcelData data) {
        // 0: 产品编号 - 空
        row.createCell(PRODUCT_CODE_INDEX).setCellValue("");
        // 1: 用户名 = 用户名
        row.createCell(USERNAME_INDEX).setCellValue(data.getUsername() != null ? data.getUsername() : "");
        // 2: 订单编号 = 订单编号
        row.createCell(ORDER_NUMBER_INDEX).setCellValue(data.getOrderNumber() != null ? data.getOrderNumber() : "");
        // 3: 产品名称 = 订单类型
        row.createCell(PRODUCT_NAME_INDEX).setCellValue(data.getOrderType() != null ? data.getOrderType().getDisplayName() : "");
        // 4: 型号 = 尺寸
        row.createCell(MODEL_INDEX).setCellValue(data.getProductSize() != null ? data.getProductSize() : "");
        // 5: 颜色 = 颜色
        row.createCell(COLOR_INDEX).setCellValue(data.getProductColor() != null ? data.getProductColor() : "");
        // 6: 产品变量 = dynamicAttributes（如"Oval Box-椭圆形开窗木盒"）
        row.createCell(PRODUCT_VARIABLE_INDEX).setCellValue(data.getDynamicAttributes() != null ? data.getDynamicAttributes() : "");
        // 7: 设计风格 = style（从Personalization中提取的设计风格）
        row.createCell(DESIGN_STYLE_INDEX).setCellValue(convertStyle(data.getStyle()));
        // 8: 刻录信息 = 空
        row.createCell(ENGRAVING_INFO_INDEX).setCellValue("");
        // 9: 字体 = font（从Personalization中提取的字体）
        row.createCell(FONT_INDEX).setCellValue(convertFont(data.getFont()));
        // 10: icon = 空
        row.createCell(ICON_INDEX).setCellValue("");
        // 11: 是否派单 = 空
        row.createCell(IS_ASSIGNED_INDEX).setCellValue("");
        // 12: 设计师 - 空
        row.createCell(DESIGNER_INDEX).setCellValue("");
        // 13: 数量 = 默认数量1
        row.createCell(QUANTITY_INDEX).setCellValue(data.getQuantity() != null ? data.getQuantity() : "1");
        // 14: 出库日期 = 系统日期（yyyy年MM月dd日）
        row.createCell(OUTPUT_DATE_INDEX).setCellValue(data.getDate());
        // 15: Personalization - 保留原有逻辑
        row.createCell(PERSONALIZATION_INDEX).setCellValue(data.getPersonalization() != null ? data.getPersonalization() : "");
        // 16: 订购完全信息 - 保留原有逻辑
        row.createCell(FULL_ORDER_INFO_INDEX).setCellValue(data.getInformation() != null ? data.getInformation() : "");
        // 17: 商品标题 - 保留原有逻辑
        row.createCell(ITEM_TITLE_INDEX).setCellValue(data.getItemTitle() != null ? data.getItemTitle() : "");

    }

    private String convertFont(String font) {
        if (font == null || font.isEmpty()) {
            return "";
        }
        // 通过映射表转换字体
        return fontNameMapper.getStandardName(font);
    }

    private String convertStyle(String style) {
        if (style == null || style.isEmpty()) {
            return "";
        }
        // 通过映射表转换设计风格
        return styleNameMapper.getStandardName(style);
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