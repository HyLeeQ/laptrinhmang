package org.example.zalu.controller;

import org.example.zalu.model.Message;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.time.LocalDateTime;

public class ChatClient {
    private Socket socket;
    private ObjectInputStream in;
    private ObjectOutputStream out;

    public ChatClient(String serverAddress, int port) throws Exception{
        try{
            socket = new Socket(serverAddress, port);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            System.out.println("Connected to server at " + serverAddress + ": " + port);
            System.out.println("Connected to server at " + serverAddress + ":" + port);
        }catch (IOException e) {
            System.out.println("Failed to connect to server: ");
            e.printStackTrace();
            throw e;
        }

    }

    public void sendMessage(Message message) throws Exception{
        out.writeObject(message);
        String response = (String) in.readObject();
        System.out.println("Server response: " + response);
    }

    public void close() throws Exception{
        socket.close();
    }

    public static void main(String[] args){
        try{
            ChatClient client = new ChatClient("localhost", 5000);
            // send tn mau
            Message message = new Message(0,5, 6,"Hello from client!", LocalDateTime.now());
            client.sendMessage(message);
            client.close();
        } catch (Exception e) {
            System.out.println("Client error: ");
            e.printStackTrace();
        }
    }
}
