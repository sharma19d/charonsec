package com.zalexdev.stryker.ui.ios;

import androidx.annotation.ColorRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.R;

/** The five tabs, their grouped tool lists, and each tool's title, glyph and tile colour. */
public final class IosNav {

    public static final int TAB_HOME = 0;
    public static final int TAB_WIRELESS = 1;
    public static final int TAB_NETWORK = 2;
    public static final int TAB_ATTACK = 3;
    public static final int TAB_MORE = 4;

    public static final class Tool {
        public final int id;
        public final String title;
        public final String subtitle;
        @DrawableRes public final int icon;
        @ColorRes public final int tile;

        Tool(int id, String title, String subtitle, @DrawableRes int icon, @ColorRes int tile) {
            this.id = id;
            this.title = title;
            this.subtitle = subtitle;
            this.icon = icon;
            this.tile = tile;
        }
    }

    public static final class Group {
        @Nullable public final String header;
        @Nullable public final String footer;
        public final Tool[] tools;

        Group(@Nullable String header, @Nullable String footer, Tool... tools) {
            this.header = header;
            this.footer = footer;
            this.tools = tools;
        }
    }

    public static final class Tab {
        public final String label;
        public final String title;
        @DrawableRes public final int icon;
        public final Group[] groups;

        Tab(String label, String title, @DrawableRes int icon, Group... groups) {
            this.label = label;
            this.title = title;
            this.icon = icon;
            this.groups = groups;
        }
    }

    public static final Tool WIFI = new Tool(R.id.wifi_item, "Wi-Fi", "Scan, deauth and capture handshakes", R.drawable.wifi, R.color.ios_blue);
    public static final Tool HANDSHAKES = new Tool(R.id.hs_item, "Handshakes", "Captured handshakes and cracking", R.drawable.storage, R.color.ios_indigo);
    public static final Tool MAC = new Tool(R.id.macchanger_item, "MAC Changer", "Spoof interface hardware addresses", R.drawable.password, R.color.ios_orange);
    public static final Tool WPAIR = new Tool(R.id.wpair_item, "WhisperPair", "Bluetooth Fast Pair research", R.drawable.wpair, R.color.ios_cyan);
    public static final Tool LAN = new Tool(R.id.lan_item, "Local Network", "Discover hosts on this network", R.drawable.lan, R.color.ios_green);
    public static final Tool NMAP = new Tool(R.id.nmap_item, "Nmap", "Port and service scanning", R.drawable.scanner, R.color.ios_teal);
    public static final Tool NUCLEI = new Tool(R.id.nuclei_item, "Web Scanner", "Template-based web vulnerability scans", R.drawable.webscan, R.color.ios_purple);
    public static final Tool ARSENAL = new Tool(R.id.arsenal_item, "Arsenal", "Exploit database and scripts", R.drawable.motion_blur, R.color.ios_red);
    public static final Tool METASPLOIT = new Tool(R.id.metasploit_item, "Metasploit", "Framework console and payloads", R.drawable.shield, R.color.ios_pink);
    public static final Tool HID = new Tool(R.id.hid_item, "HID Attacks", "Keystroke injection over USB", R.drawable.keyboard, R.color.ios_orange);
    public static final Tool USB = new Tool(R.id.usb_arsenal_item, "USB Arsenal", "USB gadget modes and profiles", R.drawable.usb, R.color.ios_indigo);
    public static final Tool WORDLISTS = new Tool(R.id.wordlists_item, "Wordlists", "Download, generate and preview lists", R.drawable.summarize, R.color.ios_indigo);
    public static final Tool GEOMAC = new Tool(R.id.geomac_item, "GeoMac", "Locate access points on a map", R.drawable.map, R.color.ios_green);
    public static final Tool VNC = new Tool(R.id.vnc_item, "VNC Desktop", "Linux desktop session", R.drawable.vnc, R.color.ios_blue);
    public static final Tool TERMINAL = new Tool(R.id.terminal_item, "Terminal", "Shell in the Linux engine", R.drawable.terminal, R.color.ios_gray);
    public static final Tool LOGS = new Tool(R.id.logs_item, "Logs", "Everything the tools printed", R.drawable.bug_report, R.color.ios_gray);
    public static final Tool MANAGER = new Tool(R.id.manager_item, "Core Manager", "Packages inside the engine", R.drawable.tune, R.color.ios_gray);
    public static final Tool SETTINGS = new Tool(R.id.settings_item, "Settings", "Engine, interfaces and app options", R.drawable.settings, R.color.ios_gray);
    public static final Tool ABOUT = new Tool(R.id.about_item, "About", "Version, licence and credits", R.drawable.info_outlined, R.color.ios_blue);
    public static final Tool HOME = new Tool(R.id.dasboard_item, "CharonSec", "", R.drawable.home, R.color.ios_blue);

    public static final Tab[] TABS = {
            new Tab("Home", "CharonSec", R.drawable.ios_tab_home),
            new Tab("Wireless", "Wireless", R.drawable.ios_tab_wireless,
                    new Group("Wi-Fi", null, WIFI, HANDSHAKES, MAC),
                    new Group("Bluetooth", null, WPAIR)),
            new Tab("Network", "Network", R.drawable.ios_tab_network,
                    new Group("Discovery", null, LAN, NMAP),
                    new Group("Web", null, NUCLEI)),
            new Tab("Attack", "Attack", R.drawable.ios_tab_attack,
                    new Group("Exploits", null, ARSENAL, METASPLOIT),
                    new Group("Hardware", "HID Attacks and USB Arsenal need root and a kernel with USB gadget support.", HID, USB)),
            new Tab("More", "More", R.drawable.ios_tab_more,
                    new Group("Toolkit", null, WORDLISTS, GEOMAC, VNC, TERMINAL),
                    new Group("CharonSec", null, LOGS, MANAGER, SETTINGS, ABOUT)),
    };

    private static final Tool[] ALL = {HOME, WIFI, HANDSHAKES, MAC, WPAIR, LAN, NMAP, NUCLEI, ARSENAL,
            METASPLOIT, HID, USB, WORDLISTS, GEOMAC, VNC, TERMINAL, LOGS, MANAGER, SETTINGS, ABOUT};

    private IosNav() {}

    @Nullable
    public static Tool tool(int id) {
        for (Tool t : ALL) if (t.id == id) return t;
        return null;
    }

    @NonNull
    public static String titleOf(int id) {
        Tool t = tool(id);
        return t == null ? "CharonSec" : t.title;
    }

    /** Tab that lists this tool, or -1. The dashboard is the Home tab itself. */
    public static int tabOf(int id) {
        if (id == R.id.dasboard_item) return TAB_HOME;
        for (int i = 0; i < TABS.length; i++) {
            for (Group g : TABS[i].groups) for (Tool t : g.tools) if (t.id == id) return i;
        }
        return -1;
    }
}
