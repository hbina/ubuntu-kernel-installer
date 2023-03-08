package com.example.juki;

import java.util.ArrayList;

public class Kernel {
    public final String version;
    public final String date;
    public final boolean installed;
    public final ArrayList<String> debs;

    public Kernel(String version, String date, boolean installed) {
        this.version = version;
        this.date = date;
        this.installed = installed;
        this.debs = new ArrayList<>();
    }
}
