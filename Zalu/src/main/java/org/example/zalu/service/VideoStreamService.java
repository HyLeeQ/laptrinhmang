package org.example.zalu.service;

import com.github.sarxos.webcam.Webcam;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.imageio.ImageIO;
import java.awt.Dimension;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

/**
 * Service quản lý video streaming
 */
public class VideoStreamService {
    private static final Logger logger = LoggerFactory.getLogger(VideoStreamService.class);

    private Webcam webcam;
    private boolean isStreaming = false;
    private ExecutorService executorService;
    private ObjectOutputStream outputStream;
    private ObjectInputStream inputStream;
    private Consumer<Image> onFrameReceived;

    // Độ phân giải video
    private static final int VIDEO_WIDTH = 640;
    private static final int VIDEO_HEIGHT = 480;
    private static final int FPS = 15; // Frame per second

    public VideoStreamService() {
        this.executorService = Executors.newFixedThreadPool(2);
    }

    /**
     * Khởi tạo webcam
     */
    public boolean initializeWebcam() {
        try {
            webcam = Webcam.getDefault();
            if (webcam == null) {
                logger.error("Không tìm thấy webcam");
                return false;
            }

            webcam.setViewSize(new Dimension(VIDEO_WIDTH, VIDEO_HEIGHT));
            webcam.open();
            logger.info("Webcam đã được khởi tạo: {}", webcam.getName());
            return true;
        } catch (Exception e) {
            logger.error("Lỗi khi khởi tạo webcam: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Bắt đầu stream video tới server
     */
    public void startStreaming(ObjectOutputStream output) {
        if (isStreaming) {
            logger.warn("Stream đã đang chạy");
            return;
        }

        this.outputStream = output;
        this.isStreaming = true;

        executorService.submit(() -> {
            logger.info("Bắt đầu streaming video...");
            long frameDelay = 1000 / FPS;

            while (isStreaming && webcam != null && webcam.isOpen()) {
                try {
                    long startTime = System.currentTimeMillis();

                    // Capture frame từ webcam
                    BufferedImage image = webcam.getImage();
                    if (image != null) {
                        // Nén và gửi frame
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        ImageIO.write(image, "jpg", baos);
                        byte[] imageBytes = baos.toByteArray();

                        // Gửi qua socket
                        synchronized (outputStream) {
                            outputStream.writeObject("VIDEO_FRAME");
                            outputStream.writeInt(imageBytes.length);
                            outputStream.write(imageBytes);
                            outputStream.flush();
                        }

                        baos.close();
                    }

                    // Điều chỉnh FPS
                    long elapsed = System.currentTimeMillis() - startTime;
                    long sleepTime = frameDelay - elapsed;
                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }

                } catch (IOException e) {
                    if (isStreaming) {
                        logger.error("Lỗi khi gửi frame: {}", e.getMessage());
                        isStreaming = false;
                    }
                } catch (InterruptedException e) {
                    logger.info("Streaming bị gián đoạn");
                    Thread.currentThread().interrupt();
                    break;
                }
            }
            logger.info("Dừng streaming video");
        });
    }

    /**
     * Bắt đầu nhận video stream từ server
     */
    public void startReceiving(ObjectInputStream input, Consumer<Image> frameCallback) {
        this.inputStream = input;
        this.onFrameReceived = frameCallback;

        executorService.submit(() -> {
            logger.info("Bắt đầu nhận video stream...");

            while (isStreaming) {
                try {
                    // Đọc frame từ socket
                    int length = inputStream.readInt();
                    byte[] imageBytes = new byte[length];
                    inputStream.readFully(imageBytes);

                    // Convert sang JavaFX Image
                    ByteArrayInputStream bais = new ByteArrayInputStream(imageBytes);
                    BufferedImage bufferedImage = ImageIO.read(bais);

                    if (bufferedImage != null) {
                        WritableImage fxImage = SwingFXUtils.toFXImage(bufferedImage, null);

                        // Gọi callback trên JavaFX thread
                        if (onFrameReceived != null) {
                            Platform.runLater(() -> onFrameReceived.accept(fxImage));
                        }
                    }

                    bais.close();

                } catch (IOException e) {
                    if (isStreaming) {
                        logger.error("Lỗi khi nhận frame: {}", e.getMessage());
                        isStreaming = false;
                    }
                }
            }
            logger.info("Dừng nhận video stream");
        });
    }

    /**
     * Dừng streaming
     */
    public void stopStreaming() {
        logger.info("Đang dừng video stream...");
        isStreaming = false;

        if (webcam != null && webcam.isOpen()) {
            webcam.close();
        }

        if (executorService != null && !executorService.isShutdown()) {
            executorService.shutdownNow();
        }
    }

    /**
     * Lấy preview image từ webcam
     */
    public Image getPreviewImage() {
        if (webcam != null && webcam.isOpen()) {
            BufferedImage bufferedImage = webcam.getImage();
            if (bufferedImage != null) {
                return SwingFXUtils.toFXImage(bufferedImage, null);
            }
        }
        return null;
    }

    public boolean isStreaming() {
        return isStreaming;
    }
}
