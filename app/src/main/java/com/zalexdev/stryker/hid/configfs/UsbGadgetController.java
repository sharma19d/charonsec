package com.zalexdev.stryker.hid.configfs;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.zalexdev.stryker.hid.capability.HidCapabilities;
import com.zalexdev.stryker.hid.capability.HidCapabilityProbe;
import com.zalexdev.stryker.utils.Core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UsbGadgetController {

    public static final String GADGET_NAME = "stryker";
    public static final String GADGET_BASE = "/config/usb_gadget/" + GADGET_NAME;
    private static final String VENDOR_GADGET = "/config/usb_gadget/g1";

    private static final String KEYBOARD_DESC_HEX =
            "05010906a101050719e029e71500250175019508810295017508810195057501050819012905910295017503910195067508150025650507190029658100c0";

    private static final String MOUSE_DESC_HEX =
            "05010902a1010901a10005091901290315002501950375018102950175058103050109300931093815018127950375088106c0c0";

    private static final String OK_MARK = "__STRYKER_OK__";
    private static final String ERR_MARK = "__STRYKER_ERR__";
    private static final String FN_MARK = "__STRYKER_FN__";

    private final Core core;
    private final HidCapabilityProbe probe;

    @Nullable private volatile String lastError;
    private final List<String> lastWarnings = Collections.synchronizedList(new ArrayList<>());

    public UsbGadgetController(Context context) {
        this.core = new Core(context);
        this.probe = new HidCapabilityProbe(context);
    }

    public HidCapabilities capabilities() {
        return probe.probe();
    }

    /** Human-readable reason the last {@link #prepare}/{@link #apply} failed, if known. */
    @Nullable
    public String lastError() {
        return lastError;
    }

    /** Substitutions made by the last {@link #prepare}, e.g. "acm → gser.0". */
    @NonNull
    public List<String> lastWarnings() {
        synchronized (lastWarnings) {
            return new ArrayList<>(lastWarnings);
        }
    }

    @NonNull
    public GadgetState readState() {
        boolean exists = pathExists(GADGET_BASE);
        String udc = read(GADGET_BASE + "/UDC").trim();
        boolean bound = exists && !udc.isEmpty();
        Set<GadgetFunction> linked = EnumSet.noneOf(GadgetFunction.class);
        if (exists) {
            for (GadgetFunction f : GadgetFunction.values()) {
                for (String dir : f.candidateDirs()) {
                    if (pathExists(GADGET_BASE + "/configs/c.1/" + dir)) {
                        linked.add(f);
                        break;
                    }
                }
            }
        }
        String msFile = exists && linked.contains(GadgetFunction.MASS_STORAGE)
                ? read(GADGET_BASE + "/functions/mass_storage.0/lun.0/file").trim()
                : null;
        return new GadgetState(exists, bound, bound ? udc : null, linked, msFile);
    }

    public boolean prepare(@NonNull GadgetProfile profile) {
        lastError = null;
        lastWarnings.clear();
        if (!core.checkRoot()) {
            lastError = "root access denied";
            return false;
        }
        List<String> base = new ArrayList<>();
        base.add("mkdir -p " + sq(GADGET_BASE));
        base.add("mkdir -p " + sq(GADGET_BASE + "/configs/c.1"));
        base.add("mkdir -p " + sq(GADGET_BASE + "/configs/c.1/strings/0x409"));
        base.add("mkdir -p " + sq(GADGET_BASE + "/strings/0x409"));
        base.add("echo " + sq(profile.idVendor) + " > " + sq(GADGET_BASE + "/idVendor"));
        base.add("echo " + sq(profile.idProduct) + " > " + sq(GADGET_BASE + "/idProduct"));
        base.add("echo 0x0200 > " + sq(GADGET_BASE + "/bcdUSB"));
        base.add("echo " + sq(profile.manufacturer) + " > " + sq(GADGET_BASE + "/strings/0x409/manufacturer"));
        base.add("echo " + sq(profile.productName) + " > " + sq(GADGET_BASE + "/strings/0x409/product"));
        base.add("echo " + sq(profile.serialNumber) + " > " + sq(GADGET_BASE + "/strings/0x409/serialnumber"));
        base.add("echo " + sq(profile.configurationLabel) + " > "
                + sq(GADGET_BASE + "/configs/c.1/strings/0x409/configuration"));
        base.add("echo 120 > " + sq(GADGET_BASE + "/configs/c.1/MaxPower"));
        if (!runChecked(base)) return false;

        // Function types depend on the kernel config, so create each one up
        // front and fall back to an equivalent type when it is not built in.
        Map<GadgetFunction, String> dirs = new EnumMap<>(GadgetFunction.class);
        for (GadgetFunction f : profile.functions) {
            String dir = createFunction(f);
            if (dir == null) {
                lastError = "kernel has no USB gadget function for " + f.key
                        + " (tried " + String.join(", ", f.candidateDirs()) + ")";
                return false;
            }
            if (!dir.equals(f.functionDir)) {
                lastWarnings.add(f.key + " → " + dir);
            }
            dirs.put(f, dir);
        }

        List<String> script = new ArrayList<>();
        if (dirs.containsKey(GadgetFunction.HID_KEYBOARD)) {
            script.addAll(prepareHidKeyboard(dirs.get(GadgetFunction.HID_KEYBOARD)));
        }
        if (dirs.containsKey(GadgetFunction.HID_MOUSE)) {
            script.addAll(prepareHidMouse(dirs.get(GadgetFunction.HID_MOUSE)));
        }
        if (dirs.containsKey(GadgetFunction.MASS_STORAGE)) {
            script.addAll(prepareMassStorage(dirs.get(GadgetFunction.MASS_STORAGE),
                    profile.massStorageImage, profile.massStorageReadOnly,
                    profile.massStorageCdrom, profile.inquiryString));
        }
        if ("rndis.usb0".equals(dirs.get(GadgetFunction.RNDIS))) {
            script.addAll(prepareRndis(dirs.get(GadgetFunction.RNDIS)));
        }

        for (String dir : dirs.values()) {
            String link = GADGET_BASE + "/configs/c.1/" + dir;
            script.add("[ -e " + sq(link) + " ] || ln -s "
                    + sq(GADGET_BASE + "/functions/" + dir) + " " + sq(link));
        }

        return runChecked(script);
    }

    /** Creates the first supported function dir for {@code f}; returns its name or null. */
    @Nullable
    private String createFunction(@NonNull GadgetFunction f) {
        StringBuilder sb = new StringBuilder("for d in");
        for (String d : f.candidateDirs()) sb.append(' ').append(sq(d));
        sb.append("; do mkdir -p ").append(sq(GADGET_BASE + "/functions")).append("/\"$d\" 2>/dev/null")
                .append(" && { echo ").append(FN_MARK).append("\"$d\"; break; }; done");
        for (String line : core.customCommand(sb.toString(), true)) {
            if (line == null) continue;
            int i = line.indexOf(FN_MARK);
            if (i >= 0) {
                String dir = line.substring(i + FN_MARK.length()).trim();
                if (!dir.isEmpty()) return dir;
            }
        }
        return null;
    }

    @Nullable
    public String bind() {
        String udc = capabilities().udcName;
        if (udc == null) return null;
        List<String> script = new ArrayList<>();
        // Vendor init keeps retrying to rebind its own gadget, so it can grab
        // the controller between our unbind of g1 and our bind; retry.
        script.add("for i in 1 2 3 4 5; do");
        script.add("  if [ -s " + sq(VENDOR_GADGET + "/UDC") + " ]; then "
                + "echo > " + sq(VENDOR_GADGET + "/UDC") + " 2>/dev/null || true; fi");
        script.add("  echo " + sq(udc) + " > " + sq(GADGET_BASE + "/UDC") + " 2>/dev/null && break");
        script.add("  sleep 0.3");
        script.add("done");
        script.add("sleep 1");
        script.add("[ \"$(cat " + sq(GADGET_BASE + "/UDC") + ")\" = " + sq(udc) + " ] || exit 1");
        if (run(script)) {
            chmodHidNodes();
            return udc;
        }
        return null;
    }

    public boolean unbind() {
        List<String> script = new ArrayList<>();
        script.add("if [ -d " + sq(GADGET_BASE) + " ]; then "
                + "echo > " + sq(GADGET_BASE + "/UDC") + " || true; fi");
        String udc = capabilities().udcName;
        if (udc != null) {
            script.add("if [ -d " + sq(VENDOR_GADGET) + " ]; then "
                    + "echo " + sq(udc) + " > " + sq(VENDOR_GADGET + "/UDC") + " || true; fi");
        }
        return run(script);
    }

    public boolean teardown() {
        List<String> script = new ArrayList<>();
        script.add("if [ -d " + sq(GADGET_BASE) + " ]; then");
        script.add("  echo > " + sq(GADGET_BASE + "/UDC") + " 2>/dev/null || true");
        script.add("  find " + sq(GADGET_BASE + "/configs/c.1") + " -maxdepth 1 -type l -delete 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE + "/configs/c.1/strings/0x409") + " 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE + "/configs/c.1") + " 2>/dev/null || true");
        script.add("  for f in " + sq(GADGET_BASE + "/functions") + "/*; do rmdir \"$f\" 2>/dev/null || true; done");
        script.add("  rmdir " + sq(GADGET_BASE + "/strings/0x409") + " 2>/dev/null || true");
        script.add("  rmdir " + sq(GADGET_BASE) + " 2>/dev/null || true");
        script.add("fi");
        return run(script);
    }

    public boolean apply(@NonNull GadgetProfile profile) {
        if (!prepare(profile)) {
            teardown();
            return false;
        }
        String udc = bind();
        if (udc == null) {
            if (lastError == null) lastError = "could not bind gadget to the USB controller";
            unbind();
            return false;
        }
        return true;
    }

    public boolean swapMassStorageImage(String absoluteImagePath,
                                        boolean readOnly,
                                        boolean cdrom) {
        if (!pathExists(GADGET_BASE + "/functions/mass_storage.0/lun.0")) return false;
        List<String> script = new ArrayList<>();
        script.add("echo " + (readOnly ? "1" : "0") + " > "
                + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/ro"));
        script.add("echo " + (cdrom ? "1" : "0") + " > "
                + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/cdrom"));
        script.add("echo " + sq(absoluteImagePath == null ? "" : absoluteImagePath)
                + " > " + sq(GADGET_BASE + "/functions/mass_storage.0/lun.0/file"));
        return run(script);
    }

    public void chmodHidNodes() {
        core.customCommand("chmod 0666 /dev/hidg0 /dev/hidg1 2>/dev/null", true);
    }

    private List<String> prepareHidKeyboard(String fnDir) {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/" + fnDir;
        s.add("echo 1 > " + sq(dir + "/protocol"));
        s.add("echo 1 > " + sq(dir + "/subclass"));
        s.add("echo 8 > " + sq(dir + "/report_length"));
        s.add("printf '" + hexToEscaped(KEYBOARD_DESC_HEX) + "' > " + sq(dir + "/report_desc"));
        return s;
    }

    private List<String> prepareHidMouse(String fnDir) {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/" + fnDir;
        s.add("echo 2 > " + sq(dir + "/protocol"));
        s.add("echo 1 > " + sq(dir + "/subclass"));
        s.add("echo 4 > " + sq(dir + "/report_length"));
        s.add("printf '" + hexToEscaped(MOUSE_DESC_HEX) + "' > " + sq(dir + "/report_desc"));
        return s;
    }

    private List<String> prepareMassStorage(String fnDir,
                                            @Nullable String image,
                                            boolean readOnly,
                                            boolean cdrom,
                                            @Nullable String inquiry) {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/" + fnDir;
        s.add(optional("echo 1 > " + sq(dir + "/stall")));
        s.add("echo " + (cdrom ? "1" : "0") + " > " + sq(dir + "/lun.0/cdrom"));
        s.add("echo " + (readOnly ? "1" : "0") + " > " + sq(dir + "/lun.0/ro"));
        s.add(optional("echo 0 > " + sq(dir + "/lun.0/nofua")));
        s.add(optional("echo 0 > " + sq(dir + "/lun.0/removable")));
        if (image != null && !image.isEmpty()) {
            s.add("echo " + sq(image) + " > " + sq(dir + "/lun.0/file"));
        }
        if (inquiry != null && !inquiry.isEmpty()) {
            // inquiry_string only exists on kernels >= 4.13.
            s.add(optional("echo " + sq(inquiry) + " > " + sq(dir + "/lun.0/inquiry_string")));
        }
        return s;
    }

    /** Wraps an attribute write that older kernels may not expose. */
    private static String optional(String cmd) {
        return "{ " + cmd + "; } 2>/dev/null || true";
    }

    private List<String> prepareRndis(String fnDir) {
        List<String> s = new ArrayList<>();
        String dir = GADGET_BASE + "/functions/" + fnDir;
        s.add("echo RNDIS > " + sq(dir + "/os_desc/interface.rndis/compatible_id") + " 2>/dev/null || true");
        s.add("echo 5162001 > " + sq(dir + "/os_desc/interface.rndis/sub_compatible_id") + " 2>/dev/null || true");
        return s;
    }

    /**
     * Runs each command in order, stopping at the first failure and recording
     * its shell error in {@link #lastError}.
     */
    private boolean runChecked(List<String> commands) {
        StringBuilder sb = new StringBuilder();
        for (String c : commands) {
            sb.append("{ ").append(c).append("\n} 2>&1 || { echo ").append(ERR_MARK)
                    .append(' ').append(sq(c)).append("; exit 1; }\n");
        }
        sb.append("echo ").append(OK_MARK).append('\n');
        List<String> out = core.customCommand(sb.toString(), true);
        String prev = null;
        for (String line : out) {
            if (line == null) continue;
            if (line.contains(OK_MARK)) return true;
            int i = line.indexOf(ERR_MARK);
            if (i >= 0) {
                String cmd = line.substring(i + ERR_MARK.length()).trim();
                lastError = prev != null && !prev.trim().isEmpty() ? prev.trim() : "failed: " + cmd;
                return false;
            }
            prev = line;
        }
        if (lastError == null) lastError = "root shell returned no result";
        return false;
    }

    private boolean run(List<String> commands) {
        StringBuilder sb = new StringBuilder();
        for (String c : commands) {
            sb.append(c).append('\n');
        }
        sb.append("echo ").append(OK_MARK).append('\n');
        List<String> out = core.customCommand(sb.toString(), true);
        for (String line : out) {
            if (line != null && line.contains(OK_MARK)) return true;
        }
        return false;
    }

    private boolean pathExists(String path) {
        return contains(core.customCommand(
                "[ -e " + sq(path) + " ] && echo 1 || echo 0", true), "1");
    }

    private String read(String path) {
        StringBuilder sb = new StringBuilder();
        List<String> lines = core.customCommand("cat " + sq(path) + " 2>/dev/null", true);
        for (String line : lines) {
            if (line == null) continue;
            if (sb.length() > 0) sb.append('\n');
            sb.append(line);
        }
        return sb.toString();
    }

    private static boolean contains(List<String> list, String needle) {
        if (list == null) return false;
        for (String s : list) {
            if (s != null && s.contains(needle)) return true;
        }
        return false;
    }

    private static String sq(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    private static String hexToEscaped(String hex) {
        StringBuilder sb = new StringBuilder(hex.length() * 2);
        for (int i = 0; i + 1 < hex.length(); i += 2) {
            sb.append("\\x").append(hex, i, i + 2);
        }
        return sb.toString();
    }
}
