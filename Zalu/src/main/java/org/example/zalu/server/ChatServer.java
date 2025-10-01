package org.example.zalu.server;

import org.example.zalu.dao.FriendDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.dao.UserDAO;
import org.example.zalu.model.DBConnection;
import org.example.zalu.model.Friend;
import org.example.zalu.model.Message;
import org.example.zalu.model.User;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ChatServer {
    private static Map<Integer, ObjectOutputStream> clients = new HashMap<>();
    private static UserDAO userDAO;
    private static FriendDAO friendDAO;
    private static MessageDAO messageDAO;

    public static void main(String[] args) {
        try {
            Connection connection = DBConnection.getConnection();
            if (connection == null) throw new Exception("Database connection failed");
            userDAO = new UserDAO(connection);
            friendDAO = new FriendDAO(connection); // Sử dụng FriendDAO có sẵn
            messageDAO = new MessageDAO(connection); // Sử dụng MessageDAO có sẵn
            System.out.println("Database connected successfully");
        } catch (Exception e) {
            System.out.println("Error initializing DAOs: " + e.getMessage());
            return;
        }

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

                // Login (giữ nguyên)
                String initialMessage = (String) in.readObject();
                if (initialMessage != null && initialMessage.startsWith("LOGIN_REQUEST|")) {
                    String[] parts = initialMessage.split("\\|");
                    if (parts.length >= 3) {
                        String username = parts[1];
                        String password = parts[2];
                        User user = userDAO.login(username, password);
                        if (user != null) {
                            userId = user.getId();
                            clients.put(userId, out);
                            String successResponse = "SUCCESS|" + userId + "|" + user.getUsername() + "|" + user.getStatus();
                            out.writeObject(successResponse);
                            out.flush();
                            System.out.println("User " + userId + " logged in successfully");
                        } else {
                            out.writeObject("FAIL|Invalid credentials");
                            out.flush();
                            socket.close();
                            return;
                        }
                    } else {
                        out.writeObject("FAIL|Invalid format");
                        out.flush();
                        socket.close();
                        return;
                    }
                } else {
                    out.writeObject("FAIL|No login request");
                    out.flush();
                    socket.close();
                    return;
                }

                // Loop xử lý requests
                while (true) {
                    Object obj = in.readObject();
                    if (obj instanceof String) {
                        String message = (String) obj;
                        System.out.println("Received from user " + userId + ": " + message);

                        if (message.startsWith("GET_FRIENDS|")) {
                            int reqUserId = Integer.parseInt(message.split("\\|")[1]);
                            List<Integer> friendIds = friendDAO.getFriendsByUserId(reqUserId); // Sử dụng method có sẵn
                            out.writeObject(friendIds);
                            out.flush();
                        } else if (message.startsWith("GET_PENDING_REQUESTS|")) {
                            int reqUserId = Integer.parseInt(message.split("\\|")[1]);
                            List<Integer> pendingIds = friendDAO.getPendingRequests(reqUserId); // Sử dụng method có sẵn
                            out.writeObject(pendingIds);
                            out.flush();
                        } else if (message.startsWith("SEND_FRIEND_REQUEST|")) {
                            String[] parts = message.split("\\|");
                            int senderId = Integer.parseInt(parts[1]);
                            int targetId = Integer.parseInt(parts[2]);
                            boolean success = friendDAO.sendFriendRequest(senderId, targetId); // Sử dụng method có sẵn (transaction)
                            out.writeObject(success);
                            out.flush();
                        } else if (message.startsWith("ACCEPT_FRIEND|")) {
                            String[] parts = message.split("\\|");
                            int userIdReq = Integer.parseInt(parts[1]); // Người nhận lời mời
                            int senderId = Integer.parseInt(parts[2]); // Người gửi
                            boolean success = friendDAO.acceptFriendRequest(userIdReq, senderId); // Sử dụng method có sẵn
                            out.writeObject(success);
                            out.flush();
                        } else if (message.startsWith("REJECT_FRIEND|")) {
                            String[] parts = message.split("\\|");
                            int userIdReq = Integer.parseInt(parts[1]);
                            int senderId = Integer.parseInt(parts[2]);
                            boolean success = friendDAO.rejectFriendRequest(userIdReq, senderId); // Sử dụng method có sẵn
                            out.writeObject(success);
                            out.flush();
                        } else if (message.startsWith("GET_MESSAGES|")) {
                            String[] parts = message.split("\\|");
                            int reqUserId = Integer.parseInt(parts[1]);
                            int friendId = Integer.parseInt(parts[2]);
                            List<Message> messages = messageDAO.getMessagesByUserAndFriend(reqUserId, friendId); // Sử dụng method có sẵn
                            out.writeObject(messages);
                            out.flush();
                        } else if (message.startsWith("SEND_MESSAGE|")) {
                            String[] parts = message.split("\\|");
                            int senderId = Integer.parseInt(parts[1]);
                            int receiverId = Integer.parseInt(parts[2]);
                            String content = parts[3];
                            Message newMessage = new Message(0, senderId, receiverId, content, false, LocalDateTime.now());
                            boolean success = messageDAO.saveMessage(newMessage); // Sử dụng method có sẵn (check friend)
                            if (success) {
                                // Broadcast đến receiver
                                String broadcast = "[" + senderId + " -> " + receiverId + "]: " + content;
                                ObjectOutputStream receiverOut = clients.get(receiverId);
                                if (receiverOut != null) {
                                    receiverOut.writeObject(broadcast);
                                    receiverOut.flush();
                                }
                            }
                            out.writeObject(success);
                            out.flush();
                        } else if (message.startsWith("MARK_READ|")) {
                            int msgId = Integer.parseInt(message.split("\\|")[1]);
                            boolean success = messageDAO.markAsRead(msgId); // Sử dụng method có sẵn
                            out.writeObject(success);
                            out.flush();
                        } else if (message.contains(":")) { // Chat real-time (nếu không phải command)
                            String[] parts = message.split(":");
                            if (parts.length >= 3) {
                                int senderId = Integer.parseInt(parts[0]);
                                int receiverId = Integer.parseInt(parts[1]);
                                String content = parts[2];
                                String broadcastMessage = "[" + senderId + " -> " + receiverId + "]: " + content;
                                ObjectOutputStream receiverOut = clients.get(receiverId);
                                if (receiverOut != null) {
                                    receiverOut.writeObject(broadcastMessage);
                                    receiverOut.flush();
                                }
                            }
                        } else {
                            out.writeObject("ERROR|Unknown command");
                            out.flush();
                        }
                    }
                }
            } catch (Exception e) {
                System.out.println("Error handling client " + userId + ": " + e.getMessage());
                e.printStackTrace();
                if (userId != -1) {
                    clients.remove(userId);
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