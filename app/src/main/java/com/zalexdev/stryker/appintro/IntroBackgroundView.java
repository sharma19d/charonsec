package com.zalexdev.stryker.appintro;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.zalexdev.stryker.R;

/** Onboarding backdrop: the plain iOS system background. */
public class IntroBackgroundView extends View {

    public IntroBackgroundView(Context context) { this(context, null); }

    public IntroBackgroundView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public IntroBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setBackgroundColor(ContextCompat.getColor(context, R.color.ch_system_bg));
    }

    public void setPageOffset(float position) { }

    public void setDarkTheme(Boolean dark) { }

    public void replayReveal() { }
}
