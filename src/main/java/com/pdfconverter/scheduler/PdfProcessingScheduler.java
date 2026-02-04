package com.pdfconverter.scheduler;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.ExcelWriterService;
import com.pdfconverter.service.FileService;
import com.pdfconverter.service.PdfExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.List;

@Component
@EnableScheduling
public class PdfProcessingScheduler {

    private static final Logger log = LoggerFactory.getLogger(PdfProcessingScheduler.class);

    @Value("${app.pdf.input-folder}")
    private String inputFolder;

    @Value("${app.pdf.bak-folder}")
    private String bakFolder;

    private final PdfExtractorService pdfExtractor;
    private final ExcelWriterService excelWriter;
    private final FileService fileService;

    public PdfProcessingScheduler(PdfExtractorService pdfExtractor, ExcelWriterService excelWriter, FileService fileService) {
        this.pdfExtractor = pdfExtractor;
        this.excelWriter = excelWriter;
        this.fileService = fileService;
    }

    @PostConstruct
    public void init() {
        log.info("========================================");
        log.info("PDF处理调度器已启动");
        log.info("输入目录: {}", inputFolder);
        log.info("备份目录: {}", bakFolder);
        log.info("扫描间隔: 60秒（每分钟执行一次）");
        log.info("========================================");
    }

    @Scheduled(fixedRate = 60000) // 每分钟执行
    public void processPdfFiles() {
        File dir = new File(inputFolder);
        if (!dir.exists()) return;

        File[] pdfs = dir.listFiles(f -> f.getName().endsWith(".pdf"));
        if (pdfs == null) return;

        if (pdfs.length > 0) {
            log.info("开始处理，本批次PDF文件数量: {}", pdfs.length);
        }

        int successCount = 0;
        int failCount = 0;

        for (File pdf : pdfs) {
            try {
                log.info("正在处理文件: {}", pdf.getName());
                List<PdfOrderData> orders = pdfExtractor.extractFromPdf(pdf.getAbsolutePath());
                excelWriter.writeOrders(orders);
                String bakPath = bakFolder + File.separator + pdf.getName();
                fileService.moveToBackup(pdf.getAbsolutePath(), bakPath);
                log.info("✓ 成功处理文件: {}", pdf.getName());
                successCount++;
            } catch (Exception e) {
                log.error("✗ 处理文件失败: {} - {}", pdf.getName(), e.getMessage(), e);
                failCount++;
            }
        }

        if (successCount > 0 || failCount > 0) {
            log.info("本次处理完成 - 成功: {}, 失败: {}", successCount, failCount);
        }
    }
}