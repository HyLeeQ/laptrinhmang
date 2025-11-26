package org.example.zalu.client;

import javafx.stage.Stage;

public class LoginSession {
    private static Stage pendingStage = null;
    private static String pendingUsername = null;
    private static Runnable onLoginSuccessCallback = null;

    public static void setPendingStage(Stage stage) {
        pendingStage = stage;
    }

    public static Stage getStage() {
        return pendingStage;
    }

    public static void setPendingUsername(String username) {
        pendingUsername = username;
    }

    public static String getUsername() {
        return pendingUsername;
    }

    public static void setOnLoginSuccess(Runnable callback) {
        onLoginSuccessCallback = callback;
    }

    public static void triggerLoginSuccess() {
        if (onLoginSuccessCallback != null) {
            onLoginSuccessCallback.run();
        }
    }

    public static void clear() {
        pendingStage = null;
        pendingUsername = null;
        onLoginSuccessCallback = null;
    }
}