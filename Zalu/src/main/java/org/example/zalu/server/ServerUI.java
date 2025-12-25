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
    private static final ObservableList<org.example.zalu.model.ClientErrorLog> errorLogData = FXCollections
            .observableArrayList();

    private Label totalMsgLabel;
    private Label totalBytesLabel;

    @Override
    public void start(Stage stage) {
        TabPane tabPane = new TabPane();
        tabPane.setStyle("-fx-background-color: #f5f5f5;");

        // --- TAB 1: DASHBOARD ---
        Tab dashboardTab = new Tab("Dashboard");
        dashboardTab.setClosable(false);
        dashboardTab.setContent(createDashboardContent());

        // --- TAB 2: STATISTICS ---
        Tab statsTab = new Tab("Thống kê Hệ thống");
        statsTab.setClosable(false);
        statsTab.setContent(createStatisticsContent());

        // --- TAB 3: CLIENT ERRORS ---
        Tab errorTab = new Tab("Báo cáo Lỗi Client");
        errorTab.setClosable(false);
        errorTab.setContent(createErrorReportContent());

        tabPane.getTabs().addAll(dashboardTab, statsTab, errorTab);

        // Đăng ký callback
        ChatServer.setActivityCallback(this::addActivity);
        ChatServer.setUserListUpdateCallback(this::updateAccountTable);
        ChatServer.setErrorReportingCallback(this::addErrorLog);

        Scene scene = new Scene(tabPane, 1280, 720); // Increased size for better visibility
        stage.setTitle("Zalu Server Monitor");
        stage.setScene(scene);
        stage.show();
    }

    private VBox createDashboardContent() {
        // Title với Toggle Switch
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPadding(new Insets(10, 20, 10, 20));

        Label titleLabel = new Label("Server Zalu Control Panel");
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

        // Bảng Account Online với ScrollPane
        accountTable = createAccountTable();
        ScrollPane accountScrollPane = new ScrollPane(accountTable);
        accountScrollPane.setFitToWidth(true);
        accountScrollPane.setFitToHeight(true);
        accountScrollPane.setStyle("-fx-background-color: transparent;");
        VBox accountBox = new VBox(10, new Label("Account Online"), accountScrollPane);
        accountBox.getChildren().get(0).setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        accountBox.setPadding(new Insets(15));
        accountBox.setPrefWidth(450);
        VBox.setVgrow(accountScrollPane, Priority.ALWAYS);

        // Bảng Hoạt động với ScrollPane
        activityTable = createActivityTable();
        ScrollPane activityScrollPane = new ScrollPane(activityTable);
        activityScrollPane.setFitToWidth(true);
        activityScrollPane.setFitToHeight(true);
        activityScrollPane.setStyle("-fx-background-color: transparent;");
        VBox activityBox = new VBox(10, new Label("Nhật ký Hoạt động"), activityScrollPane);
        activityBox.getChildren().get(0)
                .setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");
        activityBox.setPadding(new Insets(15));
        activityBox.setPrefWidth(750);
        VBox.setVgrow(activityScrollPane, Priority.ALWAYS);

        // Layout bảng với tăng trưởng động
        HBox tablesLayout = new HBox(15, accountBox, activityBox);
        tablesLayout.setPadding(new Insets(10));
        HBox.setHgrow(accountBox, Priority.SOMETIMES);
        HBox.setHgrow(activityBox, Priority.ALWAYS); // Nhật ký giãn nở hết mức
        VBox.setVgrow(tablesLayout, Priority.ALWAYS);

        // Announcement Box
        HBox announceBox = new HBox(10);
        TextField announceField = new TextField();
        announceField.setPromptText("Nhập thông báo toàn server...");
        announceField.setPrefWidth(400);
        Button sendAnnounceBtn = new Button("Gửi thông báo");
        sendAnnounceBtn.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-font-weight: bold;");
        sendAnnounceBtn.setOnAction(e -> {
            String content = announceField.getText().trim();
            if (!content.isEmpty()) {
                ChatServer.sendSystemAnnouncement(content);
                announceField.clear();
                Alert info = new Alert(Alert.AlertType.INFORMATION);
                info.setTitle("Thành công");
                info.setHeaderText(null);
                info.setContentText("Đã gửi thông báo đến tất cả client!");
                info.show();
            }
        });
        announceBox.getChildren().addAll(announceField, sendAnnounceBtn);
        announceBox.setAlignment(Pos.CENTER_LEFT);
        announceBox.setPadding(new Insets(10, 20, 10, 20));

        VBox content = new VBox(5, titleBox, announceBox, tablesLayout);
        content.setPadding(new Insets(10));
        return content;
    }

    private VBox createStatisticsContent() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(30));
        container.setAlignment(Pos.TOP_CENTER);
        container.setStyle("-fx-background-color: #f8f9fa;");

        Label title = new Label("📊 Thống kê Hiệu suất Server");
        title.setStyle("-fx-font-size: 32px; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");

        // Card style for stats
        String cardStyle = "-fx-background-color: white; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 15, 0, 0, 3); " +
                "-fx-background-radius: 12; " +
                "-fx-padding: 25;";

        // Row 1: Messages, Files, Data Transfer
        HBox row1 = new HBox(20);
        row1.setAlignment(Pos.CENTER);

        // Card 1: Messages
        VBox msgCard = createStatCard("💬", "Tổng tin nhắn", "0", "#3498db", cardStyle);
        totalMsgLabel = (Label) ((VBox) msgCard.getChildren().get(1)).getChildren().get(0);

        // Card 2: Files
        VBox filesCard = createStatCard("📁", "Tổng file gửi", "0", "#9b59b6", cardStyle);
        Label totalFilesLabel = (Label) ((VBox) filesCard.getChildren().get(1)).getChildren().get(0);

        // Card 3: Bandwidth
        VBox dataCard = createStatCard("📊", "Lưu lượng Data", "0 KB", "#2ecc71", cardStyle);
        totalBytesLabel = (Label) ((VBox) dataCard.getChildren().get(1)).getChildren().get(0);

        row1.getChildren().addAll(msgCard, filesCard, dataCard);

        // Row 2: Users, Groups, Peak Users
        HBox row2 = new HBox(20);
        row2.setAlignment(Pos.CENTER);

        // Card 4: Total Users
        VBox usersCard = createStatCard("👥", "Tổng Users", "0", "#e74c3c", cardStyle);
        Label totalUsersLabel = (Label) ((VBox) usersCard.getChildren().get(1)).getChildren().get(0);

        // Card 5: Total Groups
        VBox groupsCard = createStatCard("👨‍👩‍👧‍👦", "Tổng Groups", "0", "#f39c12", cardStyle);
        Label totalGroupsLabel = (Label) ((VBox) groupsCard.getChildren().get(1)).getChildren().get(0);

        // Card 6: Peak Concurrent
        VBox peakCard = createStatCard("🔥", "Peak Concurrent", "0", "#e67e22", cardStyle);
        Label peakUsersLabel = (Label) ((VBox) peakCard.getChildren().get(1)).getChildren().get(0);

        row2.getChildren().addAll(usersCard, groupsCard, peakCard);

        // Row 3: Server Info
        HBox row3 = new HBox(20);
        row3.setAlignment(Pos.CENTER);

        // Card 7: Online Users
        VBox onlineCard = createStatCard("🟢", "Online hiện tại", "0", "#1abc9c", cardStyle);
        Label onlineUsersLabel = (Label) ((VBox) onlineCard.getChildren().get(1)).getChildren().get(0);

        // Card 8: Server Uptime
        VBox uptimeCard = createStatCard("⏱️", "Server Uptime", "00:00:00", "#34495e", cardStyle);
        Label uptimeLabel = (Label) ((VBox) uptimeCard.getChildren().get(1)).getChildren().get(0);

        row3.getChildren().addAll(onlineCard, uptimeCard);

        // Update thread cho các label động
        Thread statsThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        // Messages
                        if (totalMsgLabel != null) {
                            totalMsgLabel.setText(String.format("%,d", ChatServer.TOTAL_MESSAGES_SENT.get()));
                        }

                        // Files
                        if (totalFilesLabel != null) {
                            totalFilesLabel.setText(String.format("%,d", ChatServer.TOTAL_FILES_SENT.get()));
                        }

                        // Bandwidth
                        if (totalBytesLabel != null) {
                            long bytes = ChatServer.TOTAL_BYTES_TRANSFERRED.get();
                            totalBytesLabel.setText(formatBytes(bytes));
                        }

                        // Total Users
                        if (totalUsersLabel != null) {
                            try {
                                int totalUsers = ChatServer.getUserDAO().getTotalUserCount();
                                totalUsersLabel.setText(String.format("%,d", totalUsers));
                            } catch (Exception e) {
                                totalUsersLabel.setText("N/A");
                            }
                        }

                        // Total Groups
                        if (totalGroupsLabel != null) {
                            try {
                                int totalGroups = ChatServer.getGroupDAO().getTotalGroupCount();
                                totalGroupsLabel.setText(String.format("%,d", totalGroups));
                            } catch (Exception e) {
                                totalGroupsLabel.setText("N/A");
                            }
                        }

                        // Peak Concurrent Users
                        if (peakUsersLabel != null) {
                            peakUsersLabel.setText(String.format("%,d", ChatServer.PEAK_CONCURRENT_USERS.get()));
                        }

                        // Online Users
                        if (onlineUsersLabel != null) {
                            onlineUsersLabel.setText(String.format("%,d", ChatServer.getCurrentOnlineUsers()));
                        }

                        // Server Uptime
                        if (uptimeLabel != null) {
                            java.time.LocalDateTime startTime = ChatServer.getServerStartTime();
                            if (startTime != null) {
                                java.time.Duration uptime = java.time.Duration.between(startTime,
                                        java.time.LocalDateTime.now());
                                uptimeLabel.setText(formatDuration(uptime));
                            } else {
                                uptimeLabel.setText("Chưa khởi động");
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        statsThread.setDaemon(true);
        statsThread.start();

        container.getChildren().addAll(title, row1, row2, row3);
        return container;
    }

    private VBox createStatCard(String icon, String label, String initialValue, String color, String cardStyle) {
        VBox card = new VBox(10);
        card.setStyle(cardStyle);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(280);
        card.setPrefHeight(140);

        // Icon + Label row
        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER);

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 28px;");

        Label titleLabel = new Label(label);
        titleLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 14px; -fx-font-weight: 600;");

        headerRow.getChildren().addAll(iconLabel, titleLabel);

        // Value
        VBox valueBox = new VBox();
        valueBox.setAlignment(Pos.CENTER);

        Label valueLabel = new Label(initialValue);
        valueLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 36px; -fx-font-weight: bold;", color));

        valueBox.getChildren().add(valueLabel);

        card.getChildren().addAll(headerRow, valueBox);
        return card;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        else if (bytes < 1024 * 1024)
            return String.format("%.2f KB", bytes / 1024.0);
        else if (bytes < 1024 * 1024 * 1024)
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        else
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatDuration(java.time.Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;

        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
    }

    private TableView<ServerModels.OnlineUser> createAccountTable() {
        TableView<ServerModels.OnlineUser> table = new TableView<>(onlineUserData);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<ServerModels.OnlineUser, Integer> idColumn = new TableColumn<>("Id User");
        idColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getUserId())
                        .asObject());
        idColumn.setPrefWidth(150);
        idColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<ServerModels.OnlineUser, String> statusColumn = new TableColumn<>("Trạng thái (On/Off)");
        statusColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getStatus()));
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

        TableColumn<ServerModels.OnlineUser, Void> actionCol = new TableColumn<>("Hành động");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(param -> new TableCell<>() {
            private final Button kickBtn = new Button("Đá");

            {
                kickBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                kickBtn.setOnAction(event -> {
                    ServerModels.OnlineUser user = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Xác nhận");
                    alert.setHeaderText("Đá người dùng?");
                    alert.setContentText("Bạn có chắc muốn đá user ID " + user.getUserId() + " ra khỏi server?");

                    if (alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        ChatServer.kickUser(user.getUserId());
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    ServerModels.OnlineUser user = getTableView().getItems().get(getIndex());
                    // Chỉ hiển thị nút Kick nếu user đang ON
                    if ("ON".equals(user.getStatus())) {
                        setGraphic(kickBtn);
                    } else {
                        setGraphic(null);
                    }
                }
            }
        });

        table.getColumns().setAll(idColumn, statusColumn, actionCol);
        return table;
    }

    private TableView<ServerModels.ActivityRecord> createActivityTable() {
        TableView<ServerModels.ActivityRecord> table = new TableView<>(activityData);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<ServerModels.ActivityRecord, Integer> activeUserColumn = new TableColumn<>("User (chủ động)");
        activeUserColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getActiveUserId()));
        activeUserColumn.setPrefWidth(120);
        activeUserColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<ServerModels.ActivityRecord, Integer> passiveUserColumn = new TableColumn<>("User (bị động)");
        passiveUserColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleObjectProperty<>(cellData.getValue().getPassiveUserId()));
        passiveUserColumn.setPrefWidth(120);
        passiveUserColumn.setStyle("-fx-alignment: CENTER;");

        TableColumn<ServerModels.ActivityRecord, String> actionColumn = new TableColumn<>("user chủ động làm gì");
        actionColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getAction()));
        actionColumn.setPrefWidth(200);

        TableColumn<ServerModels.ActivityRecord, String> contentColumn = new TableColumn<>("Nội dung");
        contentColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleStringProperty(cellData.getValue().getContent()));
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
                            "-fx-border-radius: 15;");
        } else {
            serverToggle.setText("OFF");
            serverToggle.setStyle(
                    "-fx-background-color: #ecf0f1; " +
                            "-fx-background-radius: 15; " +
                            "-fx-text-fill: #2c3e50; " +
                            "-fx-font-weight: bold; " +
                            "-fx-border-color: #bdc3c7; " +
                            "-fx-border-radius: 15; " +
                            "-fx-border-width: 1;");
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
            Integer passiveUserId = null;
            if (activity.getTargetUserId() > 0) {
                passiveUserId = activity.getTargetUserId();
            } else if (activity.getGroupId() > 0) {
                passiveUserId = activity.getGroupId();
            }
            String content = activity.getEncryptedContent() != null ? activity.getEncryptedContent() : "";
            activityData.add(new ServerModels.ActivityRecord(activity.getUserId(), passiveUserId, action, content));

            // Giữ tối đa 1000 hoạt động
            if (activityData.size() > 1000) {
                activityData.remove(0);
            }
        });
    }

    private VBox createErrorReportContent() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #f8f9fa;");

        // Header
        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🐛 Báo cáo Lỗi từ Client");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("Xóa hết");
        clearBtn.setStyle(
                "-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-padding: 8 20;");
        clearBtn.setOnAction(e -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
            confirm.setTitle("Xác nhận");
            confirm.setHeaderText("Xóa tất cả báo cáo lỗi?");
            confirm.setContentText("Hành động này không thể hoàn tác.");
            if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                errorLogData.clear();
            }
        });

        headerBox.getChildren().addAll(title, spacer, clearBtn);

        // Table with ScrollPane
        TableView<org.example.zalu.model.ClientErrorLog> table = new TableView<>(errorLogData);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<org.example.zalu.model.ClientErrorLog, String> timeCol = new TableColumn<>("Thời gian");
        timeCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"))));
        timeCol.setPrefWidth(120);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> userCol = new TableColumn<>("Người dùng");
        userCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getUsername() + " (ID: " + cell.getValue().getUserId() + ")"));
        userCol.setPrefWidth(150);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> msgCol = new TableColumn<>("Nội dung lỗi");
        msgCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getErrorMessage()));
        msgCol.setPrefWidth(400);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> osCol = new TableColumn<>("Hệ điều hành");
        osCol.setCellValueFactory(cell -> new javafx.beans.property.SimpleStringProperty(
                cell.getValue().getOsInfo()));
        osCol.setPrefWidth(150);

        TableColumn<org.example.zalu.model.ClientErrorLog, Void> actionCol = new TableColumn<>("Chi tiết");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Xem");
            {
                btn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
                btn.setOnAction(e -> {
                    org.example.zalu.model.ClientErrorLog log = getTableView().getItems().get(getIndex());
                    Alert alert = new Alert(Alert.AlertType.ERROR);
                    alert.setTitle("Chi tiết Lỗi");
                    alert.setHeaderText(log.getErrorMessage());

                    TextArea textArea = new TextArea(log.getStackTrace());
                    textArea.setEditable(false);
                    textArea.setWrapText(false);
                    textArea.setMaxWidth(Double.MAX_VALUE);
                    textArea.setMaxHeight(Double.MAX_VALUE);
                    GridPane.setVgrow(textArea, Priority.ALWAYS);
                    GridPane.setHgrow(textArea, Priority.ALWAYS);

                    GridPane expContent = new GridPane();
                    expContent.setMaxWidth(Double.MAX_VALUE);
                    expContent.add(new Label("Stack Trace:"), 0, 0);
                    expContent.add(textArea, 0, 1);

                    alert.getDialogPane().setExpandableContent(expContent);
                    alert.getDialogPane().setExpanded(true);
                    alert.showAndWait();
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : btn);
            }
        });

        table.getColumns().setAll(timeCol, userCol, msgCol, osCol, actionCol);

        // Wrap table in ScrollPane
        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(tableScrollPane, Priority.ALWAYS);

        container.getChildren().addAll(headerBox, tableScrollPane);
        return container;
    }

    private void addErrorLog(org.example.zalu.model.ClientErrorLog log) {
        Platform.runLater(() -> {
            errorLogData.add(0, log); // Add to top
            if (errorLogData.size() > 100) { // Keep max 100
                errorLogData.remove(errorLogData.size() - 1);
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
            case "KICK":
                return "bị admin đá";
            default:
                return activityType;
        }
    }
}
