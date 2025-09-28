package org.example.zalu.controller;

import org.example.zalu.model.DBConnection;
import org.example.zalu.model.Message;
import org.example.zalu.model.dao.MessageDAO;

import java.io.IOException;
import java.io.ObjectInput;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class ChatServer {
    private ServerSocket serverSocket;
    private boolean running;
    private MessageDAO messageDAO;

    public ChatServer(int port) throws SQLException{
        try{
            this.serverSocket = new ServerSocket(port);
            Connection connection = DBConnection.getConnection();
            this.messageDAO = new MessageDAO(connection);
            this.running = true;
            System.out.println("Server initialized on port " + port + " at " + LocalDateTime.now());
        } catch (IOException e) {
            System.out.println("Failed to initialize server on port " + port + ": " + e.getMessage());
            throw new SQLException("Server intinilization failed", e);
        }

    }

    public void start(){
        System.out.println("Server started on port " + serverSocket.getLocalPort() + " at " + LocalDateTime.now());
        while (running) {
            try{
                Socket clientSocket = serverSocket.accept();
                System.out.println("New client connected: " + clientSocket.getInetAddress());
                handleClient(clientSocket);
            }catch (Exception e) {
                System.out.println("Error accepting client: " + e.getMessage());
            }
        }
    }

    private void handleClient(Socket clientSocket) {
        new Thread(() -> {
            try (ObjectInputStream in = new ObjectInputStream(clientSocket.getInputStream());
                 ObjectOutputStream out = new ObjectOutputStream(clientSocket.getOutputStream())) {

                while(true){
                    //Nhan tin nhan tu client
                    Message message = (Message) in.readObject();
                    if (message == null) break;

                    //Lua tn vao csdl
                    message.setCreatedAt(LocalDateTime.now());
                    if (messageDAO.saveMessage(message)) {
                        out.writeObject("Message sent successfully");
                    }else{
                        out.writeObject("Failed to send message");
                    }
                }
            }catch (Exception e){
                System.out.println("Error handling client: " + e.getMessage());
            }finally {
                try{
                    clientSocket.close();
                }catch (Exception e) {
                    System.out.println("Error closing client socket: " + e.getMessage());
                }
            }
        }).start();
    }
    public void stop(){
        running = false;
        try{
            serverSocket.close();
        }catch (Exception e) {
            System.out.println("Error stopping server: " + e.getMessage());
        }
    }
    public static void main(String[] args){
        try {
            ChatServer server = new ChatServer(5000);
            server.start();
        }catch (SQLException e){
            System.out.println("Database error: " + e.getMessage());
        }
    }
}
