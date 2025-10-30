package com.pdfconverter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling; // 必须有！

@SpringBootApplication
@EnableScheduling  // 关键注解：启用定时任务
public class PdfToExcelApplication {
    public static void main(String[] args) {
        SpringApplication.run(PdfToExcelApplication.class, args);
        System.out.println("✅ PDF处理系统启动成功！");
    }
}