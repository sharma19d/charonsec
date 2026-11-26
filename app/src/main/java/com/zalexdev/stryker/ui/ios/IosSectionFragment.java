package com.zalexdev.stryker.ui.ios;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.widget.NestedScrollView;
import androidx.fragment.app.Fragment;

import com.zalexdev.stryker.R;

/** Tab root in the style of iOS Settings: large title, inset grouped list of tools. */
public class IosSectionFragment extends Fragment {

    public interface Host {
        void openTool(int toolId);
        boolean isToolLocked(int toolId);
        @NonNull String lockReason(int toolId);
        @Nullable IosNavBar navBar();
    }

    private static final String ARG_TAB = "tab";

    public static IosSectionFragment newInstance(int tab) {
        IosSectionFragment f = new IosSectionFragment();
        Bundle b = new Bundle();
        b.putInt(ARG_TAB, tab);
        f.setArguments(b);
        return f;
    }

    private LinearLayout column;

    public int tab() {
        return getArguments() == null ? 0 : getArguments().getInt(ARG_TAB);
    }

    @Nullable
    private Host host() {
        return getActivity() instanceof Host ? (Host) getActivity() : null;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle state) {
        Context c = inflater.getContext();
        NestedScrollView scroll = new NestedScrollView(c);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(ContextCompat.getColor(c, R.color.ch_ink_0));
        scroll.setVerticalScrollBarEnabled(false);
        column = new LinearLayout(c);
        column.setOrientation(LinearLayout.VERTICAL);
        column.setPadding(Ios.dp(c, 16), Ios.dp(c, 2), Ios.dp(c, 16), Ios.dp(c, 32));
        scroll.addView(column, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        build();
        return scroll;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle state) {
        super.onViewCreated(view, state);
        Host h = host();
        IosNavBar bar = h == null ? null : h.navBar();
        if (bar != null) bar.installLargeTitle(view, IosNav.TABS[tab()].title);
    }

    /** Locks change when the engine starts or stops. */
    public void refresh() {
        if (column == null) return;
        View title = column.findViewById(R.id.ios_large_title);
        column.removeAllViews();
        if (title != null) column.addView(title);
        build();
    }

    private void build() {
        Context c = column.getContext();
        IosNav.Tab t = IosNav.TABS[tab()];
        for (IosNav.Group g : t.groups) {
            if (g.header != null) column.addView(sectionHeader(c, g.header));
            LinearLayout card = new LinearLayout(c);
            card.setOrientation(LinearLayout.VERTICAL);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(ContextCompat.getColor(c, R.color.ch_ink_1));
            bg.setCornerRadius(getResources().getDimension(R.dimen.ios_corner_card));
            card.setBackground(bg);
            card.setClipToOutline(true);
            for (int i = 0; i < g.tools.length; i++) {
                if (i > 0) card.addView(separator(c));
                card.addView(row(c, g.tools[i]));
            }
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (g.header == null) lp.topMargin = Ios.dp(c, 22);
            column.addView(card, lp);
            if (g.footer != null) column.addView(footer(c, g.footer));
        }
    }

    private View row(Context c, IosNav.Tool tool) {
        Host h = host();
        boolean locked = h != null && h.isToolLocked(tool.id);

        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ios.dp(c, 60));
        row.setPadding(Ios.dp(c, 16), Ios.dp(c, 9), Ios.dp(c, 14), Ios.dp(c, 9));
        TypedValue tv = new TypedValue();
        c.getTheme().resolveAttribute(android.R.attr.selectableItemBackground, tv, true);
        row.setBackgroundResource(tv.resourceId);
        row.setContentDescription(tool.title);

        ImageView tile = new ImageView(c);
        GradientDrawable tileBg = new GradientDrawable();
        tileBg.setColor(ContextCompat.getColor(c, tool.tile));
        tileBg.setCornerRadius(Ios.dp(c, 7));
        tile.setBackground(tileBg);
        tile.setImageResource(tool.icon);
        tile.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        int pad = Ios.dp(c, 6);
        tile.setPadding(pad, pad, pad, pad);
        row.addView(tile, new LinearLayout.LayoutParams(Ios.dp(c, 30), Ios.dp(c, 30)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = Ios.dp(c, 14);
        tlp.rightMargin = Ios.dp(c, 8);
        row.addView(text, tlp);

        TextView title = new TextView(c);
        title.setTextAppearance(R.style.TextAppearance_Ios_Body);
        title.setText(tool.title);
        title.setSingleLine(true);
        title.setEllipsize(TextUtils.TruncateAt.END);
        text.addView(title);

        TextView sub = new TextView(c);
        sub.setTextAppearance(R.style.TextAppearance_Ios_Footnote);
        sub.setText(locked && h != null ? h.lockReason(tool.id) : tool.subtitle);
        sub.setMaxLines(2);
        sub.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        slp.topMargin = Ios.dp(c, 1);
        text.addView(sub, slp);

        ImageView accessory = new ImageView(c);
        accessory.setImageResource(locked ? R.drawable.ios_lock : R.drawable.ios_chevron_right);
        accessory.setImageTintList(ContextCompat.getColorStateList(c, R.color.ch_bone_faint));
        row.addView(accessory, new LinearLayout.LayoutParams(
                Ios.dp(c, locked ? 14 : 8), Ios.dp(c, 14)));

        if (locked) {
            tile.setAlpha(0.45f);
            title.setAlpha(0.45f);
        }
        row.setOnClickListener(v -> {
            Host hh = host();
            if (hh != null) hh.openTool(tool.id);
        });
        return row;
    }

    private static View separator(Context c) {
        View v = new View(c);
        v.setBackgroundColor(ContextCompat.getColor(c, R.color.ch_line));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, Math.max(1, Math.round(c.getResources().getDimension(R.dimen.ios_hairline))));
        lp.leftMargin = Ios.dp(c, 60);
        v.setLayoutParams(lp);
        return v;
    }

    private static View sectionHeader(Context c, String text) {
        TextView t = new TextView(c);
        t.setTextAppearance(R.style.TextAppearance_Ios_SectionHeader);
        t.setText(text);
        t.setPadding(Ios.dp(c, 16), Ios.dp(c, 22), Ios.dp(c, 16), Ios.dp(c, 7));
        return t;
    }

    private static View footer(Context c, String text) {
        TextView t = new TextView(c);
        t.setTextAppearance(R.style.TextAppearance_Ios_Footnote);
        t.setText(text);
        t.setPadding(Ios.dp(c, 16), Ios.dp(c, 7), Ios.dp(c, 16), 0);
        return t;
    }
}
