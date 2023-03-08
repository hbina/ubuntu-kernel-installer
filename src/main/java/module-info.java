module com.example.juki {
    requires javafx.controls;
    requires javafx.fxml;

    requires org.controlsfx.controls;
    requires com.dlsc.formsfx;

    requires org.jsoup;

    opens com.example.juki to javafx.fxml;
    exports com.example.juki;
}