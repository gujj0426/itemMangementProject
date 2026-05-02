package com.pdfconverter.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.Arrays;

/**
 * 应用配置类
 * 用于处理命令行参数并更新配置
 */
@Component
public class AppConfig implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(AppConfig.class);

    @Value("${app.pdf.input-folder:}")
    private String inputFolder;

    @Value("${app.pdf.bak-folder:}")
    private String bakFolder;

    @Value("${app.excel.output-folder:}")
    private String outputFolder;

    @Value("${app.excel.initial-index:1}")
    private String initialIndex;

    @Override
    public void run(String... args) throws Exception {
        log.info("========================================");
        log.info("应用配置初始化");
        log.info("命令行参数: {}", Arrays.toString(args));
        log.info("========================================");

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];

            if (arg.startsWith("--inputPath=")) {
                String value = arg.substring("--inputPath=".length());
                System.setProperty("app.pdf.input-folder", value);
                log.info("设置输入路径: {}", value);
            } else if (arg.startsWith("--bakPath=")) {
                String value = arg.substring("--bakPath=".length());
                System.setProperty("app.pdf.bak-folder", value);
                log.info("设置备份路径: {}", value);
            } else if (arg.startsWith("--outputPath=")) {
                String value = arg.substring("--outputPath=".length());
                System.setProperty("app.excel.output-folder", value);
                log.info("设置输出路径: {}", value);
            } else if (arg.startsWith("--initialIndex=")) {
                String value = arg.substring("--initialIndex=".length());
                System.setProperty("app.excel.initial-index", value);
                log.info("设置初始序号: {}", value);
            }
        }

        log.info("========================================");
        log.info("配置完成（以下为 Spring 已解析路径；也可用 JVM 参数 -DinputPath/-DbakPath/-DoutputPath 或命令行 --inputPath= 等覆盖）");
        log.info("输入目录: {}", blankToDash(inputFolder));
        log.info("备份目录: {}", blankToDash(bakFolder));
        log.info("输出目录: {}", blankToDash(outputFolder));
        log.info("初始序号: {}", blankToDash(initialIndex));
        log.info("========================================");
    }

    private static String blankToDash(String s) {
        return (s == null || s.isBlank()) ? "—" : s;
    }
}
