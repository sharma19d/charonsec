package com.zalexdev.stryker;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.hardware.usb.UsbConstants;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Bundle;
import android.os.StrictMode;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentContainerView;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputEditText;
import com.zalexdev.stryker.about.AboutFragment;
import com.zalexdev.stryker.appintro.AppIntroActivity;
import com.zalexdev.stryker.coremanger.CoreManager;
import com.zalexdev.stryker.custom.Device;
import com.zalexdev.stryker.custom.WiFINetwork;
import com.zalexdev.stryker.dashboard.Dashboard;
import com.zalexdev.stryker.arsenal.ArsenalFragment;
import com.zalexdev.stryker.geomac.GeoMac;
import com.zalexdev.stryker.handshakes.HandshakeStorage;
import com.zalexdev.stryker.localnetwork.LocalMain;
import com.zalexdev.stryker.macchanger.MACChangerFragment;
import com.zalexdev.stryker.metasploit.InstallMetasploit;
import com.zalexdev.stryker.metasploit.utils.MetasploitUtils;
import com.zalexdev.stryker.nmap.NmapScanner;
import com.zalexdev.stryker.nuclei.InstallNuclei;
import com.zalexdev.stryker.ota.UpdateManager;
import com.zalexdev.stryker.settings.SettingsNew;
import com.zalexdev.stryker.utils.Core;
import com.zalexdev.stryker.utils.PromoDialogs;
import com.zalexdev.stryker.hid.ui.HidFragment;
import com.zalexdev.stryker.usbarsenal.ui.UsbArsenalFragment;
import com.zalexdev.stryker.vnc.VNCFragment;
import com.zalexdev.stryker.wifi.Wifi;
import com.zalexdev.stryker.wpair.WpairFragment;

import com.zalexdev.stryker.ui.ios.IosNav;
import com.zalexdev.stryker.ui.ios.IosNavBar;
import com.zalexdev.stryker.ui.ios.IosSectionFragment;
import com.zalexdev.stryker.ui.ios.IosTabBar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import java.lang.ref.WeakReference;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Objects;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

public class MainActivity extends AppCompatActivity implements IosSectionFragment.Host {

    public static final String EXTRA_OPEN_UPDATE = "open_update";

    private static TextView logo;
    private static IosNavBar navBar;
    private static IosTabBar tabBar;
    private static WeakReference<MainActivity> current = new WeakReference<>(null);
    private static int currentTab = IosNav.TAB_HOME;
    private static final ArrayList<CharSequence> titleStack = new ArrayList<>();
    private static boolean toolbarEnabled = true;
    private static int lastSelectedItemId = 0;
    private int lastDepth;
    private Core core;
    private static FragmentManager fragmentManager;
    private static volatile boolean rootlessEngine;
    private static volatile boolean vmReady;
    private boolean engineWatchPending;
    private View engineStatusView;
    private static final java.util.Set<Integer> ROOT_ONLY_IDS = new java.util.HashSet<>(java.util.Arrays.asList(
            R.id.hid_item, R.id.usb_arsenal_item, R.id.macchanger_item));
    private static final java.util.Set<Integer> VM_INDEPENDENT_IDS = new java.util.HashSet<>(java.util.Arrays.asList(
            R.id.dasboard_item, R.id.logs_item, R.id.about_item,
            R.id.wpair_item, R.id.geomac_item,
            R.id.wordlists_item, R.id.settings_item));
    private MetasploitUtils metasploitUtils;
    private ArrayList<WiFINetwork> networks;
    private ArrayList<Device> devices = new ArrayList<>();
    private boolean usbState = false;
    private BottomSheetDialog usbDialog;
    private com.zalexdev.stryker.netdetect.UsbDialogRenderer usbRenderer;
    private final Receiver receiver = new Receiver();

    private volatile boolean landed;
    private volatile boolean launchRunning;
    private boolean firstResume = true;

    @SuppressLint({"NonConstantResourceId", "UseCompatLoadingForDrawables", "SetTextI18n", "SdCardPath"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        current = new WeakReference<>(this);
        core = new Core(this);
        com.zalexdev.stryker.logger.LogStore.from(getApplicationContext());
        setContentView(R.layout.activity_main);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            String NOTIFICATION_PERMISSION = Manifest.permission.POST_NOTIFICATIONS;
            if (checkSelfPermission(NOTIFICATION_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, new String[]{NOTIFICATION_PERMISSION}, 1337);
            }
        }
        StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
        StrictMode.setThreadPolicy(policy);
        fragmentManager = getSupportFragmentManager();

        logo = findViewById(R.id.stryker_main_logo);
        navBar = new IosNavBar(findViewById(R.id.nav_back), findViewById(R.id.nav_back_label), logo,
                findViewById(R.id.nav_hairline), findViewById(R.id.engine_pill));
        findViewById(R.id.nav_back).setOnClickListener(v -> onBackPressed());
        tabBar = findViewById(R.id.tab_bar);
        tabBar.setTabs(IosNav.TABS);
        tabBar.setSelected(currentTab);
        tabBar.setListener((index, again) -> {
            if (again) popToRoot();
            else switchTab(index);
        });
        if (titleStack.isEmpty()) titleStack.add(IosNav.TABS[currentTab].title);

        fragmentManager.addOnBackStackChangedListener(() -> {
            // Pops slide the leaving screen away on top; pushes slide the new one in on top.
            int depth = fragmentManager.getBackStackEntryCount();
            container().setDrawDisappearingViewsLast(depth < lastDepth);
            if (lastDepth == 0 && depth > 0) slideRoot(true);
            else if (lastDepth > 0 && depth == 0) slideRoot(false);
            lastDepth = depth;
            syncNavBar();
        });
        fragmentManager.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@androidx.annotation.NonNull FragmentManager fm,
                                              @androidx.annotation.NonNull Fragment f,
                                              @androidx.annotation.NonNull View v, Bundle state) {
                if (f.getId() != R.id.flContent && f.getId() != R.id.rootContent) return;
                if (f instanceof Dashboard) navBar.installLargeTitle(v, IosNav.TABS[IosNav.TAB_HOME].title);
                syncNavBar();
            }
        }, false);

        if (savedInstanceState == null) {
            fragmentManager.beginTransaction().replace(R.id.flContent, new Initializing()).commit();
        }

        new Thread(this::copyAssets).start();
        if (core.getBoolean("clean_after_update")) {
            new Thread(() -> core.deleteFile("/data/data/com.zalexdev.stryker/files/Stryker.apk")).start();
            core.remove("clean_after_update");
        }

        runLaunchFlow();

        engineStatusView = findViewById(R.id.main_header);
        findViewById(R.id.engine_pill).setOnClickListener(v -> openTool(R.id.settings_item));

        refreshNavLocks();
        refreshEngineStatus();

        if (lastSelectedItemId == 0) {
            receiver.changeFragmentQuiet(R.id.dasboard_item);
        }

        boolean openUpdate = getIntent() != null
                && getIntent().getBooleanExtra(EXTRA_OPEN_UPDATE, false);
        UpdateManager.checkAndPrompt(this, openUpdate);
    }

    @Override
    protected void onStart() {
        super.onStart();
        applySystemInsets();
    }

    /**
     * Edge to edge: the status bar sits over the navigation bar's background and the
     * tab bar runs under the gesture area; the keyboard pushes content up instead.
     */
    private void applySystemInsets() {
        View content = findViewById(android.R.id.content);
        ViewCompat.setOnApplyWindowInsetsListener(content, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.displayCutout());
            Insets ime = insets.getInsets(WindowInsetsCompat.Type.ime());
            boolean tabs = tabBar != null && tabBar.getVisibility() == View.VISIBLE;
            int bottom = ime.bottom > 0 ? ime.bottom : (tabs ? 0 : bars.bottom);
            v.setPadding(bars.left, bars.top, bars.right, bottom);
            if (tabBar != null) tabBar.setPadding(0, 0, 0, ime.bottom > 0 ? 0 : bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
        ViewCompat.requestApplyInsets(content);
    }

    // ------------------------------------------------------------------ navigation

    private FragmentContainerView container() {
        return findViewById(R.id.flContent);
    }

    /** Reflects the fragment on screen in the bars: splash, tab root, or pushed screen. */
    private void syncNavBar() {
        if (navBar == null || fragmentManager == null) return;
        Fragment top = fragmentManager.findFragmentById(R.id.flContent);
        Fragment cur = top != null ? top : rootFor(currentTab);
        boolean splash = cur == null || cur instanceof Initializing;
        boolean ownHeader = hasOwnHeader(cur);
        View header = findViewById(R.id.main_header);
        header.setVisibility(splash ? View.INVISIBLE : ownHeader ? View.GONE : View.VISIBLE);
        findViewById(R.id.nav_hairline).setVisibility(ownHeader ? View.GONE : View.VISIBLE);
        int tabsVis = splash || cur instanceof Error ? View.GONE : View.VISIBLE;
        if (tabBar.getVisibility() != tabsVis) {
            tabBar.setVisibility(tabsVis);
            ViewCompat.requestApplyInsets(findViewById(android.R.id.content));
        }
        tabBar.setSelected(currentTab);
        int depth = fragmentManager.getBackStackEntryCount();
        if (titleStack.isEmpty()) titleStack.add(IosNav.TABS[currentTab].title);
        titleStack.set(0, IosNav.TABS[currentTab].title);
        while (titleStack.size() > depth + 1) titleStack.remove(titleStack.size() - 1);
        while (titleStack.size() < depth + 1) titleStack.add(navBar.getTitle());
        if (depth == 0) {
            navBar.showRoot(titleStack.get(0), currentTab == IosNav.TAB_HOME);
            if (cur != null && cur.getView() != null) navBar.refreshRoot(cur.getView());
        } else {
            navBar.showPushed(titleStack.get(depth), titleStack.get(depth - 1));
            if (cur != null && cur.getView() != null) navBar.adoptContentTitle(cur.getView());
        }
    }

    /** Consoles, reports and the log viewer draw their own bar with a back chevron. */
    private static final int[] OWN_BACK_IDS = {R.id.back, R.id.terminal_back, R.id.wpair_terminal_back,
            R.id.report_back, R.id.wpair_rec_back};

    private static boolean hasOwnHeader(Fragment f) {
        View v = f == null ? null : f.getView();
        if (v == null) return false;
        for (int id : OWN_BACK_IDS) {
            View b = v.findViewById(id);
            if (b != null && b.getVisibility() == View.VISIBLE) return true;
        }
        return false;
    }

    private void push(Fragment fragment, CharSequence title, String name) {
        if (fragmentManager.isStateSaved()) return;
        int depth = fragmentManager.getBackStackEntryCount();
        while (titleStack.size() > depth + 1) titleStack.remove(titleStack.size() - 1);
        while (titleStack.size() < depth + 1) titleStack.add(navBar.getTitle());
        titleStack.add(title);
        // The incoming screen slides over the outgoing one.
        container().setDrawDisappearingViewsLast(false);
        fragmentManager.beginTransaction()
                .setCustomAnimations(R.anim.ios_push_enter, R.anim.ios_push_exit,
                        R.anim.ios_pop_enter, R.anim.ios_pop_exit)
                .replace(R.id.flContent, fragment)
                .addToBackStack(name == null ? "push" : name)
                .commit();
    }

    private void popOne() {
        container().setDrawDisappearingViewsLast(true);
        fragmentManager.popBackStack();
    }

    private void popToRoot() {
        if (!toolbarEnabled || fragmentManager.getBackStackEntryCount() == 0) return;
        container().setDrawDisappearingViewsLast(true);
        fragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
    }

    private static final String ROOT_TAG = "ios_root_";

    private Fragment rootFor(int tab) {
        return fragmentManager == null ? null : fragmentManager.findFragmentByTag(ROOT_TAG + tab);
    }

    /** Shows one tab's root, creating it the first time; the others stay alive but hidden. */
    private void showRoot(int tab) {
        FragmentTransaction t = fragmentManager.beginTransaction();
        for (int i = 0; i < IosNav.TABS.length; i++) {
            Fragment f = rootFor(i);
            if (f != null && i != tab && !f.isHidden()) t.hide(f);
        }
        Fragment r = rootFor(tab);
        if (r == null) {
            r = tab == IosNav.TAB_HOME ? new Dashboard() : IosSectionFragment.newInstance(tab);
            t.add(R.id.rootContent, r, ROOT_TAG + tab);
        } else if (r.isHidden()) {
            t.show(r);
        }
        t.commitNowAllowingStateLoss();
    }

    /** Drops every pushed screen (and the splash / error page) without animating. */
    private void clearPushed() {
        if (fragmentManager.getBackStackEntryCount() > 0) {
            fragmentManager.popBackStackImmediate(null, FragmentManager.POP_BACK_STACK_INCLUSIVE);
        }
        Fragment top = fragmentManager.findFragmentById(R.id.flContent);
        if (top != null) fragmentManager.beginTransaction().remove(top).commitNowAllowingStateLoss();
        endTransitions();
        lastDepth = 0;
        View roots = findViewById(R.id.rootContent);
        roots.animate().cancel();
        roots.setTranslationX(0f);
        roots.setVisibility(View.VISIBLE);
    }

    /** The tab root shifts left under a pushed screen (and back on pop), like UINavigationController. */
    private void slideRoot(boolean away) {
        View roots = findViewById(R.id.rootContent);
        float shift = -0.3f * roots.getWidth();
        roots.animate().cancel();
        roots.setVisibility(View.VISIBLE);
        android.view.animation.Interpolator ease =
                android.view.animation.AnimationUtils.loadInterpolator(this, R.anim.ios_ease);
        if (away) {
            roots.setTranslationX(0f);
            roots.animate().translationX(shift).setDuration(getResources().getInteger(R.integer.ios_push_duration))
                    .setInterpolator(ease)
                    .withEndAction(() -> {
                        if (fragmentManager.getBackStackEntryCount() > 0) roots.setVisibility(View.INVISIBLE);
                    }).start();
        } else {
            roots.setTranslationX(shift);
            roots.animate().translationX(0f).setDuration(getResources().getInteger(R.integer.ios_push_duration))
                    .setInterpolator(ease).withEndAction(null).start();
        }
    }

    /** Tab switches are instant, like iOS: drop the pushed screens and show the tab's root. */
    private void switchTab(int index) {
        if (!toolbarEnabled || fragmentManager.isStateSaved()) {
            tabBar.setSelected(currentTab);
            return;
        }
        currentTab = index;
        tabBar.setSelected(index);
        clearPushed();
        titleStack.clear();
        titleStack.add(IosNav.TABS[index].title);
        showRoot(index);
        lastSelectedItemId = index == IosNav.TAB_HOME ? R.id.dasboard_item : 0;
        syncNavBar();
    }

    /** Cut short any slide still running from the screens a tab switch just removed. */
    private void endTransitions() {
        FragmentContainerView c = container();
        for (int i = c.getChildCount() - 1; i >= 0; i--) {
            View child = c.getChildAt(i);
            child.clearAnimation();
            c.endViewTransition(child);
        }
    }

    @Override
    public void openTool(int itemId) {
        if (!toolbarEnabled) return;
        if (isItemLocked(itemId)) {
            new MaterialAlertDialogBuilder(this)
                    .setTitle(IosNav.titleOf(itemId) + " Unavailable")
                    .setMessage(lockReasonFor(itemId))
                    .setPositiveButton(android.R.string.ok, null)
                    .show();
            return;
        }
        if (itemId == R.id.terminal_item) {
            Intent terminal = new Intent(this, com.stryker.terminal.ui.term.NeoTermActivity.class);
            terminal.putExtra(com.stryker.terminal.ui.term.NeoTermActivity.EXTRA_NEW_SESSION, true);
            startActivity(terminal);
            return;
        }
        if (itemId == R.id.dasboard_item) {
            if (currentTab == IosNav.TAB_HOME) popToRoot();
            else switchTab(IosNav.TAB_HOME);
            return;
        }
        Fragment target = Receiver.fragmentFor(itemId);
        if (target == null) return;
        lastSelectedItemId = itemId;
        push(target, IosNav.titleOf(itemId), "tool");
    }

    @Override
    public boolean isToolLocked(int toolId) {
        return isItemLocked(toolId);
    }

    @androidx.annotation.NonNull
    @Override
    public String lockReason(int toolId) {
        return lockReasonFor(toolId);
    }

    @Override
    public IosNavBar navBar() {
        return navBar;
    }

    private void refreshEngineStatus() {
        View navView = engineStatusView;
        if (navView == null || core == null) return;
        TextView chrootStatus = navView.findViewById(R.id.drawer_chroot_status);
        View statusDot = navView.findViewById(R.id.drawer_status_dot);
        if (chrootStatus == null && statusDot == null) return;
        if (!com.zalexdev.stryker.engine.EngineType.isChosen(core)) {
            vmReady = false;
            if (chrootStatus != null) chrootStatus.setText(R.string.engine_not_chosen);
            if (statusDot != null) {
                try {
                    statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                            ContextCompat.getColor(this, R.color.status_offline)));
                } catch (Exception ignored) {}
            }
            return;
        }
        new Thread(() -> {
            boolean rootless = core.isRootless();
            boolean mounted = !rootless && core.isMounted();
            com.zalexdev.stryker.engine.EngineStatus es =
                    com.zalexdev.stryker.engine.EngineStatus.current(core, mounted, rootless);
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (chrootStatus != null) chrootStatus.setText(es.label);
                if (statusDot != null) {
                    try {
                        statusDot.setBackgroundTintList(android.content.res.ColorStateList.valueOf(
                                ContextCompat.getColor(this, es.colorRes)));
                    } catch (Exception ignored) {}
                }
                boolean changed = vmReady != es.ready;
                vmReady = es.ready;
                if (changed) refreshNavLocks();
                if (rootless && !es.ready && !engineWatchPending) {
                    engineWatchPending = true;
                    navView.postDelayed(() -> {
                        engineWatchPending = false;
                        refreshEngineStatus();
                    }, 3000);
                }
            });
        }, "drawer-engine-status").start();
    }

    private void refreshNavLocks() {
        rootlessEngine = core != null && core.isRootless();
        if (fragmentManager == null) return;
        for (int i = 0; i < IosNav.TABS.length; i++) {
            Fragment f = rootFor(i);
            if (f instanceof IosSectionFragment && f.getView() != null) ((IosSectionFragment) f).refresh();
        }
    }

    private static boolean isItemLocked(int itemId) {
        if (!rootlessEngine) return false;
        return ROOT_ONLY_IDS.contains(itemId)
                || (!vmReady && !VM_INDEPENDENT_IDS.contains(itemId));
    }

    private static String lockReasonFor(int itemId) {
        return ROOT_ONLY_IDS.contains(itemId)
                ? "Needs root hardware, so it isn't available on the rootless engine."
                : "Start the Linux engine from Home first.";
    }

    public MetasploitUtils getMetasploitUtils() {
        return metasploitUtils;
    }

    public void setMetasploitUtils(MetasploitUtils metasploitUtils) {
        this.metasploitUtils = metasploitUtils;
    }

    public ArrayList<Device> getDevices() {
        return devices;
    }

    public void setDevices(ArrayList<Device> devices) {
        this.devices = devices;
    }

    public ArrayList<WiFINetwork> getNetworks() {
        return networks;
    }

    public void setNetworks(ArrayList<WiFINetwork> networks) {
        this.networks = networks;
    }

    private com.zalexdev.stryker.netdetect.UsbFlow usbFlow;

    private void usbDialog() {
        if (usbDialog != null && usbDialog.isShowing()) {
            startUsbFlow();
            return;
        }
        usbDialog = new BottomSheetDialog(this, R.style.ThemeOverlay_Stryker_BottomSheetDialog);
        usbDialog.setDismissWithAnimation(true);
        usbDialog.setContentView(R.layout.usb_dialog);

        com.zalexdev.stryker.netdetect.UsbDialogRenderer renderer =
                new com.zalexdev.stryker.netdetect.UsbDialogRenderer(
                        usbDialog.getWindow() == null
                                ? usbDialog.findViewById(android.R.id.content)
                                : usbDialog.getWindow().getDecorView(),
                        this::applyUsbFix);
        usbRenderer = renderer;
        renderer.renderEmpty();

        View refresh = usbDialog.findViewById(R.id.usb_refresh_btn);
        if (refresh != null) refresh.setOnClickListener(v -> startUsbFlow());

        usbDialog.setOnDismissListener(d -> {
            if (usbFlow != null) usbFlow.cancel();
            usbFlow = null;
            usbRenderer = null;
        });
        usbDialog.show();
        startUsbFlow();
    }

    private void startUsbFlow() {
        if (usbFlow != null) usbFlow.cancel();
        final com.zalexdev.stryker.netdetect.UsbDialogRenderer renderer = usbRenderer;
        if (renderer == null) return;
        usbFlow = new com.zalexdev.stryker.netdetect.UsbFlow(this, (report, chain) ->
                runOnUiThread(() -> {
                    if (usbDialog == null || !usbDialog.isShowing() || usbRenderer != renderer) {
                        return;
                    }
                    renderer.render(report, chain);
                }));
        usbFlow.start();
    }

    private void applyUsbFix(com.zalexdev.stryker.netdetect.UsbChain.Fix fix,
                             com.zalexdev.stryker.netdetect.UsbDeviceReport report) {
        switch (fix) {
            case SETUP_GUEST:
                if (usbDialog != null) usbDialog.dismiss();
                com.zalexdev.stryker.wifi.guest.WifiGuestSetupDialog.show(this, (armed, iface) -> {});
                break;

            case SEARCH_DRIVER:
                openUrl(com.zalexdev.stryker.netdetect.UsbChain.searchUrl(report));
                break;

            case RETRY:
                startUsbFlow();
                break;

            default:
                break;
        }
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Exception ignored) {
        }
    }

    private boolean isConnected() {
        UsbManager manager = (UsbManager) getSystemService(Context.USB_SERVICE);
        if (manager == null) return false;
        for (UsbDevice d : manager.getDeviceList().values()) {
            if (d.getDeviceClass() != UsbConstants.USB_CLASS_HUB) return true;
        }
        return false;
    }

    public void openUsbSheet() {
        runOnUiThread(this::usbDialog);
    }

    private void schedulePromo() {
        new android.os.Handler(getMainLooper()).postDelayed(
                () -> PromoDialogs.maybeShow(MainActivity.this), 1500);
    }

    private void startRootlessLaunch() {
        com.zalexdev.stryker.engine.GuestEngine engine =
                com.zalexdev.stryker.engine.Engines.active(core);
        boolean forced = core.getBoolean(
                com.zalexdev.stryker.engine.EngineType.PREF_FORCED);
        if (!core.getBoolean("first_open")) {
            core.putString("username", "User");
            launchRunning = false;
            startActivity(new Intent(this, AppIntroActivity.class));
            return;
        }
        com.zalexdev.stryker.engine.EngineType chosen =
                com.zalexdev.stryker.engine.EngineType.active(core);
        if (!forced && com.zalexdev.stryker.engine.EnginePayload.outdated(core)) {
            launchRunning = false;
            startActivity(AppIntroActivity.replaceIntent(this, chosen));
            return;
        }
        if (!engine.isInstalled()) {
            launchRunning = false;
            startActivity(AppIntroActivity.updateIntent(this, chosen));
            return;
        }
        if (!isEngineVerified(engine) && !forced) {
            core.putString("username", "User");
            launchRunning = false;
            startActivity(new Intent(this, AppIntroActivity.class));
            return;
        }
        com.zalexdev.stryker.engine.RootlessService.start(this);
        landOn(new Dashboard());
        schedulePromo();
        checkForUsb();
        if (!isConnected()) {
            new Thread(() -> core.getInterfacesList()).start();
        }
        if (!forced) watchEngineBoot(engine);
    }

    private boolean isEngineVerified(com.zalexdev.stryker.engine.GuestEngine engine) {
        String key = com.zalexdev.stryker.engine.EngineType.PREF_VERIFIED;
        if (core.contains(key)) return core.getBoolean(key);
        if (engine.isInstalled() && core.getBoolean("first_open")) {
            core.putBoolean(key, true);
            return true;
        }
        return false;
    }

    private static final long LAUNCH_BOOT_DEADLINE_MS = 210_000;

    private void watchEngineBoot(com.zalexdev.stryker.engine.GuestEngine engine) {
        Thread watchdog = new Thread(() -> {
            long deadline = System.currentTimeMillis() + LAUNCH_BOOT_DEADLINE_MS;
            while (System.currentTimeMillis() < deadline) {
                if (isFinishing() || isDestroyed()) return;
                if (engine.isReady()) return;
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
            runOnUiThread(() -> showEngineGate(engine));
        }, "engine-watchdog");
        watchdog.setDaemon(true);
        watchdog.start();
    }

    private void showEngineGate(com.zalexdev.stryker.engine.GuestEngine dead) {
        if (isFinishing() || isDestroyed()) return;
        com.zalexdev.stryker.engine.EngineType failed = dead.type();
        com.zalexdev.stryker.engine.EngineType next =
                failed == com.zalexdev.stryker.engine.EngineType.UML
                        ? com.zalexdev.stryker.engine.EngineType.ROOTLESS
                        : com.zalexdev.stryker.engine.EngineType.UML;
        boolean haveAlternative =
                com.zalexdev.stryker.engine.EngineType.rootlessSupported(this)
                        && failed != com.zalexdev.stryker.engine.EngineType.CHROOT;

        androidx.appcompat.app.AlertDialog.Builder b =
                new androidx.appcompat.app.AlertDialog.Builder(this)
                        .setCancelable(false)
                        .setTitle(R.string.setup_gate_title);

        if (haveAlternative) {
            String nextName = com.zalexdev.stryker.engine.Engines.active(this, next).displayName();
            b.setMessage(getString(R.string.setup_gate_body, dead.displayName(), nextName))
                    .setPositiveButton(R.string.setup_gate_action,
                            (d, w) -> reinstallOn(next));
        } else {
            b.setMessage(R.string.setup_gate_dead_body)
                    .setPositiveButton(R.string.setup_gate_retry, (d, w) -> reinstallOn(failed))
                    .setNegativeButton(R.string.setup_gate_quit, (d, w) -> finishAffinity());
        }
        b.show();
    }

    private void reinstallOn(com.zalexdev.stryker.engine.EngineType next) {
        Context appContext = getApplicationContext();
        Thread stop = new Thread(() -> com.zalexdev.stryker.engine.Engines.stopAll(appContext));
        stop.setDaemon(true);
        stop.start();
        com.zalexdev.stryker.engine.EngineType.persist(core, next);
        core.putBoolean(com.zalexdev.stryker.engine.EngineType.PREF_VERIFIED, false);
        Intent intent = new Intent(this, AppIntroActivity.class);
        intent.putExtra(AppIntroActivity.EXTRA_REPAIR_ENGINE, next.name());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        finish();
    }

    private void runLaunchFlow() {
        if (core == null || landed || launchRunning) return;
        launchRunning = true;
        if (core.isRootless()) {
            startRootlessLaunch();
            return;
        }
        if (!core.getBoolean("first_open")) {
            core.putString("username", "User");
            launchRunning = false;
            startActivity(new Intent(this, AppIntroActivity.class));
            return;
        }
        new Thread(() -> {
            if (core.probeFile(Core.CHROOT_MARKER) == Core.Presence.NO
                    || core.probeFolder(Core.CHROOT_ROOT + "/usr") == Core.Presence.NO) {
                launchRunning = false;
                Intent update = AppIntroActivity.updateIntent(this,
                        com.zalexdev.stryker.engine.EngineType.CHROOT);
                if (core.hasLegacyChroot()) {
                    update.putExtra(AppIntroActivity.EXTRA_MIGRATE, true);
                }
                startActivity(update);
                return;
            }
            boolean mounted = core.isMounted() || core.mountCore();
            if (!mounted) {
                landOn(new Error());
                return;
            }
            landOn(new Dashboard());
            runOnUiThread(() -> {
                schedulePromo();
                checkForUsb();
            });
            if (!isConnected()) {
                new Thread(() -> core.getInterfacesList()).start();
            }
            new Thread(() -> {
                if (core != null && core.getBoolean("msf")) {
                    metasploitUtils = new MetasploitUtils(MainActivity.this, MainActivity.this);
                }
                if (core != null) core.chmodFolder("/data/data/com.zalexdev.stryker/files");
            }).start();
        }).start();
    }

    private void landOn(Fragment fragment) {
        runOnUiThread(() -> {
            if (isFinishing() || isDestroyed()) return;
            landed = true;
            launchRunning = false;
            if (fragmentManager.isStateSaved()) return;
            clearPushed();
            titleStack.clear();
            if (fragment instanceof Dashboard) {
                currentTab = IosNav.TAB_HOME;
                titleStack.add(IosNav.TABS[IosNav.TAB_HOME].title);
                showRoot(IosNav.TAB_HOME);
            } else {
                titleStack.add(IosNav.TABS[currentTab].title);
                fragmentManager.beginTransaction().replace(R.id.flContent, fragment).commitNowAllowingStateLoss();
            }
            syncNavBar();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshEngineStatus();
        if (firstResume) {
            firstResume = false;
            return;
        }
        runLaunchFlow();
    }

    @SuppressLint("SdCardPath")
    private void copyAssets() {
        AssetManager assetManager = getAssets();
        String[] files = null;
        try {
            files = assetManager.list("");
        } catch (IOException e) {
        }
        if (files != null) for (String filename : files) {
            if (filename.equals(Core.BUSYBOX_ASSET)) continue;
            if (filename.equals("rootless")) continue;
            InputStream in = null;
            OutputStream out = null;
            try {
                in = assetManager.open(filename);
                File outFile = new File("/data/data/com.zalexdev.stryker/files/", filename);
                out = new FileOutputStream(outFile);
                copyFile(in, out);
                out.close();
                out = null;
                outFile.setExecutable(true, false);
            } catch (IOException ignored) {
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }
                }
                if (out != null) {
                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }
                }
            }
        }
        Core.extractBusybox(this);
    }

    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private void checkForUsb() {
        Timer usb = new Timer();
        usb.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                boolean temp = usbState;
                usbState = isConnected();
                if (temp != usbState && usbState) {
                    runOnUiThread(() -> usbDialog());
                } else if (temp != usbState) {
                    runOnUiThread(() -> {
                        if (usbDialog != null) {
                            usbDialog.dismiss();
                        }
                    });
                }
            }
        }, 0, 300);
    }

    @Override
    public void onBackPressed() {
        FragmentManager fm = getSupportFragmentManager();
        Fragment current = fm.findFragmentById(R.id.flContent);
        if (current instanceof SettingsNew && ((SettingsNew) current).popChild()) return;
        if (fm.getBackStackEntryCount() > 0) {
            popOne();
        } else if (current == null && currentTab != IosNav.TAB_HOME) {
            switchTab(IosNav.TAB_HOME);
        } else {
            super.onBackPressed();
        }
    }

    private interface MainActivityReceiver {
        void setTitle(String title);
        void restoreTitle();
        String getTitle();
        void setToolBarMenuEnabled(boolean bool);
        boolean isToolBarMenuEnabled();
        void changeFragmentQuiet(int itemId);
        void changeFragment(int itemId);
        void changeFragment(int motherItemId, Fragment fragment);
        void changeFragment(int motherItemId, Fragment fragment, String backstackName);
    }

    /** Navigation entry point used by every screen; routes into the iOS navigation stack. */
    public static class Receiver implements MainActivityReceiver {

        @Override
        public void setTitle(String title) {
            if (navBar == null) return;
            navBar.setTitle(title);
            if (!titleStack.isEmpty()) titleStack.set(titleStack.size() - 1, title);
        }

        @Override
        public void restoreTitle() {
            if (navBar == null || titleStack.isEmpty()) return;
            navBar.setTitle(titleStack.get(titleStack.size() - 1));
        }

        @Override
        public String getTitle() {
            return navBar == null ? "" : navBar.getTitle().toString();
        }

        @Override
        public void setToolBarMenuEnabled(boolean bool) {
            toolbarEnabled = bool;
            if (tabBar != null) tabBar.setEnabled(bool);
            if (navBar != null) navBar.setBackEnabled(bool);
        }

        @Override
        public boolean isToolBarMenuEnabled() {
            return toolbarEnabled;
        }

        @Override
        public void changeFragmentQuiet(int itemId) {
            lastSelectedItemId = itemId;
        }

        @Override
        public void changeFragment(int itemId) {
            MainActivity a = current.get();
            if (a == null || a.isFinishing()) return;
            a.runOnUiThread(() -> a.openTool(itemId));
        }

        static Fragment fragmentFor(int itemId) {
            if (itemId == R.id.wifi_item) return new Wifi();
            if (itemId == R.id.lan_item) return new LocalMain();
            if (itemId == R.id.macchanger_item) return new MACChangerFragment();
            if (itemId == R.id.dasboard_item) return new Dashboard();
            if (itemId == R.id.arsenal_item) return new ArsenalFragment();
            if (itemId == R.id.manager_item) return new CoreManager();
            if (itemId == R.id.nuclei_item) return new InstallNuclei();
            if (itemId == R.id.hs_item) return new HandshakeStorage();
            if (itemId == R.id.metasploit_item) return new InstallMetasploit();
            if (itemId == R.id.wordlists_item) return new com.zalexdev.stryker.wordlists.WordlistsFragment();
            if (itemId == R.id.geomac_item) return new GeoMac();
            if (itemId == R.id.nmap_item) return new NmapScanner();
            if (itemId == R.id.wpair_item) return new WpairFragment();
            if (itemId == R.id.about_item) return new AboutFragment();
            if (itemId == R.id.logs_item) return new com.zalexdev.stryker.logger.LoggerFragment();
            if (itemId == R.id.vnc_item) return new VNCFragment();
            if (itemId == R.id.hid_item) return new HidFragment();
            if (itemId == R.id.usb_arsenal_item) return new UsbArsenalFragment();
            if (itemId == R.id.settings_item) return new SettingsNew();
            return null;
        }

        @Override
        public void changeFragment(int motherItemId, Fragment fragment) {
            changeFragment(motherItemId, fragment, null);
        }

        @Override
        public void changeFragment(int motherItemId, Fragment fragment, String backstackName) {
            MainActivity a = current.get();
            if (a == null || a.isFinishing()) return;
            lastSelectedItemId = motherItemId;
            CharSequence title = IosNav.tabOf(motherItemId) >= 0 && motherItemId != R.id.dasboard_item
                    ? IosNav.titleOf(motherItemId) : (navBar == null ? "" : navBar.getTitle());
            a.runOnUiThread(() -> a.push(fragment, title, backstackName));
        }
    }
}
