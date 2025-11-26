package org.example.zalu.exception.file;

/**
 * Exception được ném khi có lỗi trong quá trình xử lý file
 */
public class FileOperationException extends Exception {
    public FileOperationException(String message) {
        super(message);
    }

    public FileOperationException(String message, Throwable cause) {
        super(message, cause);
    }
}

