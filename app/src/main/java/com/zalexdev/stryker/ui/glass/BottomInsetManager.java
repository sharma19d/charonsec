package com.zalexdev.stryker.ui.glass;

import android.graphics.Rect;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.AbsListView;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.zalexdev.stryker.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Content runs underneath the frosted nav bar. For each screen in the content
 * container this keeps everything reachable:
 * <ul>
 *   <li>vertical lists that reach the bottom get bottom padding (clipToPadding off),
 *       so they scroll behind the glass but their last row can rise above it;</li>
 *   <li>floating action buttons get a larger bottom margin;</li>
 *   <li>if anything else interactive or textual sits under the bar, the whole screen
 *       is padded above the bar instead (sticky for that screen).</li>
 * </ul>
 */
public final class BottomInsetManager {

    private final View content;
    private final View bar;
    private final int touchTolerance;
    private final List<View> roots = new ArrayList<>();
    private final Rect rect = new Rect();
    private boolean lift;

    public BottomInsetManager(@NonNull View content, @NonNull View bar) {
        this.content = content;
        this.bar = bar;
        this.touchTolerance = Math.round(32 * content.getResources().getDisplayMetrics().density);
        bar.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (b - t != ob - ot) {
                for (View root : new ArrayList<>(roots)) evaluate(root);
            }
        });
    }

    public void attach(@NonNull FragmentManager fm) {
        fm.registerFragmentLifecycleCallbacks(new FragmentManager.FragmentLifecycleCallbacks() {
            @Override
            public void onFragmentViewCreated(@NonNull FragmentManager fm, @NonNull Fragment f,
                                              @NonNull View v, @Nullable Bundle state) {
                if (f.getId() == content.getId()) track(v);
            }
        }, false);
    }

    private void track(View root) {
        ViewTreeObserver.OnGlobalLayoutListener listener = () -> evaluate(root);
        View.OnAttachStateChangeListener attach = new View.OnAttachStateChangeListener() {
            @Override
            public void onViewAttachedToWindow(@NonNull View v) {
                if (!roots.contains(v)) roots.add(v);
                v.getViewTreeObserver().addOnGlobalLayoutListener(listener);
            }

            @Override
            public void onViewDetachedFromWindow(@NonNull View v) {
                roots.remove(v);
                v.getViewTreeObserver().removeOnGlobalLayoutListener(listener);
            }
        };
        root.addOnAttachStateChangeListener(attach);
        // The fragment framework usually attaches the view before onFragmentViewCreated.
        if (root.isAttachedToWindow()) attach.onViewAttachedToWindow(root);
    }

    private void evaluate(View root) {
        if (!root.isAttachedToWindow() || root.getParent() == null) return;
        int inset = bar.getVisibility() == View.VISIBLE ? bar.getHeight() : 0;
        int h = content.getHeight();
        if (h == 0) return;

        if (root.getTag(R.id.ch_inset_lifted) != null) {
            setPadBottom(root, inset);
            return;
        }
        if (root.isLayoutRequested() || inset == 0) return;

        lift = false;
        walk(root, h, inset);
        if (lift) {
            revert(root);
            root.setTag(R.id.ch_inset_lifted, Boolean.TRUE);
            setPadBottom(root, inset);
        }
    }

    private void walk(View v, int h, int inset) {
        if (lift || v.getVisibility() != View.VISIBLE || v.getAlpha() == 0f) return;
        rect.set(0, 0, v.getWidth(), v.getHeight());
        try {
            ((ViewGroup) content).offsetDescendantRectToMyCoords(v, rect);
        } catch (IllegalArgumentException notDescendant) {
            return;
        }
        int zoneTop = h - inset;
        if (rect.bottom <= zoneTop) return;

        if (v instanceof FloatingActionButton || v instanceof ExtendedFloatingActionButton) {
            if (v.getTag(R.id.ch_inset_margin) != null
                    && ((Integer) v.getTag(R.id.ch_inset_margin)) + inset == marginBottom(v)) {
                lift = true;                       // already raised but still covered
            } else {
                setMarginBottom(v, inset);
            }
            return;
        }
        if (v instanceof WebView) {
            lift = true;
            return;
        }
        if (isVerticalScroller(v)) {
            if (rect.bottom >= h - touchTolerance) setPadBottom(v, inset);
            else lift = true;
            return;
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount() && !lift; i++) walk(g.getChildAt(i), h, inset);
            return;
        }
        if (isContent(v)) lift = true;
    }

    private static boolean isVerticalScroller(View v) {
        if (v instanceof ScrollView || v instanceof NestedScrollView || v instanceof AbsListView) return true;
        if (v instanceof RecyclerView) {
            RecyclerView.LayoutManager lm = ((RecyclerView) v).getLayoutManager();
            return lm != null && lm.canScrollVertically();
        }
        return false;
    }

    private static boolean isContent(View v) {
        if (v.isClickable() || v instanceof EditText || v instanceof CompoundButton) return true;
        return v instanceof TextView && ((TextView) v).getText().length() > 0;
    }

    // ------------------------------------------------------------ apply / revert

    private static void setPadBottom(View v, int extra) {
        Object orig = v.getTag(R.id.ch_inset_pad);
        if (orig == null) {
            orig = v.getPaddingBottom();
            v.setTag(R.id.ch_inset_pad, orig);
            if (v instanceof ViewGroup) {
                v.setTag(R.id.ch_inset_clip, ((ViewGroup) v).getClipToPadding());
                ((ViewGroup) v).setClipToPadding(false);
            }
        }
        int want = (Integer) orig + extra;
        if (v.getPaddingBottom() != want) {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), want);
        }
    }

    private static int marginBottom(View v) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        return lp instanceof ViewGroup.MarginLayoutParams ? ((ViewGroup.MarginLayoutParams) lp).bottomMargin : 0;
    }

    private static void setMarginBottom(View v, int extra) {
        ViewGroup.LayoutParams lp = v.getLayoutParams();
        if (!(lp instanceof ViewGroup.MarginLayoutParams)) return;
        ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) lp;
        Object orig = v.getTag(R.id.ch_inset_margin);
        if (orig == null) {
            orig = mlp.bottomMargin;
            v.setTag(R.id.ch_inset_margin, orig);
        }
        int want = (Integer) orig + extra;
        if (mlp.bottomMargin != want) {
            mlp.bottomMargin = want;
            v.setLayoutParams(mlp);
        }
    }

    /** Undo list padding and FAB margins inside a screen that is being lifted as a whole. */
    private static void revert(View v) {
        Object pad = v.getTag(R.id.ch_inset_pad);
        if (pad != null) {
            v.setPadding(v.getPaddingLeft(), v.getPaddingTop(), v.getPaddingRight(), (Integer) pad);
            Object clip = v.getTag(R.id.ch_inset_clip);
            if (clip != null && v instanceof ViewGroup) ((ViewGroup) v).setClipToPadding((Boolean) clip);
            v.setTag(R.id.ch_inset_pad, null);
            v.setTag(R.id.ch_inset_clip, null);
        }
        Object margin = v.getTag(R.id.ch_inset_margin);
        if (margin != null && v.getLayoutParams() instanceof ViewGroup.MarginLayoutParams) {
            ViewGroup.MarginLayoutParams mlp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            mlp.bottomMargin = (Integer) margin;
            v.setLayoutParams(mlp);
            v.setTag(R.id.ch_inset_margin, null);
        }
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) revert(g.getChildAt(i));
        }
    }
}
