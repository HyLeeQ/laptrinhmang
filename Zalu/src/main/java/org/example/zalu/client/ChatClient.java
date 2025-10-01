package org.example.zalu.client;

import javafx.application.Platform;
import org.example.zalu.HelloApplication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ChatClient {
    private static final String SERVER_ADDRESS = "172.16.0.243"; // IP server của bạn
    private static final int SERVER_PORT = 5000;
    private static ObjectOutputStream out; // Static để UI sử dụng
    private static ObjectInputStream in; // Static để UI sử dụng nếu cần
    private static Socket socket; // Static để UI sử dụng nếu cần
    private static volatile boolean connected = false;

    public static void main(String[] args) {
        // Kết nối server trước
        if (connectToServer()) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT + ". Launching UI...");
            // Mở UI JavaFX ngay sau khi kết nối thành công
            Platform.runLater(() -> HelloApplication.launch(args));
            // Giữ chương trình chạy (đợi UI đóng)
            try {
                Thread.currentThread().join(); // Đợi UI đóng trước khi thoát
            } catch (InterruptedException e) {
                System.out.println("Client interrupted: " + e.getMessage());
            }
        } else {
            System.out.println("Failed to connect to server. Exiting.");
        }
    }

    private static boolean connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            System.out.println("Server connection successful");

            // Thread nhận tin nhắn nền (tích hợp với UI sau)
            new Thread(() -> {
                try {
                    while (connected) {
                        String message = (String) in.readObject();
                        if (message != null) {
                            System.out.println("Received: " + message); // Sau này tích hợp với UI chatArea
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    if (connected) {
                        System.out.println("Error receiving message: " + e.getMessage());
                    }
                }
            }).start();

            return true;
        } catch (IOException e) {
            System.out.println("Client error connecting to " + SERVER_ADDRESS + ":" + SERVER_PORT + ": " + e.getMessage());
            System.out.println("Check if server is running and port is open.");
            return false;
        }
    }

    // Getter để UI sử dụng (gửi tin nhắn từ UI)
    public static ObjectOutputStream getOut() {
        return out;
    }

    public static void disconnect() {
        connected = false;
        try {
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Error disconnecting: " + e.getMessage());
        }
    }
}