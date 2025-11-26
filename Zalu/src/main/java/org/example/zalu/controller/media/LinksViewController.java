package org.example.zalu.controller.media;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;
import org.example.zalu.model.Message;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LinksViewController {
    @FXML private Label subtitleLabel;
    @FXML private ListView<String> linksListView;
    
    private Stage dialogStage;
    private List<Message> messages;
    private boolean isGroup;
    
    private static final Pattern URL_PATTERN = Pattern.compile(
        "https?://[\\w\\-]+(\\.[\\w\\-]+)+([\\w\\-\\.,@?^=%&:/~\\+#]*[\\w\\-\\@?^=%&/~\\+#])?",
        Pattern.CASE_INSENSITIVE
    );
    
    public void initialize() {
        linksListView.setCellFactory(param -> new ListCell<String>() {
            @Override
            protected void updateItem(String link, boolean empty) {
                super.updateItem(link, empty);
                if (empty || link == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    setText(link);
                    setStyle("-fx-font-size: 14px; -fx-text-fill: #0088ff; -fx-padding: 12; -fx-cursor: hand; -fx-underline: true;");
                }
            }
        });
        
        linksListView.setOnMouseClicked(e -> {
            if (e.getClickCount() == 2) {
                String selected = linksListView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    try {
                        java.awt.Desktop.getDesktop().browse(new URI(selected));
                    } catch (Exception ex) {
                        System.err.println("Error opening link: " + ex.getMessage());
                    }
                }
            }
        });
    }
    
    public void setDialogStage(Stage stage) {
        this.dialogStage = stage;
    }
    
    public void setMessages(List<Message> messages, boolean isGroup) {
        this.messages = messages;
        this.isGroup = isGroup;
        loadLinks();
    }
    
    private void loadLinks() {
        if (messages == null || linksListView == null) return;
        
        List<String> links = new ArrayList<>();
        for (Message msg : messages) {
            if (msg.getContent() != null && !msg.getContent().trim().isEmpty()) {
                Matcher matcher = URL_PATTERN.matcher(msg.getContent());
                while (matcher.find()) {
                    String link = matcher.group();
                    if (!links.contains(link)) {
                        links.add(link);
                    }
                }
            }
        }
        
        linksListView.getItems().setAll(links);
        
        if (subtitleLabel != null) {
            subtitleLabel.setText(links.size() + " link đã chia sẻ trong cuộc trò chuyện");
        }
    }
    
    @FXML
    private void handleClose() {
        if (dialogStage != null) {
            dialogStage.close();
        }
    }
}

