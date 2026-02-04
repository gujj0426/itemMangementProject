package com.pdfconverter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 必须有！

@SpringBootApplication
@EnableScheduling  // 关键注解：启用定时任务
public class PdfToExcelApplication {
    private static final Logger log = LoggerFactory.getLogger(PdfToExcelApplication.class);

    public static void main(String[] args) {
        log.info("========================================");
        log.info("正在启动 PDF 订单处理系统...");
        log.info("========================================");
        SpringApplication.run(PdfToExcelApplication.class, args);
        log.info("========================================");
        log.info("✅  PDF 订单处理系统启动成功！");
        log.info("========================================");
        System.out.println("✅ PDF 订单处理系统启动成功！");
    }
}