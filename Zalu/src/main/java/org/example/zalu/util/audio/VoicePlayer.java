package org.example.zalu.util.audio;

import javafx.application.Platform;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.util.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Utility class để phát audio trong ứng dụng
 */
public class VoicePlayer {
    private static final Logger logger = LoggerFactory.getLogger(VoicePlayer.class);
    private MediaPlayer mediaPlayer;
    private File tempAudioFile;
    private Runnable onPlayStateChanged;
    private Runnable onFinished;
    private double totalDuration = 0; // Lưu tổng thời lượng
    private Runnable onReady;

    /**
     * Phát audio từ byte array
     * audioData có thể là raw PCM data hoặc WAV file data
     */
    public void playAudio(byte[] audioData, String fileName) {
        // Nếu đã có MediaPlayer, chỉ cần reset về đầu và phát lại
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.seek(javafx.util.Duration.ZERO);
            mediaPlayer.play();
            if (onPlayStateChanged != null) {
                onPlayStateChanged.run();
            }
            return;
        }

        stop(); // Dừng audio đang phát nếu có

        try {
            // Tạo file tạm
            Path voiceDir = Paths.get("voice_messages");
            if (!Files.exists(voiceDir)) {
                Files.createDirectories(voiceDir);
            }

            tempAudioFile = voiceDir.resolve("play_" + System.currentTimeMillis() + "_" + fileName).toFile();

            // Kiểm tra xem audioData có phải là WAV file không (có header "RIFF")
            boolean isWavFile = audioData.length >= 4 &&
                    audioData[0] == 'R' && audioData[1] == 'I' &&
                    audioData[2] == 'F' && audioData[3] == 'F';

            if (isWavFile) {
                // Nếu đã là WAV file, ghi trực tiếp
                try (FileOutputStream fos = new FileOutputStream(tempAudioFile)) {
                    fos.write(audioData);
                }
            } else {
                // Nếu là raw PCM data, cần convert sang WAV format
                // Sử dụng format tương tự AudioRecorder: 16kHz, 16bit, mono, PCM, signed,
                // little-endian
                AudioFormat audioFormat = new AudioFormat(16000.0f, 16, 1, true, false);

                AudioInputStream audioInputStream = new AudioInputStream(
                        new ByteArrayInputStream(audioData),
                        audioFormat,
                        audioData.length / audioFormat.getFrameSize());

                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempAudioFile);
                audioInputStream.close();
            }

            // Tạo Media và MediaPlayer
            String fileUrl = tempAudioFile.toURI().toString();
            Media media = new Media(fileUrl);
            mediaPlayer = new MediaPlayer(media);

            // Lưu tổng thời lượng khi media ready
            mediaPlayer.setOnReady(() -> {
                if (mediaPlayer.getMedia() != null) {
                    Duration duration = mediaPlayer.getMedia().getDuration();
                    if (duration != null && !duration.isUnknown()) {
                        totalDuration = duration.toSeconds();
                    }
                }
                if (onReady != null) {
                    onReady.run();
                }
            });

            // Xử lý khi phát xong - reset về đầu thay vì dispose
            mediaPlayer.setOnEndOfMedia(() -> {
                Platform.runLater(() -> {
                    if (mediaPlayer != null) {
                        mediaPlayer.seek(javafx.util.Duration.ZERO);
                        mediaPlayer.stop();
                    }
                    if (onFinished != null) {
                        onFinished.run();
                    }
                    if (onPlayStateChanged != null) {
                        onPlayStateChanged.run();
                    }
                });
            });

            // Xử lý lỗi
            mediaPlayer.setOnError(() -> {
                logger.error("Lỗi phát audio: {}", mediaPlayer.getError());
                Platform.runLater(() -> {
                    if (onPlayStateChanged != null) {
                        onPlayStateChanged.run();
                    }
                });
            });

            // Bắt đầu phát
            mediaPlayer.play();

            if (onPlayStateChanged != null) {
                onPlayStateChanged.run();
            }

        } catch (IOException | IllegalArgumentException e) {
            logger.error("Lỗi khi tạo file tạm để phát audio: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (onPlayStateChanged != null) {
                    onPlayStateChanged.run();
                }
            });
        } catch (Exception e) {
            logger.error("Lỗi không xác định khi phát audio: {}", e.getMessage(), e);
            Platform.runLater(() -> {
                if (onPlayStateChanged != null) {
                    onPlayStateChanged.run();
                }
            });
        }
    }

    /**
     * Dừng phát audio (không dispose để có thể phát lại)
     */
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.seek(javafx.util.Duration.ZERO);
        }
    }

    /**
     * Dispose và giải phóng tài nguyên (chỉ gọi khi không cần phát lại)
     */
    public void dispose() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }

        // Xóa file tạm sau 1 giây (để đảm bảo MediaPlayer đã giải phóng)
        if (tempAudioFile != null && tempAudioFile.exists()) {
            tempAudioFile.deleteOnExit();
        }

        totalDuration = 0;
        onReady = null;
    }

    /**
     * Lấy tổng thời lượng (không cần MediaPlayer đang active)
     */
    public double getTotalDuration() {
        if (totalDuration > 0) {
            return totalDuration;
        }
        return getDuration();
    }

    /**
     * Set callback khi media ready (để lấy duration)
     */
    public void setOnReady(Runnable callback) {
        this.onReady = callback;
        // Nếu MediaPlayer đã ready, gọi callback ngay
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.READY) {
            if (mediaPlayer.getMedia() != null) {
                Duration duration = mediaPlayer.getMedia().getDuration();
                if (duration != null && !duration.isUnknown()) {
                    totalDuration = duration.toSeconds();
                }
            }
            if (callback != null) {
                callback.run();
            }
        }
    }

    /**
     * Tạm dừng phát audio
     */
    public void pause() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
            mediaPlayer.pause();
            if (onPlayStateChanged != null) {
                onPlayStateChanged.run();
            }
        }
    }

    /**
     * Tiếp tục phát audio
     */
    public void resume() {
        if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED) {
            mediaPlayer.play();
            if (onPlayStateChanged != null) {
                onPlayStateChanged.run();
            }
        }
    }

    /**
     * Kiểm tra xem có đang phát không
     */
    public boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    /**
     * Kiểm tra xem có đang tạm dừng không
     */
    public boolean isPaused() {
        return mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PAUSED;
    }

    /**
     * Lấy thời lượng audio (giây)
     */
    public double getDuration() {
        if (mediaPlayer != null && mediaPlayer.getMedia() != null) {
            Duration duration = mediaPlayer.getMedia().getDuration();
            return duration != null && !duration.isUnknown() ? duration.toSeconds() : 0;
        }
        return 0;
    }

    /**
     * Lấy thời gian hiện tại (giây)
     */
    public double getCurrentTime() {
        if (mediaPlayer != null) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            return currentTime != null ? currentTime.toSeconds() : 0;
        }
        return 0;
    }

    /**
     * Set callback khi trạng thái phát thay đổi
     */
    public void setOnPlayStateChanged(Runnable callback) {
        this.onPlayStateChanged = callback;
    }

    /**
     * Set callback khi phát xong
     */
    public void setOnFinished(Runnable callback) {
        this.onFinished = callback;
    }

}
