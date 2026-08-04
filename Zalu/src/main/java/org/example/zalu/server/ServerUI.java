package org.example.zalu.server;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;
import org.example.zalu.dao.GroupDAO;
import org.example.zalu.dao.MessageDAO;
import org.example.zalu.model.User;
import org.example.zalu.model.UserActivity;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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

    // Stats tab charts
    private Canvas barChartCanvas;
    private TableView<ActiveUserEntry> topUsersTable;
    private static final ObservableList<ActiveUserEntry> topUsersData = FXCollections.observableArrayList();

    // User management tab
    private TableView<AdminUserEntry> userMgmtTable;
    private static final ObservableList<AdminUserEntry> userMgmtData = FXCollections.observableArrayList();

    // Group management tab
    private TableView<AdminGroupEntry> groupMgmtTable;
    private static final ObservableList<AdminGroupEntry> groupMgmtData = FXCollections.observableArrayList();

    // Report management tab
    private TableView<AdminReportEntry> reportMgmtTable;
    private static final ObservableList<AdminReportEntry> reportMgmtData = FXCollections.observableArrayList();

    // Chart data cache
    private List<long[]> chartData = new ArrayList<>();


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

        // --- TAB 3: USER MANAGEMENT ---
        Tab userMgmtTab = new Tab("Quản lý Người dùng");
        userMgmtTab.setClosable(false);
        userMgmtTab.setContent(createUserManagementContent());
        // Load data when tab selected
        userMgmtTab.setOnSelectionChanged(e -> {
            if (userMgmtTab.isSelected()) loadAllUsers();
        });

        // --- TAB 4: GROUP MANAGEMENT ---
        Tab groupMgmtTab = new Tab("Quản lý Nhóm chat");
        groupMgmtTab.setClosable(false);
        groupMgmtTab.setContent(createGroupManagementContent());
        groupMgmtTab.setOnSelectionChanged(e -> {
            if (groupMgmtTab.isSelected()) loadAllGroups();
        });

        // --- TAB 5: CLIENT ERRORS ---
        Tab errorTab = new Tab("Báo cáo Lỗi Client");
        errorTab.setClosable(false);
        errorTab.setContent(createErrorReportContent());

        // --- TAB 6: USER REPORTS ---
        Tab reportTab = new Tab("Quản lý Báo cáo");
        reportTab.setClosable(false);
        reportTab.setContent(createUserReportContent());
        reportTab.setOnSelectionChanged(e -> {
            if (reportTab.isSelected()) loadAllReports();
        });

        tabPane.getTabs().addAll(dashboardTab, statsTab, userMgmtTab, groupMgmtTab, reportTab, errorTab);

        // Đăng ký callback
        ChatServer.setActivityCallback(this::addActivity);
        ChatServer.setUserListUpdateCallback(this::updateAccountTable);
        ChatServer.setErrorReportingCallback(this::addErrorLog);

        Scene scene = new Scene(tabPane, 1400, 800);
        java.net.URL cssUrl = getClass().getResource("/css/server-theme.css");
        if (cssUrl != null) {
            scene.getStylesheets().add(cssUrl.toExternalForm());
        }
        stage.setTitle("Zalu Server Monitor");
        stage.setScene(scene);
        stage.show();
    }

    // =========================================================
    // DASHBOARD TAB
    // =========================================================
    private VBox createDashboardContent() {
        HBox titleBox = new HBox(15);
        titleBox.setAlignment(Pos.CENTER_LEFT);
        titleBox.setPadding(new Insets(10, 20, 10, 20));

        Label titleLabel = new Label("Server Zalu Control Panel");
        titleLabel.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        serverToggle = new ToggleButton();
        serverToggle.setPrefWidth(80);
        serverToggle.setPrefHeight(30);
        updateToggleStyle(false);

        serverToggle.setOnAction(e -> {
            boolean isSelected = serverToggle.isSelected();
            updateToggleStyle(isSelected);
            if (isSelected) ChatServer.startServer();
            else ChatServer.stopServer();
        });

        titleBox.getChildren().addAll(titleLabel, serverToggle);

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

        HBox tablesLayout = new HBox(15, accountBox, activityBox);
        tablesLayout.setPadding(new Insets(10));
        HBox.setHgrow(accountBox, Priority.SOMETIMES);
        HBox.setHgrow(activityBox, Priority.ALWAYS);
        VBox.setVgrow(tablesLayout, Priority.ALWAYS);

        HBox announceBox = new HBox(10);
        TextField announceField = new TextField();
        announceField.setPromptText("Nhập thông báo toàn server...");
        announceField.setPrefWidth(400);
        Button sendAnnounceBtn = new Button("Gửi thông báo");
        sendAnnounceBtn.getStyleClass().add("action-btn");
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

    // =========================================================
    // STATISTICS TAB (upgraded)
    // =========================================================
    private VBox createStatisticsContent() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #f8f9fa;");

        Label title = new Label("📊 Thống kê Hiệu suất Server");
        title.setStyle("-fx-font-size: 28px; -fx-text-fill: #2c3e50; -fx-font-weight: bold;");

        String cardStyle = "-fx-background-color: white; " +
                "-fx-effect: dropshadow(three-pass-box, rgba(0,0,0,0.15), 15, 0, 0, 3); " +
                "-fx-background-radius: 12; -fx-padding: 20;";

        // ── Stat Cards Row 1 ──────────────────────────────────
        HBox row1 = new HBox(15);
        row1.setAlignment(Pos.CENTER);

        VBox msgCard    = createStatCard("💬", "Tổng tin nhắn",    "0",      "#3498db", cardStyle);
        totalMsgLabel   = (Label) ((VBox) msgCard.getChildren().get(1)).getChildren().get(0);
        VBox filesCard  = createStatCard("📁", "Tổng file gửi",    "0",      "#9b59b6", cardStyle);
        Label totalFilesLabel  = (Label) ((VBox) filesCard.getChildren().get(1)).getChildren().get(0);
        VBox dataCard   = createStatCard("📊", "Lưu lượng Data",   "0 KB",   "#2ecc71", cardStyle);
        totalBytesLabel = (Label) ((VBox) dataCard.getChildren().get(1)).getChildren().get(0);
        row1.getChildren().addAll(msgCard, filesCard, dataCard);

        // ── Stat Cards Row 2 ──────────────────────────────────
        HBox row2 = new HBox(15);
        row2.setAlignment(Pos.CENTER);

        VBox usersCard  = createStatCard("👥", "Tổng Users",       "0",         "#e74c3c", cardStyle);
        Label totalUsersLabel  = (Label) ((VBox) usersCard.getChildren().get(1)).getChildren().get(0);
        VBox groupsCard = createStatCard("👨‍👩‍👧‍👦", "Tổng Groups",    "0",      "#f39c12", cardStyle);
        Label totalGroupsLabel = (Label) ((VBox) groupsCard.getChildren().get(1)).getChildren().get(0);
        VBox peakCard   = createStatCard("🔥", "Peak Concurrent",  "0",         "#e67e22", cardStyle);
        Label peakUsersLabel   = (Label) ((VBox) peakCard.getChildren().get(1)).getChildren().get(0);
        VBox onlineCard = createStatCard("🟢", "Online hiện tại",  "0",         "#1abc9c", cardStyle);
        Label onlineUsersLabel = (Label) ((VBox) onlineCard.getChildren().get(1)).getChildren().get(0);
        VBox uptimeCard = createStatCard("⏱️", "Server Uptime",    "00:00:00",  "#34495e", cardStyle);
        Label uptimeLabel      = (Label) ((VBox) uptimeCard.getChildren().get(1)).getChildren().get(0);
        row2.getChildren().addAll(usersCard, groupsCard, peakCard, onlineCard, uptimeCard);

        // ── Bar chart + Top users ─────────────────────────────
        HBox chartRow = new HBox(20);
        chartRow.setAlignment(Pos.TOP_LEFT);

        // Bar chart panel
        VBox chartPanel = new VBox(10);
        chartPanel.setStyle(cardStyle);
        chartPanel.setPrefWidth(680);
        chartPanel.setMinHeight(280);

        Label chartTitle = new Label("📈 Tin nhắn 7 ngày gần nhất");
        chartTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        barChartCanvas = new Canvas(660, 220);
        drawBarChart(new ArrayList<>()); // empty placeholder

        chartPanel.getChildren().addAll(chartTitle, barChartCanvas);

        // Top 5 users panel
        VBox topPanel = new VBox(10);
        topPanel.setStyle(cardStyle);
        topPanel.setPrefWidth(460);
        topPanel.setMinHeight(280);

        Label topTitle = new Label("🏆 Top 5 User hoạt động nhất");
        topTitle.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        topUsersTable = createTopUsersTable();
        VBox.setVgrow(topUsersTable, Priority.ALWAYS);
        topPanel.getChildren().addAll(topTitle, topUsersTable);

        chartRow.getChildren().addAll(chartPanel, topPanel);
        HBox.setHgrow(chartPanel, Priority.ALWAYS);

        // Refresh & Export buttons
        Button refreshStatsBtn = new Button("🔄 Làm mới biểu đồ");
        refreshStatsBtn.setStyle(
                "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 18; -fx-background-radius: 8;");
        refreshStatsBtn.setOnAction(e -> loadStatsChartData());

        Button exportStatsBtn = new Button("📥 Xuất báo cáo CSV");
        exportStatsBtn.setStyle(
                "-fx-background-color: #9b59b6; -fx-text-fill: white; -fx-font-weight: bold; " +
                "-fx-padding: 8 18; -fx-background-radius: 8;");
        exportStatsBtn.setOnAction(e -> exportStatisticsCSV());

        HBox statsActionBox = new HBox(15, refreshStatsBtn, exportStatsBtn);
        statsActionBox.setAlignment(Pos.CENTER_LEFT);

        // Background thread for auto-refresh every second
        Thread statsThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(() -> {
                        if (totalMsgLabel != null)
                            totalMsgLabel.setText(String.format("%,d", ChatServer.TOTAL_MESSAGES_SENT.get()));
                        if (totalFilesLabel != null)
                            totalFilesLabel.setText(String.format("%,d", ChatServer.TOTAL_FILES_SENT.get()));
                        if (totalBytesLabel != null)
                            totalBytesLabel.setText(formatBytes(ChatServer.TOTAL_BYTES_TRANSFERRED.get()));
                        if (totalUsersLabel != null) {
                            try {
                                totalUsersLabel.setText(String.format("%,d", ChatServer.getUserDAO().getTotalUserCount()));
                            } catch (Exception ex) { totalUsersLabel.setText("N/A"); }
                        }
                        if (totalGroupsLabel != null) {
                            try {
                                totalGroupsLabel.setText(String.format("%,d", ChatServer.getGroupDAO().getTotalGroupCount()));
                            } catch (Exception ex) { totalGroupsLabel.setText("N/A"); }
                        }
                        if (peakUsersLabel != null)
                            peakUsersLabel.setText(String.format("%,d", ChatServer.PEAK_CONCURRENT_USERS.get()));
                        if (onlineUsersLabel != null)
                            onlineUsersLabel.setText(String.format("%,d", ChatServer.getCurrentOnlineUsers()));
                        if (uptimeLabel != null) {
                            java.time.LocalDateTime start = ChatServer.getServerStartTime();
                            uptimeLabel.setText(start != null
                                    ? formatDuration(java.time.Duration.between(start, java.time.LocalDateTime.now()))
                                    : "Offline");
                        }
                    });
                } catch (InterruptedException e) { break; }
            }
        });
        statsThread.setDaemon(true);
        statsThread.start();

        // Load chart data once on startup (non-blocking)
        new Thread(() -> {
            try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            loadStatsChartData();
        }).start();

        VBox.setVgrow(chartRow, Priority.ALWAYS);
        container.getChildren().addAll(title, row1, row2, statsActionBox, chartRow);
        return container;
    }

    private void loadStatsChartData() {
        new Thread(() -> {
            try {
                MessageDAO msgDao = ChatServer.getMessageDAO();
                if (msgDao == null) return;
                List<long[]> data = msgDao.getMessageCountLastNDays(7);
                List<MessageDAO.ActiveUserRow> topUsers = msgDao.getTopActiveUsers(5);
                Platform.runLater(() -> {
                    chartData = data;
                    drawBarChart(data);
                    topUsersData.clear();
                    for (int i = 0; i < topUsers.size(); i++) {
                        MessageDAO.ActiveUserRow r = topUsers.get(i);
                        topUsersData.add(new ActiveUserEntry(i + 1, r.username, r.messageCount));
                    }
                });
            } catch (Exception ex) {
                System.err.println("Lỗi load stats chart: " + ex.getMessage());
            }
        }).start();
    }

    private void drawBarChart(List<long[]> data) {
        if (barChartCanvas == null) return;
        GraphicsContext gc = barChartCanvas.getGraphicsContext2D();
        double w = barChartCanvas.getWidth();
        double h = barChartCanvas.getHeight();

        // Background
        gc.setFill(Color.web("#f8f9fa"));
        gc.fillRect(0, 0, w, h);

        if (data == null || data.isEmpty()) {
            gc.setFill(Color.web("#95a5a6"));
            gc.setFont(Font.font("System", FontWeight.NORMAL, 14));
            gc.fillText("Không có dữ liệu (chưa có tin nhắn trong 7 ngày)", 120, h / 2);
            return;
        }

        double paddingL = 55, paddingR = 15, paddingT = 15, paddingB = 45;
        double chartW = w - paddingL - paddingR;
        double chartH = h - paddingT - paddingB;

        // Find max
        long maxVal = 1;
        for (long[] pair : data) if (pair[1] > maxVal) maxVal = pair[1];

        double barW = chartW / Math.max(data.size(), 1) * 0.6;
        double gap  = chartW / Math.max(data.size(), 1);

        // Y-axis grid lines
        gc.setStroke(Color.web("#ecf0f1"));
        gc.setLineWidth(1);
        int ySteps = 5;
        gc.setFont(Font.font("System", FontWeight.NORMAL, 11));
        for (int i = 0; i <= ySteps; i++) {
            double y = paddingT + chartH - (chartH * i / ySteps);
            gc.strokeLine(paddingL, y, w - paddingR, y);
            gc.setFill(Color.web("#7f8c8d"));
            gc.fillText(String.valueOf(maxVal * i / ySteps), 2, y + 4);
        }

        // Axes
        gc.setStroke(Color.web("#bdc3c7"));
        gc.setLineWidth(1.5);
        gc.strokeLine(paddingL, paddingT, paddingL, paddingT + chartH);
        gc.strokeLine(paddingL, paddingT + chartH, w - paddingR, paddingT + chartH);

        // Bars
        Color[] palette = {
            Color.web("#3498db"), Color.web("#2ecc71"), Color.web("#e74c3c"),
            Color.web("#9b59b6"), Color.web("#f39c12"), Color.web("#1abc9c"),
            Color.web("#e67e22")
        };
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM");

        for (int i = 0; i < data.size(); i++) {
            long epochDay = data.get(i)[0];
            long count    = data.get(i)[1];
            double barH   = (double) count / maxVal * chartH;
            double x      = paddingL + i * gap + (gap - barW) / 2;
            double y      = paddingT + chartH - barH;

            // Gradient effect: lighter top
            gc.setFill(palette[i % palette.length]);
            gc.fillRoundRect(x, y, barW, barH, 5, 5);

            // Value label on top of bar
            gc.setFill(Color.web("#2c3e50"));
            gc.setFont(Font.font("System", FontWeight.BOLD, 11));
            gc.fillText(String.valueOf(count), x + barW / 4, y - 4);

            // X-axis date label
            String label = LocalDate.ofEpochDay(epochDay).format(fmt);
            gc.setFill(Color.web("#7f8c8d"));
            gc.setFont(Font.font("System", FontWeight.NORMAL, 11));
            gc.fillText(label, x, paddingT + chartH + 16);
        }
    }

    private TableView<ActiveUserEntry> createTopUsersTable() {
        TableView<ActiveUserEntry> table = new TableView<>(topUsersData);
        table.setStyle("-fx-background-color: white;");
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        TableColumn<ActiveUserEntry, Integer> rankCol = new TableColumn<>("#");
        rankCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().rank).asObject());
        rankCol.setPrefWidth(40);
        rankCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<ActiveUserEntry, String> nameCol = new TableColumn<>("Username");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        nameCol.setStyle("-fx-alignment: CENTER-LEFT;");

        TableColumn<ActiveUserEntry, Long> countCol = new TableColumn<>("Số tin nhắn");
        countCol.setCellValueFactory(c -> new SimpleLongProperty(c.getValue().messageCount).asObject());
        countCol.setPrefWidth(110);
        countCol.setStyle("-fx-alignment: CENTER;");

        table.getColumns().setAll(rankCol, nameCol, countCol);
        return table;
    }

    // =========================================================
    // USER MANAGEMENT TAB
    // =========================================================
    private VBox createUserManagementContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #f8f9fa;");

        // Header
        Label title = new Label("👤 Quản lý Người dùng");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Search bar
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        TextField searchField = new TextField();
        searchField.setPromptText("Tìm theo username / họ tên / email...");
        searchField.setPrefWidth(350);
        // Styles are handled by CSS
        Button searchBtn = new Button("🔍 Tìm kiếm");
        searchBtn.getStyleClass().add("button");

        Button refreshBtn = new Button("↺ Tải lại");
        refreshBtn.getStyleClass().add("action-btn");

        Button exportUsersBtn = new Button("📥 Xuất CSV");
        exportUsersBtn.getStyleClass().add("button");
        exportUsersBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;"); // Keep purple for export
        exportUsersBtn.setOnAction(e -> exportUsersCSV());

        searchBar.getChildren().addAll(searchField, searchBtn, refreshBtn, exportUsersBtn);

        // Table
        userMgmtTable = createUserMgmtTable();
        ScrollPane sp = new ScrollPane(userMgmtTable);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        // Actions
        searchBtn.setOnAction(e -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) loadAllUsers();
            else searchUsers(q);
        });
        searchField.setOnAction(e -> searchBtn.fire());
        refreshBtn.setOnAction(e -> { searchField.clear(); loadAllUsers(); });

        container.getChildren().addAll(title, searchBar, sp);
        return container;
    }

    private void exportStatisticsCSV() {
        try {
            java.io.File file = new java.io.File("server_statistics_report.csv");
            try (java.io.FileWriter writer = new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                // Thêm BOM để Excel đọc đúng tiếng Việt
                writer.write('\ufeff');
                writer.write("Thống kê tổng quan\n");
                writer.write("Tổng tin nhắn," + ChatServer.TOTAL_MESSAGES_SENT.get() + "\n");
                writer.write("Tổng file," + ChatServer.TOTAL_FILES_SENT.get() + "\n");
                writer.write("Tổng user," + ChatServer.getUserDAO().getTotalUserCount() + "\n");
                writer.write("Tổng nhóm," + ChatServer.getGroupDAO().getTotalGroupCount() + "\n");
                
                writer.write("\nThống kê tin nhắn 7 ngày qua\n");
                writer.write("Ngày,Số lượng tin nhắn\n");
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
                for (long[] pair : chartData) {
                    writer.write(LocalDate.ofEpochDay(pair[0]).format(fmt) + "," + pair[1] + "\n");
                }
                
                writer.write("\nTop 5 người dùng hoạt động\n");
                writer.write("Hạng,Username,Số tin nhắn\n");
                for (ActiveUserEntry entry : topUsersData) {
                    writer.write(entry.rank + "," + entry.username + "," + entry.messageCount + "\n");
                }
            }
            showInfo("Đã xuất báo cáo thống kê ra file: " + file.getAbsolutePath());
        } catch (Exception e) {
            showError("Lỗi xuất file: " + e.getMessage());
        }
    }

    private void exportUsersCSV() {
        try {
            java.io.File file = new java.io.File("server_users_report.csv");
            try (java.io.FileWriter writer = new java.io.FileWriter(file, java.nio.charset.StandardCharsets.UTF_8)) {
                // Thêm BOM
                writer.write('\ufeff');
                writer.write("ID,Username,Họ và tên,Email,Ngày tạo,Trạng thái\n");
                for (AdminUserEntry u : userMgmtData) {
                    String status = u.locked ? "Bị khóa" : "Hoạt động";
                    writer.write(String.format("%d,%s,%s,%s,%s,%s\n", 
                        u.id, 
                        escapeCSV(u.username), 
                        escapeCSV(u.fullName), 
                        escapeCSV(u.email), 
                        u.createdAt, 
                        status));
                }
            }
            showInfo("Đã xuất danh sách người dùng ra file: " + file.getAbsolutePath());
        } catch (Exception e) {
            showError("Lỗi xuất file: " + e.getMessage());
        }
    }

    private String escapeCSV(String value) {
        if (value == null) return "";
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private TableView<AdminUserEntry> createUserMgmtTable() {
        TableView<AdminUserEntry> table = new TableView<>(userMgmtData);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<AdminUserEntry, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().id).asObject());
        idCol.setPrefWidth(55);
        idCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<AdminUserEntry, String> usernameCol = new TableColumn<>("Username");
        usernameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().username));
        usernameCol.setPrefWidth(130);

        TableColumn<AdminUserEntry, String> fullNameCol = new TableColumn<>("Họ và tên");
        fullNameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().fullName));
        fullNameCol.setPrefWidth(160);

        TableColumn<AdminUserEntry, String> emailCol = new TableColumn<>("Email");
        emailCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().email));
        emailCol.setPrefWidth(180);

        TableColumn<AdminUserEntry, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().status));
        statusCol.setPrefWidth(90);
        statusCol.setStyle("-fx-alignment: CENTER;");
        statusCol.setCellFactory(col -> new TableCell<AdminUserEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    setStyle("online".equalsIgnoreCase(item)
                            ? "-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-alignment: CENTER;"
                            : "-fx-text-fill: #95a5a6; -fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<AdminUserEntry, String> lockedCol = new TableColumn<>("Khóa?");
        lockedCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().locked ? "🔒 Khóa" : "✅ OK"));
        lockedCol.setPrefWidth(90);
        lockedCol.setStyle("-fx-alignment: CENTER;");
        lockedCol.setCellFactory(col -> new TableCell<AdminUserEntry, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    setStyle(item.startsWith("🔒")
                            ? "-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-alignment: CENTER;"
                            : "-fx-text-fill: #27ae60; -fx-alignment: CENTER;");
                }
            }
        });

        TableColumn<AdminUserEntry, String> createdCol = new TableColumn<>("Ngày tạo");
        createdCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt));
        createdCol.setPrefWidth(130);
        createdCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<AdminUserEntry, Void> actionCol = new TableColumn<>("Hành động");
        actionCol.setPrefWidth(220);
        actionCol.setCellFactory(col -> new TableCell<AdminUserEntry, Void>() {
            private final Button lockBtn   = new Button("Khóa");
            private final Button unlockBtn = new Button("Mở khóa");
            private final Button deleteBtn = new Button("Xóa");
            private final HBox   box       = new HBox(6, lockBtn, unlockBtn, deleteBtn);

            {
                lockBtn.getStyleClass().add("action-btn");
                unlockBtn.getStyleClass().add("action-btn");
                deleteBtn.getStyleClass().add("danger-btn");
                box.setAlignment(Pos.CENTER);

                lockBtn.setOnAction(ev -> {
                    AdminUserEntry u = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Khóa tài khoản \"" + u.username + "\"?", ButtonType.OK, ButtonType.CANCEL);
                    confirm.setTitle("Xác nhận khóa");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        boolean ok = ChatServer.lockUserAccount(u.id);
                        if (ok) { u.locked = true; getTableView().refresh(); showInfo("Đã khóa tài khoản " + u.username); }
                        else showError("Không thể khóa tài khoản. Kiểm tra kết nối DB.");
                    }
                });

                unlockBtn.setOnAction(ev -> {
                    AdminUserEntry u = getTableView().getItems().get(getIndex());
                    boolean ok = ChatServer.unlockUserAccount(u.id);
                    if (ok) { u.locked = false; getTableView().refresh(); showInfo("Đã mở khóa tài khoản " + u.username); }
                    else showError("Không thể mở khóa tài khoản.");
                });

                deleteBtn.setOnAction(ev -> {
                    AdminUserEntry u = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "XÓA VĨNH VIỄN tài khoản \"" + u.username + "\"?\nHành động không thể hoàn tác!",
                            ButtonType.OK, ButtonType.CANCEL);
                    confirm.setTitle("⚠️ Xác nhận xóa tài khoản");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        boolean ok = ChatServer.deleteUserAccount(u.id);
                        if (ok) { getTableView().getItems().remove(u); showInfo("Đã xóa tài khoản " + u.username); }
                        else showError("Không thể xóa tài khoản. Kiểm tra kết nối DB.");
                    }
                });
            }

            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) { setGraphic(null); }
                else {
                    AdminUserEntry u = getTableView().getItems().get(getIndex());
                    lockBtn.setVisible(!u.locked);
                    unlockBtn.setVisible(u.locked);
                    setGraphic(box);
                }
            }
        });

        table.getColumns().setAll(idCol, usernameCol, fullNameCol, emailCol, statusCol, lockedCol, createdCol, actionCol);
        return table;
    }

    private void loadAllUsers() {
        new Thread(() -> {
            try {
                List<User> users = ChatServer.getUserDAO().getAllUsers();
                List<AdminUserEntry> entries = new ArrayList<>();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                for (User u : users) {
                    boolean isOnline = ChatServer.getOnlineUsers().containsKey(u.getId());
                    entries.add(new AdminUserEntry(
                            u.getId(), u.getUsername(), u.getFullName(),
                            u.getEmail() != null ? u.getEmail() : "",
                            isOnline ? "online" : "offline",
                            u.isLocked(),
                            u.getCreatedAt() != null ? u.getCreatedAt().format(fmt) : ""));
                }
                Platform.runLater(() -> {
                    userMgmtData.setAll(entries);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Lỗi tải danh sách người dùng: " + ex.getMessage()));
            }
        }).start();
    }

    private void searchUsers(String query) {
        new Thread(() -> {
            try {
                List<User> users = ChatServer.getUserDAO().searchAllUsers(query);
                List<AdminUserEntry> entries = new ArrayList<>();
                DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                for (User u : users) {
                    boolean isOnline = ChatServer.getOnlineUsers().containsKey(u.getId());
                    entries.add(new AdminUserEntry(
                            u.getId(), u.getUsername(), u.getFullName(),
                            u.getEmail() != null ? u.getEmail() : "",
                            isOnline ? "online" : "offline",
                            u.isLocked(),
                            u.getCreatedAt() != null ? u.getCreatedAt().format(fmt) : ""));
                }
                Platform.runLater(() -> userMgmtData.setAll(entries));
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Lỗi tìm kiếm: " + ex.getMessage()));
            }
        }).start();
    }

    // =========================================================
    // GROUP MANAGEMENT TAB
    // =========================================================
    private VBox createGroupManagementContent() {
        VBox container = new VBox(12);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #f8f9fa;");

        Label title = new Label("👥 Quản lý Nhóm chat");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        // Search bar
        HBox searchBar = new HBox(10);
        searchBar.setAlignment(Pos.CENTER_LEFT);
        TextField searchField = new TextField();
        searchField.setPromptText("Tìm theo tên nhóm...");
        searchField.setPrefWidth(300);
        searchField.getStyleClass().add("text-field");

        Button searchBtn = new Button("🔍 Tìm kiếm");
        searchBtn.getStyleClass().add("button");

        Button refreshBtn = new Button("↺ Tải lại");
        refreshBtn.getStyleClass().add("action-btn");

        searchBar.getChildren().addAll(searchField, searchBtn, refreshBtn);

        groupMgmtTable = createGroupMgmtTable();
        ScrollPane sp = new ScrollPane(groupMgmtTable);
        sp.setFitToWidth(true);
        sp.setFitToHeight(true);
        sp.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(sp, Priority.ALWAYS);

        searchBtn.setOnAction(e -> {
            String q = searchField.getText().trim();
            if (q.isEmpty()) loadAllGroups();
            else searchGroups(q);
        });
        searchField.setOnAction(e -> searchBtn.fire());
        refreshBtn.setOnAction(e -> { searchField.clear(); loadAllGroups(); });

        container.getChildren().addAll(title, searchBar, sp);
        return container;
    }

    private TableView<AdminGroupEntry> createGroupMgmtTable() {
        TableView<AdminGroupEntry> table = new TableView<>(groupMgmtData);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<AdminGroupEntry, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().id).asObject());
        idCol.setPrefWidth(60);
        idCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<AdminGroupEntry, String> nameCol = new TableColumn<>("Tên nhóm");
        nameCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().name));
        nameCol.setPrefWidth(200);

        TableColumn<AdminGroupEntry, String> creatorCol = new TableColumn<>("Người tạo");
        creatorCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().creatorUsername));
        creatorCol.setPrefWidth(140);

        TableColumn<AdminGroupEntry, Integer> memberCol = new TableColumn<>("Thành viên");
        memberCol.setCellValueFactory(c -> new SimpleIntegerProperty(c.getValue().memberCount).asObject());
        memberCol.setPrefWidth(100);
        memberCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<AdminGroupEntry, String> createdCol = new TableColumn<>("Ngày tạo");
        createdCol.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().createdAt));
        createdCol.setPrefWidth(140);
        createdCol.setStyle("-fx-alignment: CENTER;");

        TableColumn<AdminGroupEntry, Void> actionCol = new TableColumn<>("Hành động");
        actionCol.setPrefWidth(120);
        actionCol.setCellFactory(col -> new TableCell<AdminGroupEntry, Void>() {
            private final Button disbandBtn = new Button("🗑 Giải tán");
            {
                disbandBtn.getStyleClass().add("danger-btn");
                disbandBtn.setOnAction(ev -> {
                    AdminGroupEntry g = getTableView().getItems().get(getIndex());
                    Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                            "Giải tán nhóm \"" + g.name + "\"?\nTất cả thành viên sẽ bị xóa khỏi nhóm!",
                            ButtonType.OK, ButtonType.CANCEL);
                    confirm.setTitle("⚠️ Xác nhận giải tán nhóm");
                    if (confirm.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) {
                        boolean ok = ChatServer.deleteGroupAdmin(g.id);
                        if (ok) { getTableView().getItems().remove(g); showInfo("Đã giải tán nhóm \"" + g.name + "\""); }
                        else showError("Không thể giải tán nhóm. Kiểm tra kết nối DB.");
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                setGraphic(empty ? null : disbandBtn);
            }
        });

        table.getColumns().setAll(idCol, nameCol, creatorCol, memberCol, createdCol, actionCol);
        return table;
    }

    private void loadAllGroups() {
        new Thread(() -> {
            try {
                List<GroupDAO.AdminGroupRow> rows = ChatServer.getGroupDAO().getAllGroups();
                List<AdminGroupEntry> entries = buildGroupEntries(rows);
                Platform.runLater(() -> groupMgmtData.setAll(entries));
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Lỗi tải danh sách nhóm: " + ex.getMessage()));
            }
        }).start();
    }

    private void searchGroups(String query) {
        new Thread(() -> {
            try {
                List<GroupDAO.AdminGroupRow> rows = ChatServer.getGroupDAO().searchGroups(query);
                List<AdminGroupEntry> entries = buildGroupEntries(rows);
                Platform.runLater(() -> groupMgmtData.setAll(entries));
            } catch (Exception ex) {
                Platform.runLater(() -> showError("Lỗi tìm kiếm nhóm: " + ex.getMessage()));
            }
        }).start();
    }

    private List<AdminGroupEntry> buildGroupEntries(List<GroupDAO.AdminGroupRow> rows) {
        List<AdminGroupEntry> entries = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
        for (GroupDAO.AdminGroupRow r : rows) {
            entries.add(new AdminGroupEntry(
                    r.id, r.name,
                    r.creatorUsername != null ? r.creatorUsername : "N/A",
                    r.memberCount,
                    r.createdAt != null ? r.createdAt.format(fmt) : ""));
        }
        return entries;
    }

    // =========================================================
    // ERROR REPORT TAB
    // =========================================================
    private VBox createErrorReportContent() {
        VBox container = new VBox(20);
        container.setPadding(new Insets(20));
        container.setStyle("-fx-background-color: #f8f9fa;");

        HBox headerBox = new HBox(15);
        headerBox.setAlignment(Pos.CENTER_LEFT);

        Label title = new Label("🐛 Báo cáo Lỗi từ Client");
        title.setStyle("-fx-font-size: 24px; -fx-font-weight: bold; -fx-text-fill: #c0392b;");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button clearBtn = new Button("Xóa hết");
        clearBtn.getStyleClass().add("danger-btn");
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

        TableView<org.example.zalu.model.ClientErrorLog> table = new TableView<>(errorLogData);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<org.example.zalu.model.ClientErrorLog, String> timeCol = new TableColumn<>("Thời gian");
        timeCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getTimestamp().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm:ss dd/MM"))));
        timeCol.setPrefWidth(120);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> userCol = new TableColumn<>("Người dùng");
        userCol.setCellValueFactory(cell -> new SimpleStringProperty(
                cell.getValue().getUsername() + " (ID: " + cell.getValue().getUserId() + ")"));
        userCol.setPrefWidth(150);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> msgCol = new TableColumn<>("Nội dung lỗi");
        msgCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getErrorMessage()));
        msgCol.setPrefWidth(400);

        TableColumn<org.example.zalu.model.ClientErrorLog, String> osCol = new TableColumn<>("Hệ điều hành");
        osCol.setCellValueFactory(cell -> new SimpleStringProperty(cell.getValue().getOsInfo()));
        osCol.setPrefWidth(150);

        TableColumn<org.example.zalu.model.ClientErrorLog, Void> actionCol = new TableColumn<>("Chi tiết");
        actionCol.setPrefWidth(100);
        actionCol.setCellFactory(col -> new TableCell<>() {
            private final Button btn = new Button("Xem");
            {
                btn.getStyleClass().add("button");
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

        ScrollPane tableScrollPane = new ScrollPane(table);
        tableScrollPane.setFitToWidth(true);
        tableScrollPane.setFitToHeight(true);
        tableScrollPane.setStyle("-fx-background-color: transparent;");
        VBox.setVgrow(tableScrollPane, Priority.ALWAYS);

        container.getChildren().addAll(headerBox, tableScrollPane);
        return container;
    }

    // =========================================================
    // SHARED UI HELPERS
    // =========================================================
    private VBox createStatCard(String icon, String label, String initialValue, String color, String cardStyle) {
        VBox card = new VBox(8);
        card.setStyle(cardStyle);
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(220);
        card.setPrefHeight(120);

        HBox headerRow = new HBox(8);
        headerRow.setAlignment(Pos.CENTER);
        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 22px;");
        Label titleLabel = new Label(label);
        titleLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 13px; -fx-font-weight: 600;");
        headerRow.getChildren().addAll(iconLabel, titleLabel);

        VBox valueBox = new VBox();
        valueBox.setAlignment(Pos.CENTER);
        Label valueLabel = new Label(initialValue);
        valueLabel.setStyle(String.format("-fx-text-fill: %s; -fx-font-size: 30px; -fx-font-weight: bold;", color));
        valueBox.getChildren().add(valueLabel);

        card.getChildren().addAll(headerRow, valueBox);
        return card;
    }

    private String formatBytes(long bytes) {
        if (bytes < 1024) return bytes + " B";
        else if (bytes < 1024 * 1024) return String.format("%.2f KB", bytes / 1024.0);
        else if (bytes < 1024L * 1024 * 1024) return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        else return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
    }

    private String formatDuration(java.time.Duration duration) {
        long days = duration.toDays();
        long hours = duration.toHours() % 24;
        long minutes = duration.toMinutes() % 60;
        long seconds = duration.getSeconds() % 60;
        return days > 0
                ? String.format("%dd %02d:%02d:%02d", days, hours, minutes, seconds)
                : String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private TableView<ServerModels.OnlineUser> createAccountTable() {
        TableView<ServerModels.OnlineUser> table = new TableView<>(onlineUserData);
        table.setPrefHeight(Region.USE_COMPUTED_SIZE);
        table.setStyle("-fx-background-color: white; -fx-border-color: #bdc3c7;");

        TableColumn<ServerModels.OnlineUser, Integer> idColumn = new TableColumn<>("Id User");
        idColumn.setCellValueFactory(
                cellData -> new javafx.beans.property.SimpleIntegerProperty(cellData.getValue().getUserId()).asObject());
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
                if (empty || item == null) { setText(null); setStyle(""); }
                else {
                    setText(item);
                    setStyle("ON".equals(item)
                            ? "-fx-text-fill: #27ae60; -fx-font-weight: bold;"
                            : "-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
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
                if (empty) { setGraphic(null); }
                else {
                    ServerModels.OnlineUser user = getTableView().getItems().get(getIndex());
                    setGraphic("ON".equals(user.getStatus()) ? kickBtn : null);
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
                    "-fx-background-color: #ff6b35; -fx-background-radius: 15; -fx-text-fill: white; " +
                    "-fx-font-weight: bold; -fx-border-color: #ff6b35; -fx-border-radius: 15;");
        } else {
            serverToggle.setText("OFF");
            serverToggle.setStyle(
                    "-fx-background-color: #ecf0f1; -fx-background-radius: 15; -fx-text-fill: #2c3e50; " +
                    "-fx-font-weight: bold; -fx-border-color: #bdc3c7; -fx-border-radius: 15; -fx-border-width: 1;");
        }
    }

    private void addActivity(UserActivity activity) {
        Platform.runLater(() -> {
            String action = getActionDescription(activity.getActivityType());
            Integer passiveUserId = null;
            if (activity.getTargetUserId() > 0) passiveUserId = activity.getTargetUserId();
            else if (activity.getGroupId() > 0) passiveUserId = activity.getGroupId();
            String content = activity.getEncryptedContent() != null ? activity.getEncryptedContent() : "";
            activityData.add(new ServerModels.ActivityRecord(activity.getUserId(), passiveUserId, action, content));
            if (activityData.size() > 1000) activityData.remove(0);
        });
    }


    private VBox createUserReportContent() {
        VBox container = new VBox(15);
        container.setPadding(new Insets(20));
        
        HBox topBox = new HBox(15);
        topBox.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label("Danh sách Báo cáo từ Người dùng");
        title.setStyle("-fx-font-size: 20px; -fx-font-weight: bold;");
        
        Button refreshBtn = new Button("Làm mới");
        refreshBtn.setOnAction(e -> loadAllReports());
        refreshBtn.getStyleClass().add("action-btn");
        
        topBox.getChildren().addAll(title, refreshBtn);
        
        reportMgmtTable = new TableView<>();
        reportMgmtTable.setItems(reportMgmtData);
        
        TableColumn<AdminReportEntry, Integer> idCol = new TableColumn<>("ID");
        idCol.setCellValueFactory(data -> new javafx.beans.property.SimpleIntegerProperty(data.getValue().id).asObject());
        idCol.setPrefWidth(50);
        
        TableColumn<AdminReportEntry, String> reporterCol = new TableColumn<>("Người báo cáo");
        reporterCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().reporterName));
        reporterCol.setPrefWidth(120);
        
        TableColumn<AdminReportEntry, String> reportedCol = new TableColumn<>("Người bị báo cáo");
        reportedCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().reportedName));
        reportedCol.setPrefWidth(120);
        
        TableColumn<AdminReportEntry, String> reasonCol = new TableColumn<>("Lý do");
        reasonCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().reason));
        reasonCol.setPrefWidth(150);
        
        TableColumn<AdminReportEntry, String> descCol = new TableColumn<>("Chi tiết");
        descCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().description));
        descCol.setPrefWidth(200);
        
        TableColumn<AdminReportEntry, String> statusCol = new TableColumn<>("Trạng thái");
        statusCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().status));
        statusCol.setPrefWidth(100);
        
        TableColumn<AdminReportEntry, String> timeCol = new TableColumn<>("Thời gian");
        timeCol.setCellValueFactory(data -> new javafx.beans.property.SimpleStringProperty(data.getValue().createdAt));
        timeCol.setPrefWidth(150);
        
        TableColumn<AdminReportEntry, Void> actionCol = new TableColumn<>("Hành động");
        actionCol.setPrefWidth(150);
        actionCol.setCellFactory(param -> new TableCell<AdminReportEntry, Void>() {
            private final Button btn = new Button("Đánh dấu Đã xử lý");
            {
                btn.getStyleClass().add("action-btn");
                btn.setOnAction(e -> {
                    AdminReportEntry report = getTableView().getItems().get(getIndex());
                    if ("PENDING".equals(report.status)) {
                        try {
                            if (ChatServer.getReportDAO().updateReportStatus(report.id, "RESOLVED")) {
                                showInfo("Đã cập nhật trạng thái báo cáo #" + report.id);
                                loadAllReports();
                            }
                        } catch (Exception ex) {
                            showError("Lỗi cập nhật trạng thái: " + ex.getMessage());
                        }
                    }
                });
            }
            @Override
            protected void updateItem(Void item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    AdminReportEntry report = getTableView().getItems().get(getIndex());
                    btn.setDisable("RESOLVED".equals(report.status));
                    setGraphic(btn);
                }
            }
        });
        
        reportMgmtTable.getColumns().addAll(idCol, reporterCol, reportedCol, reasonCol, descCol, statusCol, timeCol, actionCol);
        VBox.setVgrow(reportMgmtTable, Priority.ALWAYS);
        container.getChildren().addAll(topBox, reportMgmtTable);
        return container;
    }

    private void loadAllReports() {
        new Thread(() -> {
            try {
                if (ChatServer.getReportDAO() == null) return;
                List<org.example.zalu.model.UserReport> reports = ChatServer.getReportDAO().getAllReports();
                Platform.runLater(() -> {
                    reportMgmtData.clear();
                    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
                    for (org.example.zalu.model.UserReport r : reports) {
                        String timeStr = r.getCreatedAt() != null ? r.getCreatedAt().format(formatter) : "";
                        reportMgmtData.add(new AdminReportEntry(
                            r.getId(), r.getReporterName(), r.getReportedName(), r.getReason(), r.getDescription(), r.getStatus(), timeStr
                        ));
                    }
                });
            } catch (Exception e) {
                Platform.runLater(() -> showError("Lỗi tải danh sách báo cáo: " + e.getMessage()));
            }
        }).start();
    }

    private void updateAccountTable() {
        Platform.runLater(() -> {
            onlineUserData.clear();
            ChatServer.getOnlineUsers().forEach((id, name) -> onlineUserData.add(new ServerModels.OnlineUser(id, "ON")));
        });
    }

    private void addErrorLog(org.example.zalu.model.ClientErrorLog log) {
        Platform.runLater(() -> {
            errorLogData.add(0, log);
            if (errorLogData.size() > 100) errorLogData.remove(errorLogData.size() - 1);
        });
    }

    private String getActionDescription(String activityType) {
        switch (activityType) {
            case "MESSAGE":      return "gửi tin nhắn";
            case "GROUP_MESSAGE":return "gửi tin nhắn nhóm";
            case "FILE":         return "gửi file";
            case "GROUP_FILE":   return "gửi file nhóm";
            case "LOGIN":        return "đăng nhập";
            case "LOGOUT":       return "đăng xuất";
            case "UPDATE_PROFILE": return "chỉnh sửa thông tin cá nhân";
            case "KICK":         return "bị admin đá";
            case "LOCK_ACCOUNT": return "bị admin khóa TK";
            case "UNLOCK_ACCOUNT": return "được admin mở khóa TK";
            case "DELETE_ACCOUNT": return "bị admin xóa TK";
            case "DELETE_GROUP": return "nhóm bị admin giải tán";
            default:             return activityType;
        }
    }

    private void showInfo(String msg) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Thông báo");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Lỗi");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.show();
    }

    // =========================================================
    // INNER DATA CLASSES FOR TABLE VIEWS
    // =========================================================

    /** Entry for Top Active Users table */
    public static class ActiveUserEntry {
        public final int rank;
        public final String username;
        public final long messageCount;
        ActiveUserEntry(int rank, String username, long messageCount) {
            this.rank = rank; this.username = username; this.messageCount = messageCount;
        }
    }

    /** Entry for Admin User Management table */
    public static class AdminUserEntry {
        public final int id;
        public final String username;
        public final String fullName;
        public final String email;
        public final String status;
        public boolean locked;
        public final String createdAt;
        AdminUserEntry(int id, String username, String fullName, String email,
                       String status, boolean locked, String createdAt) {
            this.id = id; this.username = username; this.fullName = fullName;
            this.email = email; this.status = status; this.locked = locked;
            this.createdAt = createdAt;
        }
    }

    /** Entry for Admin Group Management table */
    public static class AdminGroupEntry {
        public final int id;
        public final String name;
        public final String creatorUsername;
        public final int memberCount;
        public final String createdAt;
        AdminGroupEntry(int id, String name, String creatorUsername, int memberCount, String createdAt) {
            this.id = id; this.name = name; this.creatorUsername = creatorUsername;
            this.memberCount = memberCount; this.createdAt = createdAt;
        }
    }

    /** Entry for Admin Report Management table */
    public static class AdminReportEntry {
        public final int id;
        public final String reporterName;
        public final String reportedName;
        public final String reason;
        public final String description;
        public final String status;
        public final String createdAt;
        AdminReportEntry(int id, String reporterName, String reportedName, String reason, String description, String status, String createdAt) {
            this.id = id; this.reporterName = reporterName; this.reportedName = reportedName;
            this.reason = reason; this.description = description; this.status = status; this.createdAt = createdAt;
        }
    }

}
