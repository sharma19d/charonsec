package com.zalexdev.stryker.ui;

import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.graphics.ColorUtils;

import com.google.android.material.button.MaterialButton;

/** Faint measurement grid drawn over a button's face (as its foreground). */
public final class GridOverlayDrawable extends android.graphics.drawable.Drawable {

    private static final int LIGHT_LINES = 0x16FFFFFF;
    private static final int DARK_LINES = 0x17000000;
    private static final int OUTLINE_LINES = 0x12FFFFFF;

    private final View host;
    private final Paint paint = new Paint();
    private final float step;
    private final int fixedFill;
    private int alpha = 255;

    private GridOverlayDrawable(View host, int fixedFill) {
        this.host = host;
        this.fixedFill = fixedFill;
        float dp = host.getResources().getDisplayMetrics().density;
        step = 7f * dp;
        paint.setStrokeWidth(Math.max(1f, Math.round(dp * 0.6f)));
    }

    public static void apply(@NonNull View v) {
        if (v.getForeground() == null) v.setForeground(new GridOverlayDrawable(v, 0));
    }

    /** For button-like views without a background tint: {@code fill} is the face colour under the grid. */
    public static void apply(@NonNull View v, int fill) {
        if (v.getForeground() == null) v.setForeground(new GridOverlayDrawable(v, fill));
    }

    /** Line colour for the current fill, or 0 when there is no face to draw on. */
    private int lineColor() {
        ColorStateList tint = host.getBackgroundTintList();
        int fill = fixedFill != 0 ? fixedFill : tint != null ? tint.getColorForState(host.getDrawableState(), tint.getDefaultColor())
                : Color.TRANSPARENT;
        if (Color.alpha(fill) < 40) {
            if (host instanceof MaterialButton && ((MaterialButton) host).getStrokeWidth() > 0) {
                return OUTLINE_LINES;
            }
            return 0;
        }
        return ColorUtils.calculateLuminance(fill | 0xFF000000) > 0.28 ? DARK_LINES : LIGHT_LINES;
    }

    @Override
    public void draw(@NonNull Canvas c) {
        int color = lineColor();
        if (color == 0) return;
        Rect b = getBounds();
        int top = b.top, bottom = b.bottom;
        if (host instanceof MaterialButton) {
            top += ((MaterialButton) host).getInsetTop();
            bottom -= ((MaterialButton) host).getInsetBottom();
        }
        if (bottom - top < step || b.width() < step) return;
        paint.setColor(color);
        paint.setAlpha(Color.alpha(color) * alpha / 255);

        int save = c.save();
        c.clipRect(b.left, top, b.right, bottom);
        float ox = b.left + (b.width() % step) / 2f;
        float oy = top + ((bottom - top) % step) / 2f;
        for (float x = ox + step; x < b.right - 1; x += step) {
            float px = Math.round(x) + 0.5f;
            c.drawLine(px, top, px, bottom, paint);
        }
        for (float y = oy + step; y < bottom - 1; y += step) {
            float py = Math.round(y) + 0.5f;
            c.drawLine(b.left, py, b.right, py, paint);
        }
        c.restoreToCount(save);
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    protected boolean onStateChange(@NonNull int[] state) { return true; }

    @Override
    public void setAlpha(int a) {
        alpha = a;
        invalidateSelf();
    }

    @Override
    public void setColorFilter(@Nullable ColorFilter cf) { paint.setColorFilter(cf); }

    @Override
    public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
