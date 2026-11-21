package com.zalexdev.stryker.ui.ios;

import android.app.Application;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;

import androidx.annotation.ColorRes;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

/** App-wide access to the iOS colour tokens, resolved for the current light/dark appearance. */
public final class Ios {

    private static Context app;

    private Ios() {}

    public static void init(@NonNull Application application) {
        app = application;
    }

    public static int color(@ColorRes int res) {
        return ContextCompat.getColor(app, res);
    }

    public static boolean isDark(@NonNull Context c) {
        return (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
    }

    public static int dp(@NonNull Context c, float dp) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, dp,
                c.getResources().getDisplayMetrics()));
    }
}
