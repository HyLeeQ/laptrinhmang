module org.example.zalu {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires java.sql;
    requires jbcrypt;

    opens org.example.zalu to javafx.fxml;
    exports org.example.zalu;
    exports org.example.zalu.controller;
    opens org.example.zalu.controller to javafx.fxml;
    opens org.example.zalu.views to javafx.fxml;
    exports org.example.zalu.server;
    opens org.example.zalu.server to javafx.fxml;
    exports org.example.zalu.client;
    opens org.example.zalu.client to javafx.fxml;
}