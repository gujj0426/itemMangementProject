package com.pdfconverter.scheduler;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.export.ExcelWriterService;
import com.pdfconverter.service.core.FileService;
import com.pdfconverter.service.PdfExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
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
        log.info("开始处理PDF文件");
        log.info("输入目录: {}", inputFolder);
        log.info("备份目录: {}", bakFolder);
        log.info("扫描间隔: 60秒（每分钟执行一次）");

        File dir = new File(inputFolder);
        if (!dir.exists()) {
            log.warn("⚠️  输入目录不存在: {}", inputFolder);
            log.info("正在创建输入目录...");
            boolean created = dir.mkdirs();
            log.info("创建目录 {}: {}", inputFolder, created ? "成功" : "失败");
        } else {
            log.info("✅ 输入目录已存在: {}", dir.getAbsolutePath());
        }

        File bakDir = new File(bakFolder);
        if (!bakDir.exists()) {
            log.warn("⚠️  备份目录不存在: {}", bakFolder);
            log.info("正在创建备份目录...");
            boolean created = bakDir.mkdirs();
            log.info("创建目录 {}: {}", bakFolder, created ? "成功" : "失败");
        } else {
            log.info("✅ 备份目录已存在: {}", bakDir.getAbsolutePath());
        }

        log.info("========================================");
    }

    @Scheduled(fixedRate = 60000) // 每分钟执行
    public void processPdfFiles() {
        File dir = new File(inputFolder);
        if (!dir.exists()) {
            log.warn("输入目录不存在: {}，跳本次扫描", inputFolder);
            return;
        }

        File[] pdfs = dir.listFiles(f -> f.getName().endsWith(".pdf"));
        if (pdfs == null) {
            log.warn("无法读取输入目录: {}", inputFolder);
            return;
        }

        if (pdfs.length > 0) {
            log.info("开始处理，本批次PDF文件数量: {}", pdfs.length);
        } else {
            log.debug("扫描完成，当前输入目录没有PDF文件");
            return;
        }

        // 第一阶段：解析所有PDF，收集所有订单信息
        List<PdfOrderData> allOrders = new ArrayList<>();
        List<File> successFiles = new ArrayList<>();
        List<File> failedFiles = new ArrayList<>();

        for (File pdf : pdfs) {
            try {
                log.info("正在解析文件: {}", pdf.getName());
                // 解析单个pdf，收集订单信息
                List<PdfOrderData> orders = pdfExtractor.extractFromPdf(pdf.getAbsolutePath());
                allOrders.addAll(orders);
                log.info("✓ 成功解析文件: {}，订单数: {}", pdf.getName(), orders.size());
                successFiles.add(pdf);
            } catch (Exception e) {
                log.error("✗ 解析文件失败: {} - {}", pdf.getName(), e.getMessage(), e);
                failedFiles.add(pdf);
            }
        }

        // 第二阶段：统一写入Excel（只有成功解析的文件才写入）
        if (!allOrders.isEmpty()) {
            try {
                log.info("开始写入Excel，总订单数: {}", allOrders.size());
                excelWriter.writeOrders(allOrders);
                log.info("✓ 成功写入Excel");
            } catch (Exception e) {
                log.error("✗ 写入Excel失败: {}", e.getMessage(), e);
                // 写入Excel失败，不移动文件到备份目录
                log.info("由于写入Excel失败，不移动文件到备份目录");
                return;
            }
        }

        // 第三阶段：移动成功的PDF到备份目录
        for (File pdf : successFiles) {
            try {
                String bakPath = bakFolder + File.separator + pdf.getName();
                fileService.moveToBackup(pdf.getAbsolutePath(), bakPath);
                log.info("✓ 成功移动文件到备份: {}", pdf.getName());
            } catch (Exception e) {
                log.error("✗ 移动文件到备份失败: {} - {}", pdf.getName(), e.getMessage(), e);
            }
        }

        // 输出统计信息
        int successCount = successFiles.size();
        int failCount = failedFiles.size();
        if (successCount > 0 || failCount > 0) {
            log.info("本次处理完成 - 成功解析: {}, 失败: {}, 总订单数: {}", 
                     successCount, failCount, allOrders.size());
        }
    }
}