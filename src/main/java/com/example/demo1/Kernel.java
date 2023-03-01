package com.example.demo1;

import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;

import java.util.ArrayList;

public class Kernel {

    private final SimpleStringProperty version;
    private final SimpleStringProperty date;
    private final SimpleBooleanProperty installed;
    private final SimpleObjectProperty<ArrayList<String>> debs;

    public Kernel(String version, String date, boolean installed) {
        this.version = new SimpleStringProperty(version);
        this.date = new SimpleStringProperty(date);
        this.installed = new SimpleBooleanProperty(installed);
        this.debs = new SimpleObjectProperty<>(new ArrayList<>());
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

    public ArrayList<String> getDebs() {
        return this.debs.get();
    }

    public void setDebs(ArrayList<String> debs) {
        this.debs.set(debs);
    }

    public SimpleObjectProperty<ArrayList<String>> debsProperty() {
        return this.debs;
    }
}
