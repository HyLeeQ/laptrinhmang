package org.example.zalu.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    private static final String SERVER_ADDRESS = "172.16.0.243"; // IP server của bạn
    private static final int SERVER_PORT = 5000;
    private static int userId;

    public static void main(String[] args) {
        try (
                Socket socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
                ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(socket.getInputStream())
        ) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT + ". Enter your userId to login:");
            Scanner scanner = new Scanner(System.in);
            userId = Integer.parseInt(scanner.nextLine());
            out.writeObject("LOGIN:" + userId);
            out.flush();

            // Thread nhận tin nhắn
            new Thread(() -> {
                try {
                    while (true) {
                        String message = (String) in.readObject();
                        if (message != null) {
                            System.out.println(message);
                        }
                    }
                } catch (IOException | ClassNotFoundException e) {
                    System.out.println("Error receiving message: " + e.getMessage());
                }
            }).start();

            // Gửi tin nhắn
            while (true) {
                System.out.println("Enter receiverId and message (e.g., 2:Hello):");
                String input = scanner.nextLine();
                String[] parts = input.split(":", 2);
                if (parts.length == 2) {
                    int receiverId = Integer.parseInt(parts[0]);
                    String content = parts[1];
                    String message = userId + ":" + receiverId + ":" + content;
                    out.writeObject(message);
                    out.flush();
                } else {
                    System.out.println("Invalid format. Use: receiverId:message");
                }
            }
        } catch (IOException e) {
            System.out.println("Client error connecting to " + SERVER_ADDRESS + ":" + SERVER_PORT + ": " + e.getMessage());
            System.out.println("Check if server is running and port is open.");
        }
    }
}