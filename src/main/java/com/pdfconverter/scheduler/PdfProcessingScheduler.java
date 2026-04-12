package com.pdfconverter.scheduler;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.ConfigImportService;
import com.pdfconverter.service.export.ExcelWriterService;
import com.pdfconverter.service.core.PdfMarkService;
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
    private final PdfMarkService pdfMarkService;

    public PdfProcessingScheduler(PdfExtractorService pdfExtractor,
                                   ExcelWriterService excelWriter,
                                   FileService fileService,
                                   ConfigImportService configImportService,
                                   PdfMarkService pdfMarkService) {
        this.pdfExtractor = pdfExtractor;
        this.excelWriter = excelWriter;
        this.fileService = fileService;
        this.configImportService = configImportService;
        this.pdfMarkService = pdfMarkService;
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
        // PDF → 标注页码映射（markId → Set{页码0基}），由解析阶段收集
        Map<File, Map<String, Set<Integer>>> marksByPdf = new LinkedHashMap<>();

        for (File pdf : pdfs) {
            try {
                log.info("正在解析文件: {}", pdf.getName());
                // 标注页码收集：key=markId（与 PdfMarkService.MARKS 一致）
                Map<String, Set<Integer>> marksPages = new LinkedHashMap<>();
                marksPages.put("tieClipStyle6", new LinkedHashSet<>());
                marksPages.put("silentDogTagS",  new LinkedHashSet<>());
                List<PdfOrderData> orders = pdfExtractor.extractFromPdf(
                        pdf.getAbsolutePath(), marksPages.get("tieClipStyle6"), marksPages.get("silentDogTagS"));
                allOrders.addAll(orders);
                // 清理空集后记录
                marksPages.values().removeIf(Set::isEmpty);
                if (!marksPages.isEmpty()) {
                    marksByPdf.put(pdf, marksPages);
                    for (Map.Entry<String, Set<Integer>> e : marksPages.entrySet()) {
                        log.info("  → 标注 {}，需标注页码: {}", e.getKey(), e.getValue());
                    }
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

        // 第三阶段：标注 PDF（通过 PdfMarkService 统一处理），然后移动到备份目录
        for (File pdf : successFiles) {
            try {
                Map<String, Set<Integer>> marksPages = marksByPdf.get(pdf);
                if (marksPages != null && !marksPages.isEmpty()) {
                    pdfMarkService.markAll(pdf.getAbsolutePath(), marksPages);
                    log.info("✓ {} 已完成标注: {}", pdf.getName(), marksPages.keySet());
                }

                // 移动到备份目录
                String bakPath = bakFolder + File.separator + pdf.getName();
                fileService.moveToBackup(pdf.getAbsolutePath(), bakPath);
                log.info("✓ 成功移动文件到备份: {}", pdf.getName());
            } catch (Exception e) {
                log.error("✗ PDF标注或移动失败: {} - {}", pdf.getName(), e.getMessage(), e);
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