package com.example.demo1;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyObjectWrapper;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.property.StringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class HelloApplication extends Application {

    final static SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    final static String KERNEL_URL = "https://kernel.ubuntu.com/~kernel-ppa/mainline/";
    // Extend this regex to only match the right rows
    // Is it even worth it?
    final static Pattern PATTERN = Pattern.compile("v(\\d).*", Pattern.CASE_INSENSITIVE);
    /// Properties
    final static ObservableList<Kernel> availableKernelsObs = FXCollections.observableArrayList();
    final static StringProperty distributionProperty = new SimpleStringProperty();
    final static StringProperty architectureProperty = new SimpleStringProperty();
    final static StringProperty currentKernelProperty = new SimpleStringProperty();
    final static SimpleStringProperty selectedKernelVersionProperty = new SimpleStringProperty();
    final static ObservableList<String> selectedKernelDebsObs = FXCollections.observableArrayList();

    public static void main(String[] args) {
        launch(args);
    }

    public static String runCommand(String... args) throws IOException {
        Process process = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder builder = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
        }
        return builder.toString();
    }

    public static String curlLink(String link) throws IOException {
        StringBuilder rawHTML = new StringBuilder();
        // create url with the string.
        URL url = new URL(link);
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        String inputLine = in.readLine();

        // read every line of the HTML content in the URL
        // and concat each line to the rawHTML string until every line is read.
        while (inputLine != null) {
            rawHTML.append(inputLine);
            inputLine = in.readLine();
        }
        in.close();

        return rawHTML.toString();
    }


    public static void curlIndex() {
        try {
            distributionProperty.set(runCommand("lsb_release", "-sd"));
            architectureProperty.set(runCommand("dpkg", "--print-architecture"));
            currentKernelProperty.set(runCommand("uname", "-r"));

            // create url with the string.
            String index = curlLink(KERNEL_URL);

            Document doc = Jsoup.parse(index);
            ArrayList<Kernel> kernels = doc.getElementsByTag("body").stream() //
                    .flatMap((e) -> e.getElementsByTag("table").stream()) //
                    .flatMap((e) -> e.getElementsByTag("tbody").stream()) //
                    .flatMap((e) -> e.getElementsByTag("tr").stream()) //
                    .map((e) -> {
                        try {
                            var versionStr = e.child(1).child(0).text();
                            versionStr = versionStr.substring(0, versionStr.length() - 1);
                            if (PATTERN.matcher(versionStr).find()) {
                                var dateStr = e.child(2).text();
                                return new Kernel(versionStr, dateStr, false);
                            } else {
                                return null;
                            }
                        } catch (Exception ex) {
                            return null;
                        }
                    }) //
                    .filter(Objects::nonNull) //
                    .collect(Collectors.toCollection(ArrayList::new));
            Collections.reverse(kernels);

            // The first 2 and the last rows are header related stuff.
            // Skip
            for (var kernel : kernels) {
                // TODO: Figure out the currently installed version and append
                var thread = new Thread(() -> {
                    try {
                        var debs = curlKernelDebs(kernel.version);
                        if (!debs.isEmpty()) {
                            kernel.debs.addAll(debs);
                            Platform.runLater(() -> availableKernelsObs.add(kernel));
                        }
                    } catch (Exception e) {
                        // System.out.println("Skipping " + kernel.getVersion() + " because it does not contain any DEB files.");
                    }
                });
                thread.setPriority(Thread.MIN_PRIORITY);
                thread.setDaemon(true);
                thread.start();
            }
            System.out.println("Done!");
        } catch (Exception e) {
            e.printStackTrace();
            // TODO: Show error to user
        }
    }

    public static HBox generateTopBox() {
        Label distributionText = new Label();
        distributionText.textProperty().bind(distributionProperty);

        Label architectureText = new Label();
        architectureText.textProperty().bind(architectureProperty);

        Label currentKernelText = new Label();
        currentKernelText.textProperty().bind(currentKernelProperty);

        HBox box = new HBox(distributionText, architectureText, currentKernelText);
        box.setSpacing(8);

        return box;
    }


    public static TableView<Kernel> generateKernelTable() {
        TableView<Kernel> table = new TableView<>(availableKernelsObs);

        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        // VBox.setVgrow(table, Priority.ALWAYS);

        var versionColumn = new TableColumn<Kernel, String>("Version");
        var dateColumn = new TableColumn<Kernel, String>("Date");

        versionColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().version));
        dateColumn.setCellValueFactory(p -> new ReadOnlyObjectWrapper<>(p.getValue().date));

        table.getColumns().add(versionColumn);
        table.getColumns().add(dateColumn);

        // Bind data to table
        table.setRowFactory(tv -> {
            TableRow<Kernel> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                selectedKernelDebsObs.clear();
                selectedKernelDebsObs.addAll(row.getItem().debs);
            });
            return row;
        });

        return table;
    }

    public static List<String> curlKernelDebs(String version) throws IOException {
        var page = curlLink(KERNEL_URL + version + "/" + architectureProperty.get() + "/");
        var pattern = Pattern.compile("linux-(.)*.deb", Pattern.CASE_INSENSITIVE);
        var doc = Jsoup.parse(page);

        return doc.getElementsByTag("body").stream() //
                .flatMap((e) -> e.getElementsByTag("table").stream()) //
                .flatMap((e) -> e.getElementsByTag("tbody").stream()) //
                .flatMap((e) -> e.getElementsByTag("tr").stream()) //
                .flatMap((e) -> e.getElementsByTag("td").stream()) //
                .flatMap((e) -> e.getElementsByTag("a").stream()) //
                .map((e) -> e.attr("href")) //
                .filter((e) -> pattern.matcher(e).find()) //
                .toList();
    }

    public static VBox generateBottomBox(TableView<Kernel> kernelsTable) {
        ListView<String> lv = new ListView<>(selectedKernelDebsObs);
        lv.addEventFilter(MouseEvent.MOUSE_PRESSED, Event::consume);

        Button button = new Button("Install");
        button.disableProperty().bind(kernelsTable.getSelectionModel().selectedItemProperty().isNull());

        VBox vbox = new VBox(lv, button);
        vbox.setSpacing(8);

        return vbox;
    }

    @Override
    public void init() {
        var thread = new Thread(HelloApplication::curlIndex);
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void start(Stage stage) {
        var topBox = generateTopBox();
        var kernelsTable = generateKernelTable();
        var bottomBox = generateBottomBox(kernelsTable);

        VBox sceneBox = new VBox(topBox, kernelsTable, bottomBox);
        sceneBox.setPadding(new Insets(10));
        sceneBox.setSpacing(10);

        Scene scene = new Scene(sceneBox);

        stage.setTitle("Ubuntu Kernel Installer");
        stage.setScene(scene);
        stage.setHeight(376);
        stage.setWidth(667);
        stage.show();
    }
}