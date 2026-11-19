package com.zalexdev.stryker.hid.configfs;

public enum GadgetFunction {
    HID_KEYBOARD("hid_keyboard", "hid.keyboard"),
    HID_MOUSE("hid_mouse", "hid.mouse"),
    MASS_STORAGE("mass_storage", "mass_storage.0"),
    // Many vendor kernels (e.g. Qualcomm 4.x) ship without the mainline
    // RNDIS/ECM/ACM configfs functions but do have NCM and generic serial,
    // so those are tried when the preferred function type is missing.
    RNDIS("rndis", "rndis.usb0", "ncm.0"),
    ECM("ecm", "ecm.usb0", "ncm.1"),
    ACM("acm", "acm.0", "gser.0"),
    ADB("adb", "ffs.adb");

    public final String key;
    public final String functionDir;
    /** Alternative function dirs tried in order when {@link #functionDir} is unsupported. */
    public final String[] fallbackDirs;

    GadgetFunction(String key, String functionDir, String... fallbackDirs) {
        this.key = key;
        this.functionDir = functionDir;
        this.fallbackDirs = fallbackDirs;
    }

    /** {@link #functionDir} followed by every fallback. */
    public String[] candidateDirs() {
        String[] out = new String[fallbackDirs.length + 1];
        out[0] = functionDir;
        System.arraycopy(fallbackDirs, 0, out, 1, fallbackDirs.length);
        return out;
    }
}
