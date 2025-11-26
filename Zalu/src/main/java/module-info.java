module org.example.zalu {
    requires javafx.controls;
    requires javafx.fxml;
    requires javafx.media;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires org.kordamp.ikonli.core;
    requires org.kordamp.ikonli.materialdesign2;
    requires org.kordamp.ikonli.fontawesome5;
    requires jbcrypt;
    requires java.desktop;
    requires mysql.connector.j;
    requires com.zaxxer.hikari;
    requires java.sql;
    requires org.slf4j;
    requires ch.qos.logback.classic;

    opens org.example.zalu to javafx.fxml;
    exports org.example.zalu;
    exports org.example.zalu.controller;
    opens org.example.zalu.controller to javafx.fxml;
    opens org.example.zalu.controller.auth to javafx.fxml;
    opens org.example.zalu.controller.chat to javafx.fxml;
    opens org.example.zalu.controller.friend to javafx.fxml;
    opens org.example.zalu.controller.group to javafx.fxml;
    opens org.example.zalu.controller.profile to javafx.fxml;
    opens org.example.zalu.controller.common to javafx.fxml;
    opens org.example.zalu.controller.media to javafx.fxml;
    opens org.example.zalu.ui to javafx.fxml;
    opens org.example.zalu.util.ui to javafx.fxml;
    exports org.example.zalu.ui;
    exports org.example.zalu.util.ui;
    exports org.example.zalu.server;
    opens org.example.zalu.server to javafx.fxml;
    exports org.example.zalu.client;
    opens org.example.zalu.client to javafx.fxml;
    exports org.example.zalu.test;
    opens org.example.zalu.test to javafx.fxml;
    exports org.example.zalu.model;
}