package org.example.zalu.exception.file;

/**
 * Exception được ném khi có lỗi trong quá trình xử lý audio (ghi âm/phát âm)
 */
public class AudioException extends Exception {
    public AudioException(String message) {
        super(message);
    }

    public AudioException(String message, Throwable cause) {
        super(message, cause);
    }
}

