package com.example.demo1;

import javafx.application.Application;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


public class HelloApplication extends Application {

    final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    final static String KERNEL_URL = "https://kernel.ubuntu.com/~kernel-ppa/mainline/";
    // Extend this regex to only match the right rows
    // Is it even worth it?
    final static Pattern PATTERN = Pattern.compile("v(\\d)", Pattern.CASE_INSENSITIVE);

    final static ReadOnlyObjectProperty<ObservableList<Kernel>> kernelsProperty = new SimpleObjectProperty<>(FXCollections.observableArrayList());
    final static StringProperty distributionProperty = new SimpleStringProperty();
    final static StringProperty architectureProperty = new SimpleStringProperty();
    final static StringProperty currentKernelProperty = new SimpleStringProperty();

    public static void main(String[] args) {
        launch(args);
    }

    public static final String runCommand(String... args) throws IOException {
        Process process = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        String result = builder.toString();
        return result;
    }

    @Override
    public void init() {
        StringBuilder rawHTML = new StringBuilder();


        Runnable job = () -> {
            try {
                distributionProperty.set(runCommand("lsb_release", "-sd"));
                architectureProperty.set(runCommand("dpkg", "--print-architecture"));
                currentKernelProperty.set(runCommand("uname", "-r"));

                // create url with the string.
                URL url = new URL(KERNEL_URL);
                BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
                String inputLine = in.readLine();

                // read every line of the HTML content in the URL
                // and concat each line to the rawHTML string until every line is read.
                while (inputLine != null) {
                    rawHTML.append(inputLine);
                    inputLine = in.readLine();
                }
                in.close();

                Document doc = Jsoup.parse(rawHTML.toString());
                Elements rows = doc.getElementsByTag("body").first().getElementsByTag("table").first().getElementsByTag("tbody").first().getElementsByTag("tr");

                // The first 2 and the last rows are header related stuff.
                // Skip
                for (Element row : rows.stream().skip(2).limit(rows.size() - 3).toList()) {
                    String version = row.getElementsByTag("td").get(1).getElementsByTag("a").first().text();
                    // Trim the last `/`
                    Matcher matcher = PATTERN.matcher(version);
                    if (matcher.find()) {
                        version = version.substring(0, version.length() - 1);
                        String dateStr = row.getElementsByTag("td").get(2).text();
                        // TODO: Figure out the currently installed version and append
                        kernelsProperty.get().add(new Kernel(version, dateStr, false));
                    }

                }
                System.out.println("Done!");
            } catch (Exception e) {
                e.printStackTrace();
                // TODO: Show error to user
            }
        };


        new Thread(job).start();
    }

    @Override
    public void start(Stage stage) {
        Label distributionText = new Label();
        distributionText.textProperty().bind(distributionProperty);

        Label architectureText = new Label();
        architectureText.textProperty().bind(architectureProperty);

        Label currentKernelText = new Label();
        currentKernelText.textProperty().bind(currentKernelProperty);

        TableView<Kernel> kernelsTable = new TableView<>();
        kernelsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        VBox.setVgrow(kernelsTable, Priority.ALWAYS);

        TableColumn<Kernel, String> versionColumn = new TableColumn<>("Version");
        TableColumn<Kernel, String> dateColumn = new TableColumn<>("Date");

        versionColumn.setCellValueFactory(new PropertyValueFactory<>("version"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));

        // Bind data to table
        kernelsTable.getColumns().add(versionColumn);
        kernelsTable.getColumns().add(dateColumn);
        kernelsTable.itemsProperty().bind(kernelsProperty);

        Button installButton = new Button("Install");

        installButton.disableProperty().bind(kernelsTable.getSelectionModel().selectedItemProperty().isNull());

        HBox topInfoBox = new HBox(distributionText, architectureText, currentKernelText);
        topInfoBox.setSpacing(8);

        HBox bottomButtonBox = new HBox(installButton);
        bottomButtonBox.setSpacing(8);

        VBox sceneBox = new VBox(topInfoBox, kernelsTable, bottomButtonBox);
        sceneBox.setPadding(new Insets(10));
        sceneBox.setSpacing(10);

        Scene scene = new Scene(sceneBox);

        stage.setTitle("Ubuntu Kernel Installer");
        stage.setScene(scene);
        stage.setHeight(376);
        stage.setWidth(667);
        stage.show();
    }

    public static class Kernel {

        private final SimpleStringProperty version;
        private final SimpleStringProperty date;
        private final SimpleBooleanProperty installed;

        public Kernel(String version, String date, boolean installed) {
            this.version = new SimpleStringProperty(version);
            this.date = new SimpleStringProperty(date);
            this.installed = new SimpleBooleanProperty(installed);
        }

        public String getVersion() {
            return this.version.get();
        }

        public SimpleStringProperty versionProperty() {
            return this.version;
        }

        public String getDate() {
            return this.date.get();
        }

        public SimpleStringProperty dateProperty() {
            return this.date;
        }

        public boolean getInstalled() {
            return this.installed.get();
        }

        public SimpleBooleanProperty installedProperty() {
            return this.installed;
        }
    }
}