package com.pdfconverter.service.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

@Service
public class FileService {

    private static final Logger log = LoggerFactory.getLogger(FileService.class);

    /**
     * 移动文件到备份目录（带重试机制）
     *
     * @param inputPath 源文件路径
     * @param bakPath 备份文件路径
     * @throws IOException 如果重试后仍然失败
     */
    public void moveToBackup(String inputPath, String bakPath) throws IOException {
        Path source = Paths.get(inputPath);
        Path target = Paths.get(bakPath);
        Files.createDirectories(target.getParent());

        int maxRetries = 3; // 最大重试次数
        int retryDelay = 2000; // 重试间隔（毫秒）

        for (int i = 0; i < maxRetries; i++) {
            try {
                Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("文件移动成功: {} -> {}", inputPath, bakPath);
                return; // 成功则返回
            } catch (FileSystemException e) {
                if (i < maxRetries - 1) {
                    log.warn("文件移动失败，第{}次重试中... 原因: {}", (i + 1), e.getMessage());
                    try {
                        Thread.sleep(retryDelay);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IOException("文件移动被中断", ie);
                    }
                } else {
                    // 最后一次重试失败
                    log.error("文件移动失败，已重试{}次: {}", maxRetries, e.getMessage());
                    throw new IOException("文件移动失败: " + e.getMessage(), e);
                }
            }
        }
    }

    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }
}