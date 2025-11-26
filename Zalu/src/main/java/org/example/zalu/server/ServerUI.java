package org.example.zalu.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import org.example.zalu.model.UserActivity;

/**
 * Giao diện quản lý Server Zalu
 */
public class ServerUI extends Application {
    private TableView<ServerModels.OnlineUser> accountTable;
    private TableView<ServerModels.ActivityRecord> activityTable;
    private ToggleButton serverToggle;
    
    private static final ObservableList<ServerModels.OnlineUser> onlineUserData = FXCollections.observableArrayList();
    private static final ObservableList<ServerModels.ActivityRecord> activityData = FXCollections.observableArrayList();
    
    @Override
    public void start(Stage stage) {
        // Title với Toggle Switch
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPadding(new Insets(10, 20, 10, 20));
        
        Label titleLabel = new Label("Server Zalu");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        
        // Toggle Switch
        serverToggle = new ToggleButton();
        serverToggle.setPrefWidth(80);
        serverToggle.setPrefHeight(30);
        updateToggleStyle(false);
        
        serverToggle.setOnAction(e -> {
            boolean isSelected = serverToggle.isSelected();
            updateToggleStyle(isSelected);
            
            if (isSelected) {
                ChatServer.startServer();
            } else {
                ChatServer.stopServer();
            }
        });
        
        titleBox.getChildren().addAll(titleLabel, serverToggle);
        
        // Bảng Account Online
        accountTable = createAccountTable();
        VBox accountBox = new VBox(10, new Label("Account Online"), accountTable);
        accountBox.getChildren().get(0).setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        accountBox.setPadding(new Insets(15));
        accountBox.setPrefWidth(400);
        
        // Bảng Hoạt động
        activityTable = createActivityTable();
        VBox activityBox = new VBox(10, new Label("Hoạt động"), activityTable);
        activityBox.getChildren().get(0).setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        activityBox.setPadding(new Insets(15));
        activityBox.setPrefWidth(650);
        
        // Layout chính
        HBox mainLayout = new HBox(10, accountBox, activityBox);
        mainLayout.setPadding(new Insets(10));
        
        VBox root = new VBox(10, titleBox, mainLayout);
        root.setPadding(new Insets(10));
        root.setStyle("-fx-background-color: #f5f5f5;");
        
        // Đăng ký callback cho hoạt động
        ChatServer.setActivityCallback(this::addActivity);
        ChatServer.setUserListUpdateCallback(this::updateAccountTable);
        
        Scene scene = new Scene(root, 1100, 450);
        stage.setTitle("Zalu Server Monitor");
        stage.setScene(scene);
        stage.show();
    }
    
    private TableView<ServerModels.OnlineUser> createAccountTable() {
        TableView<ServerModels.OnlineUser> table = new TableView<>(onlineUserData);
        table.setPrefHeight(300);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");
        
        TableColumn<ServerModels.OnlineUser, Integer> idColumn = new TableColumn<>("Id User");
        idColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getUserId()).asObject());
        idColumn.setPrefWidth(150);
        idColumn.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<ServerModels.OnlineUser, String> statusColumn = new TableColumn<>("Trạng thái (On/Off)");
        statusColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));
        statusColumn.setPrefWidth(200);
        statusColumn.setStyle("-fx-alignment: CENTER;");
        statusColumn.setCellFactory(column -> new TableCell<ServerModels.OnlineUser, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if ("ON".equals(item)) {
                        setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    } else {
                        setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    }
                }
            }
        });
        
        table.getColumns().setAll(idColumn, statusColumn);
        return table;
    }
    
    private TableView<ServerModels.ActivityRecord> createActivityTable() {
        TableView<ServerModels.ActivityRecord> table = new TableView<>(activityData);
        table.setPrefHeight(300);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");
        
        TableColumn<ServerModels.ActivityRecord, Integer> activeUserColumn = new TableColumn<>("User (chủ động)");
        activeUserColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getActiveUserId()).asObject());
        activeUserColumn.setPrefWidth(120);
        activeUserColumn.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<ServerModels.ActivityRecord, Integer> passiveUserColumn = new TableColumn<>("User (bị động)");
        passiveUserColumn.setCellValueFactory(cellData -> {
            Integer passiveId = cellData.getValue().getPassiveUserId();
            return passiveId != null ? new javafx.beans.property.SimpleIntegerProperty(passiveId).asObject() : null;
        });
        passiveUserColumn.setPrefWidth(120);
        passiveUserColumn.setStyle("-fx-alignment: CENTER;");
        
        TableColumn<ServerModels.ActivityRecord, String> actionColumn = new TableColumn<>("user chủ động làm gì");
        actionColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getAction()));
        actionColumn.setPrefWidth(200);
        
        TableColumn<ServerModels.ActivityRecord, String> contentColumn = new TableColumn<>("Nội dung");
        contentColumn.setCellValueFactory(cellData -> 
            new javafx.beans.property.SimpleStringProperty(cellData.getValue().getContent()));
        contentColumn.setPrefWidth(200);
        
        table.getColumns().setAll(activeUserColumn, passiveUserColumn, actionColumn, contentColumn);
        return table;
    }
    
    private void updateToggleStyle(boolean isOn) {
        if (isOn) {
            serverToggle.setText("ON");
            serverToggle.setStyle(
                "-fx-background-color: #ff6b35; " +
                "-fx-background-radius: 15; " +
                "-fx-text-fill: white; " +
                "-fx-font-weight: bold; " +
                "-fx-border-color: #ff6b35; " +
                "-fx-border-radius: 15;"
            );
        } else {
            serverToggle.setText("OFF");
            serverToggle.setStyle(
                "-fx-background-color: #ecf0f1; " +
                "-fx-background-radius: 15; " +
                "-fx-text-fill: #2c3e50; " +
                "-fx-font-weight: bold; " +
                "-fx-border-color: #bdc3c7; " +
                "-fx-border-radius: 15; " +
                "-fx-border-width: 1;"
            );
        }
    }
    
    private void updateAccountTable() {
        Platform.runLater(() -> {
            onlineUserData.clear();
            ChatServer.getOnlineUsers().forEach((id, name) -> {
                onlineUserData.add(new ServerModels.OnlineUser(id, "ON"));
            });
        });
    }
    
    private void addActivity(UserActivity activity) {
        Platform.runLater(() -> {
            String action = getActionDescription(activity.getActivityType());
            Integer passiveUserId = activity.getTargetUserId() > 0 ? activity.getTargetUserId() : 
                                  (activity.getGroupId() > 0 ? activity.getGroupId() : null);
            String content = activity.getEncryptedContent() != null ? activity.getEncryptedContent() : "";
            activityData.add(new ServerModels.ActivityRecord(activity.getUserId(), passiveUserId, action, content));
            
            // Giữ tối đa 1000 hoạt động
            if (activityData.size() > 1000) {
                activityData.remove(0);
            }
        });
    }
    
    private String getActionDescription(String activityType) {
        switch (activityType) {
            case "MESSAGE":
                return "gửi tin nhắn";
            case "GROUP_MESSAGE":
                return "gửi tin nhắn nhóm";
            case "FILE":
                return "gửi file";
            case "GROUP_FILE":
                return "gửi file nhóm";
            case "LOGIN":
                return "đăng nhập";
            case "LOGOUT":
                return "đăng xuất";
            case "UPDATE_PROFILE":
                return "chỉnh sửa thông tin cá nhân";
            default:
                return activityType;
        }
    }
}

