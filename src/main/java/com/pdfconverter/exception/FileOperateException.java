package com.pdfconverter.exception;

/**
 * 文件操作异常
 */
public class FileOperateException extends RuntimeException {
    public FileOperateException(String message) {
        super(message);
    }

    public FileOperateException(String message, Throwable cause) {
        super(message, cause);
    }
}