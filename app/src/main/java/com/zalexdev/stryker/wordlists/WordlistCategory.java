package com.zalexdev.stryker.wordlists;

import com.zalexdev.stryker.R;

import java.util.Locale;

public enum WordlistCategory {

    PASSWORD("password", "Passwords",  R.drawable.password,  com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_signal)),
    WIFI    ("wifi",     "Wi-Fi PSK",  R.drawable.wifi,      com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_signal)),
    PIN     ("pin",      "PIN codes",  R.drawable.fiber_pin, com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_signal)),
    USERNAME("username", "Usernames",  R.drawable.name,      com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_signal)),
    WEB     ("web",      "Web paths",  R.drawable.web,       com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_signal)),
    OTHER   ("other",    "Other",      R.drawable.file,      com.zalexdev.stryker.ui.ios.Ios.color(com.zalexdev.stryker.R.color.ch_bone_dim));

    public final String id;
    public final String title;
    public final int iconRes;
    public final int color;

    WordlistCategory(String id, String title, int iconRes, int color) {
        this.id = id;
        this.title = title;
        this.iconRes = iconRes;
        this.color = color;
    }

    public static WordlistCategory byId(String id) {
        if (id != null) {
            for (WordlistCategory c : values()) {
                if (c.id.equalsIgnoreCase(id)) return c;
            }
        }
        return OTHER;
    }

    public static WordlistCategory detectFromName(String name) {
        if (name == null) return OTHER;
        String n = name.toLowerCase(Locale.US);
        if (n.contains("pin")) return PIN;
        if (n.contains("wpa") || n.contains("wifi") || n.contains("wi-fi")
                || n.contains("psk") || n.contains("router")) return WIFI;
        if (n.contains("user") || n.contains("login") || n.contains("names")) return USERNAME;
        if (n.contains("dir") || n.contains("path") || n.contains("subdomain")
                || n.contains("vhost") || n.contains("fuzz") || n.contains("endpoint")) return WEB;
        if (n.contains("pass") || n.contains("rockyou") || n.contains("wordlist")
                || n.contains("credential")) return PASSWORD;
        return OTHER;
    }

    public static WordlistCategory detectFromSample(java.util.List<String> sample) {
        if (sample == null || sample.isEmpty()) return OTHER;
        int numeric = 0, shortLines = 0, withSlash = 0, total = 0;
        for (String line : sample) {
            String s = line.trim();
            if (s.isEmpty()) continue;
            total++;
            if (s.matches("\\d{4,8}")) numeric++;
            if (s.length() < 8) shortLines++;
            if (s.startsWith("/") || s.contains("/")) withSlash++;
        }
        if (total == 0) return OTHER;
        if (numeric == total) return PIN;
        if (withSlash * 2 > total) return WEB;
        if (shortLines * 2 > total) return USERNAME;
        return PASSWORD;
    }
}
