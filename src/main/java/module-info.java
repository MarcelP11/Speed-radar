module com.example.doppler {
    requires javafx.controls;
    requires javafx.fxml;
    requires com.fazecast.jSerialComm;


    opens com.example.doppler to javafx.fxml;
    exports com.example.doppler;
}