package com.zalexdev.stryker.ui.ios;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;

import com.zalexdev.stryker.R;

/** iOS tab bar: glyph over a small label, tint for the selected tab, hairline on top. */
public class IosTabBar extends LinearLayout {

    public interface Listener {
        /** @param reselected the tab was already selected (iOS pops that tab to its root) */
        void onTab(int index, boolean reselected);
    }

    private final Paint line = new Paint();
    @Nullable private Listener listener;
    private int selected = -1;

    public IosTabBar(Context context) { this(context, null); }

    public IosTabBar(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
        setWillNotDraw(false);
        setBackgroundColor(ContextCompat.getColor(context, R.color.ch_glass));
        line.setColor(ContextCompat.getColor(context, R.color.ch_bar_line));
        line.setStrokeWidth(Math.max(1f, getResources().getDisplayMetrics().density * 0.4f));
        // Content must not be tappable through the bar.
        setClickable(true);
    }

    public void setListener(@Nullable Listener l) { listener = l; }

    public void setTabs(@NonNull IosNav.Tab[] tabs) {
        removeAllViews();
        Context c = getContext();
        ColorStateList tint = ContextCompat.getColorStateList(c, R.color.ios_tab_tint);
        int h = getResources().getDimensionPixelSize(R.dimen.ios_tab_bar_height);
        for (int i = 0; i < tabs.length; i++) {
            LinearLayout item = new LinearLayout(c);
            item.setOrientation(VERTICAL);
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(0, Ios.dp(c, 6), 0, 0);
            item.setContentDescription(tabs[i].label);

            ImageView icon = new ImageView(c);
            icon.setImageResource(tabs[i].icon);
            icon.setImageTintList(tint);
            icon.setDuplicateParentStateEnabled(true);
            item.addView(icon, new LayoutParams(Ios.dp(c, 26), Ios.dp(c, 26)));

            TextView label = new TextView(c);
            label.setText(tabs[i].label);
            label.setTextColor(tint);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10);
            label.setTypeface(ResourcesCompat.getFont(c, R.font.charon_body), android.graphics.Typeface.NORMAL);
            label.setLetterSpacing(0.01f);
            label.setIncludeFontPadding(false);
            label.setSingleLine(true);
            label.setDuplicateParentStateEnabled(true);
            LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ios.dp(c, 3);
            item.addView(label, lp);

            final int index = i;
            item.setOnClickListener(v -> {
                boolean again = index == selected;
                setSelected(index);
                if (listener != null) listener.onTab(index, again);
            });
            addView(item, new LayoutParams(0, h, 1f));
        }
        if (selected >= 0) setSelected(selected);
    }

    public int getSelected() { return selected; }

    public void setSelected(int index) {
        selected = index;
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setSelected(i == index);
    }

    @Override
    public void setEnabled(boolean enabled) {
        super.setEnabled(enabled);
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.setEnabled(enabled);
            v.setAlpha(enabled || i == selected ? 1f : 0.45f);
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        super.onDraw(c);
        c.drawLine(0, 0, getWidth(), 0, line);
    }
}
