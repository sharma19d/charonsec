package com.zalexdev.stryker.ui;

import android.content.Context;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.AppCompatButton;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.theme.MaterialComponentsViewInflater;

/** Registered as the theme's viewInflaterClass: adds the grid face to every inflated button. */
public class CharonViewInflater extends MaterialComponentsViewInflater {

    @NonNull
    @Override
    protected AppCompatButton createButton(@NonNull Context context, @NonNull AttributeSet attrs) {
        AppCompatButton b = super.createButton(context, attrs);
        GridOverlayDrawable.apply(b);
        return b;
    }

    @Nullable
    @Override
    protected View createView(Context context, String name, AttributeSet attrs) {
        View v;
        switch (name) {
            case "com.google.android.material.button.MaterialButton":
                v = new MaterialButton(context, attrs);
                break;
            case "com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton":
                v = new ExtendedFloatingActionButton(context, attrs);
                break;
            case "com.google.android.material.floatingactionbutton.FloatingActionButton":
                v = new FloatingActionButton(context, attrs);
                break;
            default:
                return null;
        }
        GridOverlayDrawable.apply(v);
        return v;
    }
}
