package com.zalexdev.stryker.ui.ios;

import android.content.Context;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.zalexdev.stryker.R;

/** The 34sp bold title at the top of an iOS tab root. */
public final class LargeTitle {

    private LargeTitle() {}

    @NonNull
    public static TextView create(@NonNull Context c) {
        TextView t = new TextView(c);
        t.setId(R.id.ios_large_title);
        t.setTextAppearance(R.style.TextAppearance_Ios_LargeTitle);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        t.setPadding(0, 0, 0, 0);
        t.setMinHeight(Ios.dp(c, 52));
        t.setGravity(android.view.Gravity.CENTER_VERTICAL);
        return t;
    }
}
