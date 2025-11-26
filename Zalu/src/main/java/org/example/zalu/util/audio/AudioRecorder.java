package org.example.zalu.util.audio;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;

/**
 * Utility class để ghi âm audio
 * Lưu ý: Cần quyền truy cập microphone
 */
public class AudioRecorder {
    private TargetDataLine targetDataLine;
    private AudioFormat audioFormat;
    private boolean isRecording = false;
    private ByteArrayOutputStream audioOutputStream;

    public AudioRecorder() {
        // Cấu hình audio format: 16kHz, 16bit, mono, PCM
        audioFormat = new AudioFormat(16000.0f, 16, 1, true, false);
    }

    /**
     * Bắt đầu ghi âm
     */
    public boolean startRecording() {
        if (isRecording) {
            return false;
        }

        try {
            DataLine.Info dataLineInfo = new DataLine.Info(TargetDataLine.class, audioFormat);
            if (!AudioSystem.isLineSupported(dataLineInfo)) {
                System.err.println("Microphone không được hỗ trợ với format này");
                return false;
            }

            targetDataLine = (TargetDataLine) AudioSystem.getLine(dataLineInfo);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            audioOutputStream = new ByteArrayOutputStream();
            isRecording = true;

            // Thread để đọc dữ liệu từ microphone
            Thread recordingThread = new Thread(() -> {
                byte[] buffer = new byte[4096];
                while (isRecording) {
                    int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
                    if (bytesRead > 0) {
                        audioOutputStream.write(buffer, 0, bytesRead);
                    }
                }
            });
            recordingThread.setDaemon(true);
            recordingThread.start();

            return true;
        } catch (LineUnavailableException e) {
            System.err.println("Không thể mở microphone: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Dừng ghi âm và trả về dữ liệu audio dạng byte[]
     */
    public byte[] stopRecording() {
        if (!isRecording) {
            return null;
        }

        isRecording = false;
        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
        }

        if (audioOutputStream != null) {
            byte[] audioData = audioOutputStream.toByteArray();
            try {
                audioOutputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return audioData;
        }

        return null;
    }

    /**
     * Lưu audio data vào file WAV
     */
    public boolean saveToFile(byte[] audioData, File outputFile) {
        if (audioData == null || audioData.length == 0) {
            return false;
        }

        try {
            AudioInputStream audioInputStream = new AudioInputStream(
                    new java.io.ByteArrayInputStream(audioData),
                    audioFormat,
                    audioData.length / audioFormat.getFrameSize()
            );

            AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, outputFile);
            audioInputStream.close();
            return true;
        } catch (IOException e) {
            System.err.println("Lỗi khi lưu file audio: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Kiểm tra xem có đang ghi âm không
     */
    public boolean isRecording() {
        return isRecording;
    }

    /**
     * Hủy ghi âm (không lưu)
     */
    public void cancelRecording() {
        if (isRecording) {
            isRecording = false;
            if (targetDataLine != null) {
                targetDataLine.stop();
                targetDataLine.close();
            }
            if (audioOutputStream != null) {
                try {
                    audioOutputStream.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}

