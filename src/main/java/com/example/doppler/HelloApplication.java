package com.example.doppler;


import com.fazecast.jSerialComm.SerialPort;
import com.fazecast.jSerialComm.SerialPortDataListener;
import com.fazecast.jSerialComm.SerialPortEvent;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;


import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Comparator;

public class HelloApplication extends Application {

    private Label frequencyLabel;
    private Label speedLabel;
    private Label maxSpeedLabel;
    private Label connectionStatusLabel; // Label for connection status
    private TextField nameField;
    private TextField teamField;
    private SerialPort serialPort;
    private Timeline updateTimeline;
    private float maxSpeed = 0;
    private boolean isMeasuring = false;

    private TableView<ObservableList<String>> tableView;
    private Button startButton;
    private Button stopButton;

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Speed & Frequency Display");

        frequencyLabel = createStyledLabel("Frequency: N/A Hz", 20);
        speedLabel = createStyledLabel("Speed: N/A km/h", 20);
        maxSpeedLabel = createStyledLabel("Max Speed: Not measuring", 20);
        maxSpeedLabel.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");

        connectionStatusLabel = createStyledLabel("Connection with sensor: Not connected", 16);
        connectionStatusLabel.setStyle("-fx-text-fill: red;");

        nameField = new TextField();
        nameField.setPromptText("Player name");

        teamField = new TextField();
        teamField.setPromptText("Team");

        startButton = new Button("Start");
        startButton.setOnAction(e -> startMeasurement());
        startButton.setMinWidth(100);

        stopButton = new Button("Stop");
        stopButton.setOnAction(e -> stopMeasurement());
        stopButton.setDisable(true);
        stopButton.setMinWidth(100);

        Button deleteButton = new Button("Delete");
        deleteButton.setOnAction(e -> deleteSelectedRecord());
        Button exportButton = new Button("Export as CSV");
        exportButton.setOnAction(e -> exportToCSV());

        tableView = new TableView<>();
        tableView.getColumns().addAll(
                createColumn("Order", 0, 50),
                createColumn("Player Name", 1, 150),
                createColumn("Team", 2, 150),
                createColumn("Max Speed", 3, 100)
        );

        tableView.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            deleteButton.setDisable(newSelection == null);
        });

        VBox vbox = new VBox(10);
        vbox.getChildren().addAll(
                connectionStatusLabel,
                frequencyLabel,
                speedLabel,
                maxSpeedLabel,
                new HBox(10, new Label("Player name:"), nameField, new Label("Team:"), teamField),
                new HBox(10, startButton, stopButton),
                new Separator(),
                createStyledLabel("RESULTS", 24, "1.5em"),
                new HBox(10, deleteButton, exportButton),
                tableView
        );

        vbox.setPadding(new Insets(20));
        vbox.setAlignment(Pos.CENTER);

        primaryStage.setScene(new Scene(vbox, 600, 500));
        primaryStage.show();

        primaryStage.setOnCloseRequest(event -> stop());

        connectToArduino();

        updateTimeline = new Timeline(
                new KeyFrame(Duration.seconds(2), event -> readValuesFromArduino())
        );
        updateTimeline.setCycleCount(Timeline.INDEFINITE);
        updateTimeline.play();
    }

    private TableColumn<ObservableList<String>, String> createColumn(String title, int index, int width) {
        TableColumn<ObservableList<String>, String> column = new TableColumn<>(title);
        column.setCellValueFactory(param -> new SimpleStringProperty(param.getValue().get(index)));
        column.setPrefWidth(width);
        return column;
    }

    private void connectToArduino() {
        serialPort = SerialPort.getCommPort("COM3"); // Change to the correct serial port
        serialPort.setBaudRate(57600);

        if (serialPort.openPort()) {
            connectionStatusLabel.setText("Connection with sensor: Connected");
            connectionStatusLabel.setStyle("-fx-text-fill: green;");
            serialPort.addDataListener(new SerialPortDataListener());
        } else {
            connectionStatusLabel.setText("Connection with sensor: Not connected");
            connectionStatusLabel.setStyle("-fx-text-fill: red;");
        }
    }

    private void readValuesFromArduino() {
        try {
            if (serialPort != null && serialPort.isOpen() && isMeasuring) {
                byte[] requestData = {'R'};
                serialPort.writeBytes(requestData, 1); // Request data update from Arduino
            }
        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    private boolean validateInput() {
        if (nameField.getText().isEmpty()) {
            showAlert("Please enter player name.");
            return false;
        }
        if (teamField.getText().isEmpty()) {
            showAlert("Please enter team.");
            return false;
        }
        return true;
    }

    private void showAlert(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void startMeasurement() {
        if (validateInput()) {
            startButton.setDisable(true);
            stopButton.setDisable(false);
            isMeasuring = true;
            maxSpeed = 0; // Reset max speed
            maxSpeedLabel.setText("Max Speed: N/A km/h");
            maxSpeedLabel.setStyle("-fx-text-fill: green; -fx-font-size: 20px;");
        }
    }

    private void stopMeasurement() {
        startButton.setDisable(false);
        stopButton.setDisable(true);
        isMeasuring = false;

        String playerName = nameField.getText();
        String teamName = teamField.getText();
        String maxSpeedStr = String.format("%.2f km/h", maxSpeed).replace(',', '.');

        ObservableList<String> newRow = FXCollections.observableArrayList(
                String.valueOf(tableView.getItems().size() + 1),
                playerName,
                teamName,
                maxSpeedStr
        );

        tableView.getItems().add(newRow);
        tableView.getItems().sort((row1, row2) -> {
            float speed1 = Float.parseFloat(row1.get(3).replace(" km/h", ""));
            float speed2 = Float.parseFloat(row2.get(3).replace(" km/h", ""));
            return Float.compare(speed2, speed1);
        });

        for (int i = 0; i < tableView.getItems().size(); i++) {
            tableView.getItems().get(i).set(0, String.valueOf(i + 1));
        }

        // Reset max speed display
        maxSpeedLabel.setText("Max Speed: Not measuring");
        maxSpeedLabel.setStyle("-fx-text-fill: red; -fx-font-size: 20px;");
    }

    private void deleteSelectedRecord() {
        ObservableList<String> selectedRecord = tableView.getSelectionModel().getSelectedItem();
        if (selectedRecord != null) {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Confirmation");
            alert.setHeaderText(null);
            alert.setContentText("Are you sure you want to delete this record?");

            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    tableView.getItems().remove(selectedRecord);
                    for (int i = 0; i < tableView.getItems().size(); i++) {
                        tableView.getItems().get(i).set(0, String.valueOf(i + 1));
                    }
                }
            });
        }
    }

    private void exportToCSV() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save as CSV");
        fileChooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("CSV Files", "*.csv"));
        File file = fileChooser.showSaveDialog(null);

        if (file != null) {
            try (FileWriter writer = new FileWriter(file)) {
                for (ObservableList<String> row : tableView.getItems()) {
                    writer.write(String.join(";", row) + "\n");
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void stop() {
        if (updateTimeline != null) {
            updateTimeline.stop();
        }
        if (serialPort != null && serialPort.isOpen()) {
            serialPort.closePort();
        }
    }

        private class SerialPortDataListener implements com.fazecast.jSerialComm.SerialPortDataListener {
        private StringBuilder buffer = new StringBuilder();

        @Override
        public int getListeningEvents() {
            return SerialPort.LISTENING_EVENT_DATA_AVAILABLE;
        }

        @Override
        public void serialEvent(SerialPortEvent event) {
            if (event.getEventType() == SerialPort.LISTENING_EVENT_DATA_AVAILABLE) {
                byte[] newData = new byte[serialPort.bytesAvailable()];
                int numRead = serialPort.readBytes(newData, newData.length);
                String data = new String(newData);
                buffer.append(data);

                if (buffer.toString().contains("\n")) {
                    String[] values = buffer.toString().trim().split(",");
                    System.out.println("Data received from Arduino: " + buffer.toString());
                    if (values.length == 2) {
                        try {
                            // Extracting the numbers from the strings
                            String frequencyString = values[0].split(": ")[1].replace(" Hz", "").trim();
                            String speedString = values[1].split(": ")[1].replace(" km/h", "").trim();

                            float frequency = Float.parseFloat(frequencyString);
                            float speed = Float.parseFloat(speedString);

                            if (isMeasuring) {
                                maxSpeed = Math.max(maxSpeed, speed);
                            }

                            Platform.runLater(() -> {
                                frequencyLabel.setText("Frequency: " + frequency + " Hz");
                                speedLabel.setText("Speed: " + speed + " km/h");
                                maxSpeedLabel.setText("Max Speed: " + (isMeasuring ? maxSpeed + " km/h" : "Not measuring"));
                            });

                        } catch (NumberFormatException ex) {
                            ex.printStackTrace();
                        }
                    }
                    buffer.setLength(0); // Vyprázdnění bufferu
                }
            }
        }
    }

    private Label createStyledLabel(String text, int fontSize) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: " + fontSize + "px;");
        return label;
    }

    private Label createStyledLabel(String text, int fontSize, String sizeStyle) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: " + sizeStyle + ";");
        return label;
    }

    public static void main(String[] args) {
        launch(args);
    }
}