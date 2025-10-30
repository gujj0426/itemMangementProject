package com.pdfconverter.service;

import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;

@Service
public class FileService {

    public void moveToBackup(String inputPath, String bakPath) throws IOException {
        Path source = Paths.get(inputPath);
        Path target = Paths.get(bakPath);
        Files.createDirectories(target.getParent());
        Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean exists(String path) {
        return Files.exists(Paths.get(path));
    }
}