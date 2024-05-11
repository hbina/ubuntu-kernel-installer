package com.example.juki;

import javafx.application.Platform;
import javafx.beans.property.*;
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

import java.io.*;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.Collectors;


public class Application extends javafx.application.Application {
    final static String KERNEL_URL = "https://kernel.ubuntu.com/~kernel-ppa/mainline/";
    // Extend this regex to only match the right rows
    // Is it even worth it?
    final static Pattern PATTERN = Pattern.compile("v(\\d).*", Pattern.CASE_INSENSITIVE);
    /// Properties
    final static ObservableList<Kernel> availableKernelsObs = FXCollections.observableArrayList();
    final static ObservableList<String> selectedKernelDebsObs = FXCollections.observableArrayList();
    final static StringProperty distributionProperty = new SimpleStringProperty("Unknown distribution");
    final static StringProperty architectureProperty = new SimpleStringProperty("Unknown architecture");
    final static StringProperty kernelVersionProperty = new SimpleStringProperty("Unknown kernel version");
    final static StringProperty downloadButtonTextProperty = new SimpleStringProperty("Install");
    final static BooleanProperty isInstallingProperty = new SimpleBooleanProperty(false);
    static Kernel selectedKernel = null;

    public static void main(String[] args) {
        launch(args);
    }

    public static String runCommand(List<String> args) throws IOException {
        System.out.println("Running command: '" + String.join(" ", args) + "'");
        Process process = new ProcessBuilder(args).start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        StringBuilder builder = new StringBuilder();

        String line;
        while ((line = reader.readLine()) != null) {
            builder.append(line);
            builder.append(System.lineSeparator());
        }

        return builder.toString();
    }

    public static String curlLink(String link) throws IOException {
        StringBuilder rawHTML = new StringBuilder();
        // create url with the string.
        URL url = new URL(link);
        BufferedReader in = new BufferedReader(new InputStreamReader(url.openStream()));
        String inputLine = in.readLine();

        while (inputLine != null) {
            rawHTML.append(inputLine);
            inputLine = in.readLine();
        }
        in.close();

        return rawHTML.toString();
    }

    public static void triggerKernelDownload() {
        if (isInstallingProperty.get() || selectedKernel == null) {
            return;
        }

        System.out.println("Triggering download for " + selectedKernel.version);

        isInstallingProperty.set(true);
        downloadButtonTextProperty.set("Installing " + selectedKernel.version);

        final var kernel = selectedKernel;
        final var architecture = architectureProperty.get();
        Runnable mainJob = () -> {
            var downloadCounter = new AtomicInteger(kernel.debs.size());
            var jobs = kernel.debs.stream().map((debName) -> new Thread(() -> {
                try {
                    var currentFile = new File(debName);

                    if (currentFile.exists()) {
                        if (!currentFile.delete()) {
                            System.out.println("Unable to delete " + debName);
                        }
                    }

                    // An example link:
                    // https://kernel.ubuntu.com/~kernel-ppa/mainline/v5.15.79/amd64/linux-headers-5.15.79-051579-generic_5.15.79-051579.202211160602_amd64.deb
                    var urlStr = KERNEL_URL + kernel.version + "/" + architecture + "/" + debName;
                    URL url = new URL(urlStr);
                    ReadableByteChannel readableByteChannel = Channels.newChannel(url.openStream());
                    try (FileOutputStream fileOutputStream = new FileOutputStream("/tmp/" + debName, false)) {
                        fileOutputStream.getChannel().transferFrom(readableByteChannel, 0, Long.MAX_VALUE);
                        System.out.println("Finished downloading " + urlStr);
                        downloadCounter.decrementAndGet();
                    }
                } catch (Exception e) {
                    System.out.println("Unable to download " + kernel.version + " because " + e);
                }
            })).toList();

            for (var job : jobs) {
                job.start();
            }

            for (var job : jobs) {
                try {
                    job.join();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

            var args = new ArrayList<String>(6 + kernel.debs.size());
            Collections.addAll(args, "pkexec", "env", "DISPLAY=${DISPLAY}", "XAUTHORITY=${XAUTHORITY}", "dpkg", "--install");
            for (var deb : kernel.debs) {
                args.add("/tmp/" + deb);
            }

            if (downloadCounter.get() == 0) {
                try {
                    var output = runCommand(args);
                    System.out.println(output);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            } else {
                System.out.println("Skipping install because one of the download failed");
            }

            Platform.runLater(() -> {
                isInstallingProperty.set(false);
                downloadButtonTextProperty.set("Install");
            });
        };
        new Thread(mainJob).start();
    }


    public static void curlIndex() {
        try {
            Platform.runLater(() -> {
                try {
                    distributionProperty.set(runCommand(Arrays.asList("lsb_release", "-sd")).trim());
                    architectureProperty.set(runCommand(Arrays.asList("dpkg", "--print-architecture")).trim());
                    kernelVersionProperty.set(runCommand(Arrays.asList("uname", "-r")).trim());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });

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
                var thread = getThread(kernel);
                thread.start();
            }
            System.out.println("Done!");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static Thread getThread(Kernel kernel) {
        var thread = new Thread(() -> {
            try {
                var debs = curlKernelDebs(kernel.version);
                if (!debs.isEmpty()) {
                    kernel.debs.addAll(debs);
                    Platform.runLater(() -> availableKernelsObs.add(kernel));
                }
            } catch (Exception ignored) {
                // Ignore versions that don't have DEB files
            }
        });
        thread.setPriority(Thread.MIN_PRIORITY);
        thread.setDaemon(true);
        return thread;
    }

    public static HBox generateTopBox() {
        Label distributionText = new Label();
        distributionText.textProperty().bind(distributionProperty);

        Label architectureText = new Label();
        architectureText.textProperty().bind(architectureProperty);

        Label currentKernelText = new Label();
        currentKernelText.textProperty().bind(kernelVersionProperty);

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
                var kernel = row.getItem();
                selectedKernelDebsObs.setAll(kernel.debs);
                selectedKernel = kernel;
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
        var lv = new ListView<>(selectedKernelDebsObs);
        lv.addEventFilter(MouseEvent.MOUSE_PRESSED, Event::consume);

        var button = new Button();
        button.textProperty().bind(downloadButtonTextProperty);
        button.disableProperty().bind(kernelsTable.getSelectionModel().selectedItemProperty().isNull().or(isInstallingProperty));
        button.setOnAction((e) -> triggerKernelDownload());

        var vbox = new VBox(lv, button);
        vbox.setSpacing(8);

        return vbox;
    }

    @Override
    public void init() {
        var thread = new Thread(Application::curlIndex);
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