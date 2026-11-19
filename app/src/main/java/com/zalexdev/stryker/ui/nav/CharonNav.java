package com.zalexdev.stryker.ui.nav;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.ui.dial.RotaryDialView;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Wires the rotary dial (sections), the tool strip above it (tools of the
 * selected section) and the header crumb. Fragment switching itself stays in
 * MainActivity; this class only decides what to open and reflects what is open.
 */
public final class CharonNav {

    public interface Host {
        /** @param direction +1 content comes from the right, -1 from the left, 0 no slide */
        void open(int itemId, int direction);
        boolean isLocked(int itemId);
        @NonNull String lockReason(int itemId);
        @NonNull String titleOf(int itemId);
    }

    public static final class Section {
        public final String label;
        public final int icon;
        public final int[] tools;

        Section(String label, int icon, int... tools) {
            this.label = label;
            this.icon = icon;
            this.tools = tools;
        }
    }

    /** Dial order. The terminal opens its own activity, so it is never a section default. */
    public static final Section[] SECTIONS = {
            new Section("HOME", R.drawable.home, R.id.dasboard_item, R.id.logs_item),
            new Section("WIRELESS", R.drawable.wifi, R.id.wifi_item, R.id.hs_item, R.id.macchanger_item),
            new Section("BLUETOOTH", R.drawable.wpair, R.id.wpair_item),
            new Section("NETWORK", R.drawable.lan, R.id.lan_item, R.id.nmap_item, R.id.nuclei_item),
            new Section("EXPLOIT", R.drawable.shield, R.id.arsenal_item, R.id.metasploit_item),
            new Section("HARDWARE", R.drawable.usb, R.id.hid_item, R.id.usb_arsenal_item),
            new Section("TOOLKIT", R.drawable.terminal, R.id.wordlists_item, R.id.geomac_item,
                    R.id.vnc_item, R.id.terminal_item),
            new Section("SYSTEM", R.drawable.tune, R.id.manager_item, R.id.about_item),
    };

    private static final String PREFS = "charon_nav";

    private final Context ctx;
    private final Host host;
    private final RotaryDialView dial;
    private final HorizontalScrollView stripScroll;
    private final LinearLayout strip;
    private final TextView crumb;
    private final SharedPreferences prefs;
    private final int[] lastTool = new int[SECTIONS.length];
    private int currentItem;
    private int stripSection = -1;

    public CharonNav(@NonNull Context ctx, @NonNull Host host, @NonNull RotaryDialView dial,
                     @NonNull HorizontalScrollView stripScroll, @NonNull LinearLayout strip,
                     @NonNull TextView crumb) {
        this.ctx = ctx;
        this.host = host;
        this.dial = dial;
        this.stripScroll = stripScroll;
        this.strip = strip;
        this.crumb = crumb;
        this.prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        for (int s = 0; s < SECTIONS.length; s++) {
            lastTool[s] = prefs.getInt("last_" + SECTIONS[s].label, 0);
        }

        List<RotaryDialView.Item> items = new ArrayList<>();
        for (Section s : SECTIONS) {
            items.add(new RotaryDialView.Item(s.label,
                    ContextCompat.getDrawable(ctx, s.icon)));
        }
        dial.setItems(items);
        dial.setListener((index, changed) -> onSectionSettled(index));
    }

    // -------------------------------------------------------------- queries

    public static int sectionOf(int itemId) {
        for (int s = 0; s < SECTIONS.length; s++) {
            for (int t : SECTIONS[s].tools) if (t == itemId) return s;
        }
        return -1;
    }

    @NonNull
    public String currentTitle() {
        return currentItem == 0 ? ctx.getString(R.string.app_name) : host.titleOf(currentItem);
    }

    // -------------------------------------------------------------- dial -> app

    private void onSectionSettled(int section) {
        int item = pickTool(section);
        renderStrip(section);
        if (sectionOf(currentItem) == section && item == currentItem) return;
        if (host.isLocked(item)) {
            android.widget.Toast.makeText(ctx, host.lockReason(item),
                    android.widget.Toast.LENGTH_SHORT).show();
            return;
        }
        host.open(item, sectionDirection(sectionOf(currentItem), section));
    }

    private int pickTool(int section) {
        Section s = SECTIONS[section];
        int remembered = lastTool[section];
        if (remembered != 0 && remembered != R.id.terminal_item && contains(s.tools, remembered)
                && !host.isLocked(remembered)) {
            return remembered;
        }
        for (int t : s.tools) {
            if (t != R.id.terminal_item && !host.isLocked(t)) return t;
        }
        return s.tools[0];
    }

    // -------------------------------------------------------------- app -> nav

    /** A tool was opened (from anywhere): move the dial and strip to match, without navigating. */
    public void reflect(int itemId) {
        if (itemId == R.id.terminal_item) return;       // separate activity; content unchanged
        int section = sectionOf(itemId);
        if (section < 0) return;
        currentItem = itemId;
        if (lastTool[section] != itemId) {
            lastTool[section] = itemId;
            prefs.edit().putInt("last_" + SECTIONS[section].label, itemId).apply();
        }
        if (dial.getSelected() != section) dial.setSelected(section, true);
        crumb.setText(String.format(Locale.ROOT, "%02d · %s", section + 1, SECTIONS[section].label));
        renderStrip(section);
    }

    /** Re-evaluate which sections/tools are locked (rootless engine, VM state). */
    public void refreshLocks() {
        for (int s = 0; s < SECTIONS.length; s++) {
            boolean all = true;
            for (int t : SECTIONS[s].tools) if (!host.isLocked(t)) { all = false; break; }
            dial.setLocked(s, all);
        }
        stripSection = -1;
        renderStrip(dial.getSelected());
    }

    /** Back: go to HOME from any other section. Returns false if already home. */
    public boolean onBack() {
        if (dial.getSelected() == 0 && sectionOf(currentItem) == 0) return false;
        dial.setSelected(0, true);
        host.open(pickTool(0), -1);
        return true;
    }

    public void setEnabled(boolean enabled) {
        dial.setEnabled(enabled);
        dial.setAlpha(enabled ? 1f : 0.5f);
        strip.setEnabled(enabled);
        for (int i = 0; i < strip.getChildCount(); i++) strip.getChildAt(i).setEnabled(enabled);
    }

    // -------------------------------------------------------------- strip

    private void renderStrip(int section) {
        Section s = SECTIONS[section];
        if (s.tools.length <= 1) {
            stripScroll.setVisibility(View.GONE);
            stripSection = section;
            return;
        }
        stripScroll.setVisibility(View.VISIBLE);
        if (stripSection != section) {
            strip.removeAllViews();
            for (int t : s.tools) strip.addView(makeChip(t));
            stripSection = section;
            stripScroll.scrollTo(0, 0);
        }
        for (int i = 0; i < strip.getChildCount(); i++) {
            TextView chip = (TextView) strip.getChildAt(i);
            int id = (int) chip.getTag();
            boolean active = id == currentItem;
            boolean locked = host.isLocked(id);
            chip.setTextColor(ContextCompat.getColor(ctx,
                    active ? R.color.ch_signal : locked ? R.color.ch_bone_faint : R.color.ch_bone_dim));
            chip.setBackgroundResource(active ? R.drawable.ch_tab_active : 0);
            chip.setAlpha(locked ? 0.55f : 1f);
        }
    }

    private TextView makeChip(int itemId) {
        TextView tv = new TextView(ctx);
        tv.setTag(itemId);
        tv.setText(host.titleOf(itemId).toUpperCase(Locale.ROOT));
        tv.setTypeface(ResourcesCompat.getFont(ctx, R.font.barlow_condensed_semibold));
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f);
        tv.setLetterSpacing(0.1f);
        tv.setGravity(Gravity.CENTER);
        tv.setSingleLine(true);
        int padH = dp(14);
        tv.setPadding(padH, 0, padH, 0);
        TypedValue ripple = new TypedValue();
        ctx.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true);
        if (ripple.resourceId != 0) tv.setForeground(ContextCompat.getDrawable(ctx, ripple.resourceId));
        tv.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        tv.setOnClickListener(v -> {
            if (host.isLocked(itemId)) {
                android.widget.Toast.makeText(ctx, host.lockReason(itemId),
                        android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            if (itemId != currentItem) {
                com.zalexdev.stryker.ui.Feedback.get(ctx).confirm();
                host.open(itemId, toolDirection(currentItem, itemId));
            }
        });
        return tv;
    }

    /** Which way the dial turned to get from one section to another (shortest way round). */
    private static int sectionDirection(int from, int to) {
        if (from < 0 || from == to) return 0;
        int n = SECTIONS.length;
        int d = Math.floorMod(to - from, n);
        return d <= n / 2 ? 1 : -1;
    }

    private static int toolDirection(int fromItem, int toItem) {
        int s = sectionOf(toItem);
        if (s < 0 || sectionOf(fromItem) != s) return 0;
        int a = indexOf(SECTIONS[s].tools, fromItem), b = indexOf(SECTIONS[s].tools, toItem);
        return Integer.compare(b, a);
    }

    private static int indexOf(int[] arr, int v) {
        for (int i = 0; i < arr.length; i++) if (arr[i] == v) return i;
        return -1;
    }

    private int dp(int v) {
        return Math.round(v * ctx.getResources().getDisplayMetrics().density);
    }

    private static boolean contains(int[] arr, int v) {
        for (int x : arr) if (x == v) return true;
        return false;
    }

    @Nullable
    public static Section section(int index) {
        return index >= 0 && index < SECTIONS.length ? SECTIONS[index] : null;
    }
}
