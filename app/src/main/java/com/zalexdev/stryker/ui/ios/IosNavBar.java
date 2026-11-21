package com.zalexdev.stryker.ui.ios;

import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;

/**
 * Drives the top bar. On a tab root the large title lives in the content and the
 * small centred title plus hairline fade in once it has scrolled under the bar;
 * on pushed screens the small title and a "‹ Back" button are always shown.
 */
public final class IosNavBar {

    private static final long FADE_MS = 160;

    private final View back;
    private final TextView backLabel;
    private final TextView title;
    private final View hairline;
    private final View trailing;
    private boolean root = true;
    private boolean pushedLarge;
    private boolean collapsed;

    public IosNavBar(@NonNull View back, @NonNull TextView backLabel, @NonNull TextView title,
                     @NonNull View hairline, @NonNull View trailing) {
        this.back = back;
        this.backLabel = backLabel;
        this.title = title;
        this.hairline = hairline;
        this.trailing = trailing;
    }

    public void setTitle(@Nullable CharSequence text) {
        title.setText(text);
    }

    @NonNull
    public CharSequence getTitle() {
        return title.getText();
    }

    /** Tab root: no back button, title hidden until the large title scrolls away. */
    public void showRoot(@NonNull CharSequence text, boolean showTrailing) {
        root = true;
        pushedLarge = false;
        collapsed = false;
        title.setText(text);
        back.setVisibility(View.GONE);
        trailing.setVisibility(showTrailing ? View.VISIBLE : View.GONE);
        fade(trailing, 1f, false);
        fade(title, 0f, false);
        fade(hairline, 0f, false);
    }

    /** Pushed screen: back button labelled with the previous screen, title and hairline shown. */
    public void showPushed(@NonNull CharSequence text, @NonNull CharSequence backTo) {
        root = false;
        pushedLarge = false;
        title.setText(text);
        backLabel.setText(backTo.length() > 12 ? "Back" : backTo);
        back.setVisibility(View.VISIBLE);
        trailing.setVisibility(View.GONE);
        fade(title, 1f, false);
        fade(hairline, 1f, false);
    }

    public void setBackEnabled(boolean enabled) {
        back.setEnabled(enabled);
        back.setAlpha(enabled ? 1f : 0.4f);
    }

    /** Scroll position of the root content: collapse once the large title is under the bar. */
    public void onRootScrolled(int scrollY, int largeTitleBottom) {
        if (!root && !pushedLarge) return;
        boolean c = scrollY > Math.max(0, largeTitleBottom - Ios.dp(title.getContext(), 6));
        if (c == collapsed) return;
        collapsed = c;
        fade(title, c ? 1f : 0f, true);
        fade(hairline, c ? 1f : 0f, true);
        // The trailing status capsule would collide with the centred title.
        if (root && trailing.getVisibility() == View.VISIBLE) fade(trailing, c ? 0f : 1f, true);
    }


    /**
     * A pushed screen that starts with its own big heading: treat it as the iOS large
     * title, hiding the small bar title until the heading scrolls under the bar.
     */
    public void adoptContentTitle(@NonNull View screen) {
        if (root) return;
        TextView heading = findHeading(screen, new int[]{0});
        if (heading == null) return;
        android.content.Context c = heading.getContext();
        heading.setTypeface(androidx.core.content.res.ResourcesCompat.getFont(c,
                com.zalexdev.stryker.R.font.charon_display), android.graphics.Typeface.BOLD);
        if (heading.getText().length() <= 22) heading.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 34);
        heading.setLetterSpacing(0.005f);
        title.setText(heading.getText());
        pushedLarge = true;
        collapsed = false;
        fade(title, 0f, false);
        fade(hairline, 0f, false);
        ViewGroup scroller = null;
        for (android.view.ViewParent p = heading.getParent(); p instanceof View && p != screen.getParent(); p = p.getParent()) {
            if (p instanceof NestedScrollView || p instanceof android.widget.ScrollView) {
                scroller = (ViewGroup) p;
                break;
            }
        }
        if (scroller == null) return;
        final ViewGroup sc = scroller;
        sc.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) -> {
            if (sc.getChildCount() == 0) return;
            android.graphics.Rect r = new android.graphics.Rect(0, 0, heading.getWidth(), heading.getHeight());
            try {
                ((ViewGroup) sc.getChildAt(0)).offsetDescendantRectToMyCoords(heading, r);
            } catch (RuntimeException e) {
                return;
            }
            onRootScrolled(y, r.bottom);
        });
    }

    /** First text of 24sp or more, if it is among the first few texts on the screen. */
    @Nullable
    private static TextView findHeading(View v, int[] seen) {
        if (v.getVisibility() == View.GONE || seen[0] > 4) return null;
        if (v instanceof TextView && !(v instanceof android.widget.EditText) && !(v instanceof android.widget.Button)) {
            seen[0]++;
            TextView t = (TextView) v;
            float sp = t.getTextSize() / t.getResources().getDisplayMetrics().scaledDensity;
            return sp >= 24f && t.getText().length() > 0 ? t : null;
        }
        if (v instanceof androidx.recyclerview.widget.RecyclerView || v instanceof android.widget.AbsListView) {
            seen[0] = 99;
            return null;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                TextView t = findHeading(g.getChildAt(i), seen);
                if (t != null) return t;
                if (seen[0] > 4) return null;
            }
        }
        return null;
    }

    private static void fade(View v, float to, boolean animate) {
        v.animate().cancel();
        if (!animate) {
            v.setAlpha(to);
            return;
        }
        v.animate().alpha(to).setDuration(FADE_MS).start();
    }


    /** Re-applies the collapsed state for a root that kept its scroll position (tab roots stay alive). */
    public void refreshRoot(@NonNull View screen) {
        View large = screen.findViewById(com.zalexdev.stryker.R.id.ios_large_title);
        if (large == null) return;
        ViewGroup sc = null;
        for (android.view.ViewParent p = large.getParent(); p instanceof View; p = p.getParent()) {
            if (p instanceof NestedScrollView || p instanceof android.widget.ScrollView) {
                sc = (ViewGroup) p;
                break;
            }
        }
        if (sc == null || sc.getChildCount() == 0) return;
        boolean c = sc.getScrollY() > Math.max(0, large.getBottom() + sc.getChildAt(0).getTop()
                - Ios.dp(title.getContext(), 6));
        collapsed = c;
        fade(title, c ? 1f : 0f, false);
        fade(hairline, c ? 1f : 0f, false);
        if (trailing.getVisibility() == View.VISIBLE) fade(trailing, c ? 0f : 1f, false);
    }

    // ------------------------------------------------------------------ large title

    /**
     * Puts an iOS large title at the top of a screen's scrolling column and wires
     * it to the bar. Returns false when the screen has no suitable scroll view.
     */
    public boolean installLargeTitle(@NonNull View screen, @NonNull CharSequence text) {
        NestedScrollView nested = find(screen, NestedScrollView.class);
        android.widget.ScrollView plain = nested == null ? find(screen, android.widget.ScrollView.class) : null;
        ViewGroup scroller = nested != null ? nested : plain;
        if (scroller == null || scroller.getChildCount() == 0
                || !(scroller.getChildAt(0) instanceof android.widget.LinearLayout)) return false;
        android.widget.LinearLayout column = (android.widget.LinearLayout) scroller.getChildAt(0);
        if (column.getOrientation() != android.widget.LinearLayout.VERTICAL) return false;

        View existing = column.findViewById(com.zalexdev.stryker.R.id.ios_large_title);
        TextView large = existing instanceof TextView ? (TextView) existing : LargeTitle.create(screen.getContext());
        large.setText(text);
        if (existing == null) {
            android.widget.LinearLayout.LayoutParams lp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.bottomMargin = Ios.dp(screen.getContext(), 6);
            int side = Ios.dp(screen.getContext(), 16) - column.getPaddingStart();
            large.setPadding(Math.max(0, side), 0, 0, 0);
            column.addView(large, 0, lp);
            if (column.getPaddingTop() > Ios.dp(screen.getContext(), 4)) {
                column.setPadding(column.getPaddingLeft(), Ios.dp(screen.getContext(), 2),
                        column.getPaddingRight(), column.getPaddingBottom());
            }
        }
        scroller.setOnScrollChangeListener((View.OnScrollChangeListener) (v, x, y, ox, oy) ->
                onRootScrolled(y, large.getBottom() + column.getTop()));
        return true;
    }

    @Nullable
    private static <T extends View> T find(View v, Class<T> type) {
        if (type.isInstance(v)) return type.cast(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                T t = find(g.getChildAt(i), type);
                if (t != null) return t;
            }
        }
        return null;
    }
}
