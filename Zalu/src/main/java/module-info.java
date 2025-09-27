module org.example.zalu {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires net.synedra.validatorfx;
    requires org.kordamp.ikonli.javafx;
    requires java.sql;

    opens org.example.zalu to javafx.fxml;
    exports org.example.zalu;
}