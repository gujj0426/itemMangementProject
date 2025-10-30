package com.pdfconverter.scheduler;

import com.pdfconverter.model.PdfOrderData;
import com.pdfconverter.service.ExcelWriterService;
import com.pdfconverter.service.FileService;
import com.pdfconverter.service.PdfExtractorService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.List;

@Component
@EnableScheduling
public class PdfProcessingScheduler {

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

    @Scheduled(fixedRate = 60000) // 每分钟执行
    public void processPdfFiles() {
        File dir = new File(inputFolder);
        if (!dir.exists()) return;

        File[] pdfs = dir.listFiles(f -> f.getName().endsWith(".pdf"));
        if (pdfs == null) return;

        for (File pdf : pdfs) {
            try {
                List<PdfOrderData> orders = pdfExtractor.extractFromPdf(pdf.getAbsolutePath());
                excelWriter.writeOrders(orders);
                String bakPath = bakFolder + "/" + pdf.getName();
                fileService.moveToBackup(pdf.getAbsolutePath(), bakPath);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}