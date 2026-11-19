package com.zalexdev.stryker.ui.glass;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RecordingCanvas;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.util.AttributeSet;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.webkit.WebView;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.content.ContextCompat;

import com.zalexdev.stryker.R;

/**
 * Frosted bar over the bottom of the content.
 *
 * <p>On Android 10+ the content behind the bar is re-recorded each frame into
 * a RenderNode (the same hardware path the window itself uses, so view state
 * is untouched), rendered into a 1/{@link Blur#SCALE} size GPU layer and drawn
 * back upscaled with four offset taps, which gives a soft blur. Older versions
 * get a denser solid tint instead.
 */
public class GlassBarLayout extends LinearLayout {

    @Nullable private View source;
    @Nullable private Object blur;          // Blur on API 29+, kept as Object so older VMs never load it
    private boolean hasFrame;

    private final Paint tintPaint = new Paint();
    private final Paint solidPaint = new Paint();
    private final Paint edgePaint = new Paint();
    private final Paint sheenPaint = new Paint();
    private final int base;
    private final float dp;

    private final ViewTreeObserver.OnPreDrawListener preDraw = () -> {
        capture();
        return true;
    };

    public GlassBarLayout(Context context) { this(context, null); }

    public GlassBarLayout(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        base = ContextCompat.getColor(context, R.color.ch_ink_0);
        tintPaint.setColor(ContextCompat.getColor(context, R.color.ch_glass));
        int ink1 = ContextCompat.getColor(context, R.color.ch_ink_1);
        solidPaint.setColor((ink1 & 0x00FFFFFF) | 0xF0000000);
        edgePaint.setColor(ContextCompat.getColor(context, R.color.ch_line_strong));
        edgePaint.setStrokeWidth(1f);
        setWillNotDraw(false);
        // Blurred content behind the bar must not receive taps.
        setClickable(true);
    }

    public void setSource(@Nullable View content) {
        source = content;
        hasFrame = false;
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        sheenPaint.setShader(new LinearGradient(0, 0, 0, 28f * dp,
                0x14E7E4EF, 0x00E7E4EF, Shader.TileMode.CLAMP));
        if (blur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ((Blur) blur).resize(w, h);
        hasFrame = false;
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && isHardwareAccelerated() && blur == null) {
            Blur b = new Blur();
            if (getWidth() > 0) b.resize(getWidth(), getHeight());
            blur = b;
        }
        getViewTreeObserver().addOnPreDrawListener(preDraw);
    }

    @Override
    protected void onDetachedFromWindow() {
        getViewTreeObserver().removeOnPreDrawListener(preDraw);
        if (blur != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ((Blur) blur).release();
        blur = null;
        hasFrame = false;
        super.onDetachedFromWindow();
    }

    /** Runs before each frame; invalidating here joins the current frame instead of scheduling another. */
    private void capture() {
        View s = source;
        if (blur == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return;
        int w = getWidth(), h = getHeight();
        if (s == null || w == 0 || h == 0 || getVisibility() != VISIBLE || !s.isAttachedToWindow()
                || hasVisibleWebView(s)) {
            if (hasFrame) {
                hasFrame = false;
                invalidate();
            }
            return;
        }
        float dx = getLeft() - s.getLeft();
        float dy = getTop() - s.getTop();
        if (getParent() != s.getParent()) {
            int[] a = new int[2], b = new int[2];
            getLocationInWindow(a);
            s.getLocationInWindow(b);
            dx = a[0] - b[0];
            dy = a[1] - b[1];
        }
        try {
            ((Blur) blur).record(s, dx, dy);
            hasFrame = true;
        } catch (RuntimeException e) {
            ((Blur) blur).release();
            blur = null;
            hasFrame = false;
        }
        invalidate();
    }

    private static boolean hasVisibleWebView(View v) {
        if (v.getVisibility() != VISIBLE) return false;
        if (v instanceof WebView) return v.getWidth() > 0 && v.getHeight() > 0;
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                if (hasVisibleWebView(g.getChildAt(i))) return true;
            }
        }
        return false;
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int w = getWidth(), h = getHeight();
        if (hasFrame && blur != null && c.isHardwareAccelerated()
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            c.drawColor(base);
            ((Blur) blur).draw(c);
            c.drawRect(0, 0, w, h, tintPaint);
        } else {
            c.drawRect(0, 0, w, h, solidPaint);
        }
        c.drawRect(0, 0, w, 28f * dp, sheenPaint);
        c.drawLine(0, 0.5f, w, 0.5f, edgePaint);
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private static final class Blur {
        static final int SCALE = 10;

        private final RenderNode low = new RenderNode("glass-low");
        private final RenderNode[] taps = new RenderNode[4];
        private int bw, bh;

        Blur() {
            low.setUseCompositingLayer(true, new Paint(Paint.FILTER_BITMAP_FLAG));
            for (int i = 0; i < taps.length; i++) taps[i] = new RenderNode("glass-tap" + i);
        }

        void resize(int w, int h) {
            bw = Math.max(1, (w + SCALE - 1) / SCALE);
            bh = Math.max(1, (h + SCALE - 1) / SCALE);
            low.setPosition(0, 0, bw, bh);
            float o = SCALE * 0.5f;
            float[][] off = {{-o, -o}, {o, -o}, {-o, o}, {o, o}};
            for (int i = 0; i < taps.length; i++) {
                RenderNode t = taps[i];
                t.setPosition(0, 0, bw, bh);
                t.setPivotX(0);
                t.setPivotY(0);
                t.setScaleX(SCALE);
                t.setScaleY(SCALE);
                t.setTranslationX(off[i][0]);
                t.setTranslationY(off[i][1]);
                // Running average: each tap blends in at 1/(n+1), so all four weigh the same.
                t.setAlpha(1f / (i + 1));
                t.setHasOverlappingRendering(false);
                RecordingCanvas rc = t.beginRecording(bw, bh);
                try {
                    rc.drawRenderNode(low);
                } finally {
                    t.endRecording();
                }
            }
        }

        void record(View src, float dx, float dy) {
            RecordingCanvas rc = low.beginRecording(bw, bh);
            try {
                rc.scale(1f / SCALE, 1f / SCALE);
                rc.translate(-dx, -dy);
                rc.clipRect(dx, dy, dx + bw * SCALE, dy + bh * SCALE);
                src.draw(rc);
            } finally {
                low.endRecording();
            }
        }

        void draw(Canvas c) {
            for (RenderNode t : taps) c.drawRenderNode(t);
        }

        void release() {
            low.discardDisplayList();
            for (RenderNode t : taps) t.discardDisplayList();
        }
    }
}
