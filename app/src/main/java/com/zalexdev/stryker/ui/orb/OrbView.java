package com.zalexdev.stryker.ui.orb;

import android.animation.TimeAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

/**
 * Living orb: a dark glass sphere with translucent colour ribbons that flow,
 * overlap additively and swell with a simulated "energy" level.
 */
public class OrbView extends View {

    private static final int POINTS = 72;

    /** Ribbon colours: lavender, violet, steel blue, orchid. */
    private static final int[] COLORS = {0xFFA99FD8, 0xFF6E5CD6, 0xFF5E9CCB, 0xFFC98BC4};

    private final Paint blobPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint spherePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint haloPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint layerPaint = new Paint();
    private final Path path = new Path();
    private final Path clip = new Path();
    private final RadialGradient[] blobShaders = new RadialGradient[COLORS.length];
    private final android.graphics.Matrix matrix = new android.graphics.Matrix();
    private final float dp;

    private float cx, cy, radius;
    private float t;            // seconds
    private float level = 0.4f; // smoothed energy 0..1
    @Nullable private TimeAnimator ticker;

    public OrbView(Context context) { this(context, null); }

    public OrbView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        dp = getResources().getDisplayMetrics().density;
        blobPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1.2f * dp);
        rimPaint.setColor(0x55C9C1E8);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        cx = w / 2f;
        cy = h / 2f;
        radius = Math.min(w, h) * 0.32f;
        spherePaint.setShader(new RadialGradient(cx, cy - radius * 0.25f, radius * 1.15f,
                new int[]{0xFF221F2D, 0xFF121118, 0xFF0B0A0F}, new float[]{0f, 0.7f, 1f},
                Shader.TileMode.CLAMP));
        haloPaint.setShader(new RadialGradient(cx, cy, radius * 1.55f,
                new int[]{0x669E94C6, 0x229E94C6, 0x009E94C6}, new float[]{0.55f, 0.75f, 1f},
                Shader.TileMode.CLAMP));
        specPaint.setShader(new RadialGradient(cx - radius * 0.35f, cy - radius * 0.45f, radius * 0.55f,
                new int[]{0x30FFFFFF, 0x00FFFFFF}, null, Shader.TileMode.CLAMP));
        for (int i = 0; i < COLORS.length; i++) {
            int c = COLORS[i] & 0x00FFFFFF;
            // Unit-radius gradient, positioned per frame with a matrix.
            blobShaders[i] = new RadialGradient(0, 0, 1f,
                    new int[]{0x9A000000 | c, 0x50000000 | c, c}, new float[]{0f, 0.6f, 1f},
                    Shader.TileMode.CLAMP);
        }
        clip.reset();
        clip.addCircle(cx, cy, radius, Path.Direction.CW);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        start();
    }

    @Override
    protected void onDetachedFromWindow() {
        stop();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) start();
        else stop();
    }

    private void start() {
        if (ticker != null) return;
        ticker = new TimeAnimator();
        ticker.setTimeListener((a, total, delta) -> {
            float dt = Math.min(delta, 50) / 1000f;
            t += dt;
            // Speech-like energy: a few incommensurate waves, smoothed.
            float target = 0.45f
                    + 0.25f * (float) Math.sin(t * 1.7)
                    + 0.18f * (float) Math.sin(t * 4.3 + 1.1)
                    + 0.12f * (float) Math.sin(t * 7.9 + 2.3);
            target = Math.max(0.1f, Math.min(1f, target));
            level += (target - level) * Math.min(1f, dt * 6f);
            invalidate();
        });
        ticker.start();
    }

    private void stop() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    @Override
    protected void onDraw(@NonNull Canvas c) {
        if (radius <= 0) return;

        float breathe = 1f + 0.05f * (float) Math.sin(t * 1.3) + 0.06f * level;
        c.save();
        c.scale(breathe, breathe, cx, cy);
        c.drawCircle(cx, cy, radius * 1.55f, haloPaint);
        c.restore();

        c.drawCircle(cx, cy, radius, spherePaint);

        int layer = c.saveLayer(cx - radius, cy - radius, cx + radius, cy + radius, layerPaint);
        c.clipPath(clip);
        for (int i = 0; i < COLORS.length; i++) drawRibbon(c, i);
        c.restoreToCount(layer);

        c.drawCircle(cx, cy, radius, specPaint);
        rimPaint.setAlpha((int) (70 + 60 * level));
        c.drawCircle(cx, cy, radius, rimPaint);
    }

    /** One wobbling blob, orbiting the centre, filled with a soft radial gradient. */
    private void drawRibbon(Canvas c, int i) {
        float phase = i * 1.618f;
        float speed = 0.35f + 0.12f * i;
        float orbit = radius * (0.18f + 0.10f * level) * (1f + 0.4f * (float) Math.sin(t * 0.7 + phase));
        float ang = t * speed * (i % 2 == 0 ? 1f : -1f) + phase * 2f;
        float bx = cx + orbit * (float) Math.cos(ang);
        float by = cy + orbit * (float) Math.sin(ang) * 0.8f;
        float br = radius * (0.62f + 0.16f * level);

        path.reset();
        for (int k = 0; k <= POINTS; k++) {
            double th = 2 * Math.PI * k / POINTS;
            float wob = 1f
                    + (0.10f + 0.10f * level) * (float) Math.sin(2 * th + t * (1.1 + 0.3 * i) + phase)
                    + 0.07f * (float) Math.sin(3 * th - t * (1.7 + 0.2 * i) + phase * 2)
                    + 0.04f * (float) Math.sin(5 * th + t * 2.3 + phase * 3);
            float r = br * wob;
            float x = bx + r * (float) Math.cos(th);
            float y = by + r * (float) Math.sin(th);
            if (k == 0) path.moveTo(x, y);
            else path.lineTo(x, y);
        }
        path.close();

        matrix.setScale(br * 1.25f, br * 1.25f);
        matrix.postTranslate(bx, by);
        blobShaders[i].setLocalMatrix(matrix);
        blobPaint.setShader(blobShaders[i]);
        c.drawPath(path, blobPaint);
    }
}
