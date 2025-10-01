package org.example.zalu.client;

import org.example.zalu.HelloApplication;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;

public class ChatClient {
    private static final String SERVER_ADDRESS = "172.16.0.243";
    private static final int SERVER_PORT = 5000;
    private static ObjectOutputStream out;
    private static ObjectInputStream in;
    private static Socket socket;
    private static volatile boolean connected = false;

    public static void main(String[] args) {
        if (connectToServer()) {
            System.out.println("Connected to server at " + SERVER_ADDRESS + ":" + SERVER_PORT + ". Launching UI...");
            HelloApplication.launch(HelloApplication.class, args);
        } else {
            System.out.println("Failed to connect to server. Exiting.");
        }
    }

    private static boolean connectToServer() {
        try {
            socket = new Socket(SERVER_ADDRESS, SERVER_PORT);
            out = new ObjectOutputStream(socket.getOutputStream());
            out.flush();
            in = new ObjectInputStream(socket.getInputStream());
            connected = true;
            System.out.println("Server connection successful");
            // Không start listener ở đây

            return true;
        } catch (IOException e) {
            System.out.println("Client error connecting to " + SERVER_ADDRESS + ":" + SERVER_PORT + ": " + e.getMessage());
            return false;
        }
    }

    public static ObjectOutputStream getOut() {
        return out;
    }

    public static ObjectInputStream getIn() {
        return in;
    }

    public static void disconnect() {
        connected = false;
        try {
            if (out != null) out.close();
            if (in != null) in.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            System.out.println("Error disconnecting: " + e.getMessage());
        }
    }
}