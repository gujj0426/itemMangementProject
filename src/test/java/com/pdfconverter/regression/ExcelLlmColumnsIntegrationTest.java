package com.pdfconverter.regression;

import com.pdfconverter.PdfToExcelApplication;
import com.pdfconverter.constant.OrderType;
import com.pdfconverter.constant.ProductColor;
import com.pdfconverter.constant.ProductName;
import com.pdfconverter.constant.ProductSize;
import com.pdfconverter.constant.ProductVariable;
import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.export.ExcelWriterService;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

import static com.pdfconverter.constant.ExcelConstant.DESIGN_STYLE_INDEX;
import static com.pdfconverter.constant.ExcelConstant.ENGRAVING_INFO_INDEX;
import static com.pdfconverter.constant.ExcelConstant.FONT_INDEX;
import static com.pdfconverter.constant.ExcelConstant.ICON_INDEX;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证开启 LLM 合并策略后，ItemDetail 上模拟的识别结果会写入 Excel「设计风格 / 刻录信息 / 字体 / icon」列。
 * 不调用真实 DeepSeek，仅测导出链路。
 */
@SpringBootTest(classes = PdfToExcelApplication.class)
@ActiveProfiles("test")
class ExcelLlmColumnsIntegrationTest {

    private static Path excelOutputDir;

    static {
        try {
            excelOutputDir = Files.createTempDirectory("excel-llm-columns-").toAbsolutePath();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @DynamicPropertySource
    static void registerExcelFolder(DynamicPropertyRegistry registry) {
        registry.add("app.excel.output-folder", () -> excelOutputDir.toString());
        registry.add("app.llm.personalization.enabled", () -> "true");
        registry.add("app.llm.personalization.merge-policy", () -> "overlay");
    }

    @Autowired
    private ExcelWriterService excelWriterService;

    @AfterEach
    void cleanupXlsx() throws IOException {
        if (Files.isDirectory(excelOutputDir)) {
            try (Stream<Path> stream = Files.list(excelOutputDir)) {
                stream.filter(p -> p.toString().endsWith(".xlsx")).forEach(p -> {
                    try {
                        Files.deleteIfExists(p);
                    } catch (IOException ignored) {
                        // ignore
                    }
                });
            }
        }
    }

    @Test
    void writeOrders_writesLlmStyleEngravingFontToExcelColumns() throws Exception {
        PdfOrderData order = new PdfOrderData();
        order.setUsername("测试用户");
        order.setOrderNumber("LLM-EXCEL-001");

        PdfOrderData.ItemDetail item = new PdfOrderData.ItemDetail();
        item.setMainProductFlg(true);
        item.setOrderType(OrderType.CUFFLINK);
        item.setProductName(ProductName.CUFFLINK);
        item.setProductSize(ProductSize.L);
        item.setProductColor(ProductColor.GOLD);
        item.setProductVariable(ProductVariable.UNKNOWN);
        item.setItemQuantity(1);
        item.setPersonalization("dummy personalization for export test");
        item.setItemTitle("Test Listing Title");
        // 简单动态信息，避免多面刻录拆行
        item.setDynamicAttributes("Color: Gold");
        item.setStyle("Style 99");
        item.setFont("Font 1");
        // 模拟 LLM 输出（应覆盖规则列，因 merge-policy=overlay）
        item.setLlmDesignStyle("Style 5");
        item.setLlmFont("Font 10(no)");
        item.setLlmIcon("icon #7");
        item.setLlmEngravingContent("连通性刻录内容XYZ");

        order.setItemDetails(Collections.singletonList(item));

        excelWriterService.writeOrders(Collections.singletonList(order));

        Path xlsx = findSingleExcelFile(excelOutputDir);
        assertTrue(Files.exists(xlsx), "应生成 xlsx: " + xlsx);

        DataFormatter formatter = new DataFormatter();
        try (var wb = WorkbookFactory.create(xlsx.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row row1 = sheet.getRow(1);
            assertEquals("Style 5", formatter.formatCellValue(row1.getCell(DESIGN_STYLE_INDEX)),
                    "设计风格列应写入 LLM 识别值（经样式映射后仍为 Style 5）");
            assertEquals("连通性刻录内容XYZ", formatter.formatCellValue(row1.getCell(ENGRAVING_INFO_INDEX)),
                    "刻录信息列应写入 LLM 刻录正文");
            assertEquals("Font 10(no)", formatter.formatCellValue(row1.getCell(FONT_INDEX)),
                    "字体列应写入 LLM 识别值（经字体映射后）");
            assertEquals("icon #7", formatter.formatCellValue(row1.getCell(ICON_INDEX)),
                    "icon 列应写入标准化 icon #n");
        }
    }

    private static Path findSingleExcelFile(Path dir) throws IOException {
        List<Path> files;
        try (Stream<Path> stream = Files.list(dir)) {
            files = stream.filter(p -> p.getFileName().toString().endsWith(".xlsx")).toList();
        }
        assertEquals(1, files.size(), "预期目录内仅一个 xlsx");
        return files.get(0);
    }
}
