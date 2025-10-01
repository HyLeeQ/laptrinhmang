package org.example.zalu.server;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;

public class ChatServer {
    private static Map<Integer, ObjectOutputStream> clients = new HashMap<>(); // Lưu client theo userId

    public static void main(String[] args) {
        int port = 5000;
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Chat Server started on port " + port);

            while (true) {
                Socket clientSocket = serverSocket.accept();
                new ClientHandler(clientSocket).start();
            }
        } catch (IOException e) {
            System.out.println("Server error: " + e.getMessage());
        }
    }

    static class ClientHandler extends Thread {
        private Socket socket;
        private ObjectInputStream in;
        private ObjectOutputStream out;
        private int userId = -1;
        public ClientHandler(Socket socket) {
            this.socket = socket;
        }

        @Override
        public void run() {
            try {
                out = new ObjectOutputStream(socket.getOutputStream());
                in = new ObjectInputStream(socket.getInputStream());

                // Đọc userId từ client khi kết nối
                String initialMessage = (String) in.readObject();
                if (initialMessage != null && initialMessage.startsWith("LOGIN:")) {
                    userId = Integer.parseInt(initialMessage.split(":")[1]);
                    clients.put(userId, out);
                    System.out.println("User " + userId + " connected");
                }
                while (true) {
                    String message = (String) in.readObject();
                    if (message != null) {
                        String[] parts = message.split(":");
                        if (parts.length >= 3) {
                            int senderId = Integer.parseInt(parts[0]);
                            int receiverId = Integer.parseInt(parts[1]);
                            String content = parts[2];
                            String broadcastMessage = "[" + senderId + " -> " + receiverId + "]: " + content;

                            // Gửi tin nhắn đến receiver
                            ObjectOutputStream receiverOut = clients.get(receiverId);
                            if (receiverOut != null) {
                                receiverOut.writeObject(broadcastMessage);
                                receiverOut.flush();
                            }
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                System.out.println("Error handling client " + userId + ": " + e.getMessage());
                if (userId != -1) {
                    clients.remove(userId);
                    System.out.println("User " + userId + " disconnected");
                }
            } finally {
                try {
                    if (socket != null) socket.close();
                } catch (IOException e) {
                    System.out.println("Error closing socket: " + e.getMessage());
                }
            }
        }
    }
}