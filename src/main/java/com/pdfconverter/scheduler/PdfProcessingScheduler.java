package com.pdfconverter.scheduler;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.ConfigImportService;
import com.pdfconverter.service.export.ExcelWriterService;
import com.pdfconverter.service.core.FileService;
import com.pdfconverter.service.core.Style6MarkService;
import com.pdfconverter.service.PdfExtractorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private final ConfigImportService configImportService;
    private final Style6MarkService style6MarkService;

    public PdfProcessingScheduler(PdfExtractorService pdfExtractor,
                                   ExcelWriterService excelWriter,
                                   FileService fileService,
                                   ConfigImportService configImportService,
                                   Style6MarkService style6MarkService) {
        this.pdfExtractor = pdfExtractor;
        this.excelWriter = excelWriter;
        this.fileService = fileService;
        this.configImportService = configImportService;
        this.style6MarkService = style6MarkService;
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
        }

        File bakDir = new File(bakFolder);
        if (!bakDir.exists()) {
            log.warn("⚠️  备份目录不存在: {}", bakFolder);
            log.info("正在创建备份目录...");
            boolean created = bakDir.mkdirs();
            log.info("创建目录 {}: {}", bakFolder, created ? "成功" : "失败");
        }

        log.info("========================================");
    }

    @Scheduled(fixedRate = 60000) // 每分钟执行
    public void processPdfFiles() {
        // ── 优先处理配置模板文件 ──────────────────────────────────────
        try {
            int configCount = configImportService.processConfigFiles();
            if (configCount > 0) {
                log.info("✅ 本轮处理了 {} 个配置模板文件，请重启服务以使配置生效", configCount);
            }
        } catch (Exception e) {
            log.error("配置模板处理异常: {}", e.getMessage(), e);
        }

        // ── 正常 PDF 处理流程 ──────────────────────────────────────────
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
        // PDF → 需要标注 Style 6 的页码列表（0基），由解析阶段直接计算
        Map<File, Set<Integer>> style6PagesByPdf = new LinkedHashMap<>();

        for (File pdf : pdfs) {
            try {
                log.info("正在解析文件: {}", pdf.getName());
                // 解析单个pdf，收集订单信息，同时收集需要标注的页码
                Set<Integer> style6Pages = new LinkedHashSet<>();
                List<PdfOrderData> orders = pdfExtractor.extractFromPdf(pdf.getAbsolutePath(), style6Pages);
                allOrders.addAll(orders);
                if (!style6Pages.isEmpty()) {
                    style6PagesByPdf.put(pdf, style6Pages);
                    log.info("  → 含 Style 6 订单，需标注页码: {}", style6Pages);
                }
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

        // 第三阶段：检测Style 6并标注，然后移动PDF到备份目录
        // 页码映射在解析阶段（Phase 1）已收集完毕，不再重复扫描 PDF
        for (File pdf : successFiles) {
            try {
                Set<Integer> pagesToMark = style6PagesByPdf.get(pdf);
                if (pagesToMark != null && !pagesToMark.isEmpty()) {
                    style6MarkService.detectAndMark(pdf.getAbsolutePath(), new ArrayList<>(pagesToMark));
                    log.info("✓ {} 已添加Style 6标注（页码: {}）", pdf.getName(), pagesToMark);
                }

                // 移动到备份目录
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