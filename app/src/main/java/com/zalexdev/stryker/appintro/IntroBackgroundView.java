package com.zalexdev.stryker.appintro;

import android.animation.TimeAnimator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.zalexdev.stryker.R;

import java.util.Random;

/**
 * First-run backdrop: drifting pools of violet light, a faint grid that
 * scrolls slowly, a scan sweep, rising motes, and a large tuning scale on the
 * right that turns slowly and follows the page swipes.
 */
public class IntroBackgroundView extends View {

    private static final float TICK_DEG = 3f;
    private static final float DEG_PER_PAGE = 24f;
    private static final float IDLE_DEG_PER_S = 1.6f;
    private static final int MOTES = 26;

    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint ring = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gridPaint = new Paint();
    private final Paint basePaint = new Paint();
    private final Paint sweepPaint = new Paint();
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint motePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint[] poolPaint = new Paint[3];
    private final RadialGradient[] poolShader = new RadialGradient[3];
    private final Matrix matrix = new Matrix();
    @Nullable private LinearGradient sweepShader;
    private final int cMinor, cMajor, cSignal, cLine;
    private final float dp;

    private final float[] moteX = new float[MOTES], moteY = new float[MOTES];
    private final float[] moteSpeed = new float[MOTES], moteSize = new float[MOTES], motePhase = new float[MOTES];

    private float pageOffset;
    private float reveal = 1f;
    private float t;
    private float cx, cy, r;
    private float gridStep;
    @Nullable private ValueAnimator revealAnim;
    @Nullable private TimeAnimator ticker;

    public IntroBackgroundView(Context context) { this(context, null); }

    public IntroBackgroundView(Context context, AttributeSet attrs) { this(context, attrs, 0); }

    public IntroBackgroundView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        dp = getResources().getDisplayMetrics().density;
        cMinor = ContextCompat.getColor(context, R.color.ch_line_strong);
        cMajor = ContextCompat.getColor(context, R.color.ch_bone_dim);
        cSignal = ContextCompat.getColor(context, R.color.ch_signal);
        cLine = ContextCompat.getColor(context, R.color.ch_line_strong);
        ring.setStyle(Paint.Style.STROKE);
        ring.setStrokeWidth(1f * dp);
        ring.setColor(cLine);
        needle.setColor(cSignal);
        needle.setStrokeWidth(2.5f * dp);
        needle.setStrokeCap(Paint.Cap.ROUND);
        gridPaint.setStrokeWidth(1f);
        motePaint.setColor(0xFFC9C1E8);
        gridStep = 28f * dp;
        for (int i = 0; i < 3; i++) poolPaint[i] = new Paint(Paint.ANTI_ALIAS_FLAG);
        replayReveal();
    }

    public void setPageOffset(float position) {
        if (pageOffset != position) {
            pageOffset = position;
            invalidate();
        }
    }

    /** Kept for API compatibility: the intro is always dark. */
    public void setDarkTheme(Boolean dark) { }

    /** Everything fades and turns in once when the intro opens. */
    public void replayReveal() {
        if (revealAnim != null) revealAnim.cancel();
        revealAnim = ValueAnimator.ofFloat(0f, 1f);
        revealAnim.setDuration(1400);
        revealAnim.setStartDelay(100);
        revealAnim.setInterpolator(new DecelerateInterpolator(2f));
        revealAnim.addUpdateListener(a -> {
            reveal = (float) a.getAnimatedValue();
            invalidate();
        });
        reveal = 0f;
        revealAnim.start();
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        r = w * 0.92f;
        cx = w * 1.10f;
        cy = h * 0.26f;

        basePaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0xFF0F0E13, 0xFF131219, 0xFF1A1724}, new float[]{0f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        // Grid is invisible at the top and strongest near the bottom.
        gridPaint.setShader(new LinearGradient(0, 0, 0, h,
                new int[]{0x00E7E4EF, 0x0AE7E4EF, 0x1CE7E4EF}, new float[]{0.15f, 0.55f, 1f},
                Shader.TileMode.CLAMP));
        sweepShader = new LinearGradient(0, -1f, 0, 1f,
                new int[]{0x009E94C6, 0x169E94C6, 0x009E94C6}, null, Shader.TileMode.CLAMP);
        sweepPaint.setShader(sweepShader);
        glowPaint.setShader(new RadialGradient(cx - r, cy, 90f * dp,
                new int[]{(cSignal & 0x00FFFFFF) | 0x50000000, cSignal & 0x00FFFFFF},
                null, Shader.TileMode.CLAMP));

        int[] pool = {0x9E94C6, 0x5B4E9C, 0x4E6C92};
        int[] poolA = {0x3A, 0x48, 0x34};
        for (int i = 0; i < 3; i++) {
            poolShader[i] = new RadialGradient(0, 0, 1f,
                    new int[]{(poolA[i] << 24) | pool[i], ((poolA[i] / 3) << 24) | pool[i], pool[i]},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            poolPaint[i].setShader(poolShader[i]);
        }

        Random rnd = new Random(11);
        for (int i = 0; i < MOTES; i++) {
            moteX[i] = rnd.nextFloat() * w;
            moteY[i] = rnd.nextFloat() * h;
            moteSpeed[i] = (6f + rnd.nextFloat() * 14f) * dp;
            moteSize[i] = (0.8f + rnd.nextFloat() * 1.4f) * dp;
            motePhase[i] = rnd.nextFloat() * 6.28f;
        }
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        startTicker();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (revealAnim != null) revealAnim.cancel();
        stopTicker();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onVisibilityChanged(@NonNull View changed, int visibility) {
        super.onVisibilityChanged(changed, visibility);
        if (visibility == VISIBLE && isAttachedToWindow()) startTicker();
        else stopTicker();
    }

    private void startTicker() {
        if (ticker != null) return;
        ticker = new TimeAnimator();
        ticker.setTimeListener((a, total, delta) -> {
            float dt = Math.min(delta, 50) / 1000f;
            t += dt;
            int h = getHeight(), w = getWidth();
            for (int i = 0; i < MOTES; i++) {
                moteY[i] -= moteSpeed[i] * dt;
                if (moteY[i] < -10f * dp) {
                    moteY[i] = h + 10f * dp;
                    moteX[i] = (moteX[i] * 7.31f + 97f * dp) % Math.max(1, w);
                }
            }
            invalidate();
        });
        ticker.start();
    }

    private void stopTicker() {
        if (ticker != null) {
            ticker.cancel();
            ticker = null;
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        if (w == 0 || h == 0) return;
        c.drawRect(0, 0, w, h, basePaint);

        // Drifting pools of light (slow Lissajous paths).
        drawPool(c, 0, w * (0.25f + 0.12f * (float) Math.sin(t * 0.11)),
                h * (0.30f + 0.08f * (float) Math.sin(t * 0.07 + 1f)), w * 0.75f);
        drawPool(c, 1, w * (0.70f + 0.15f * (float) Math.sin(t * 0.09 + 2f)),
                h * (0.68f + 0.10f * (float) Math.cos(t * 0.06)), w * 0.95f);
        drawPool(c, 2, w * (0.15f + 0.10f * (float) Math.cos(t * 0.08 + 0.5f)),
                h * (0.88f + 0.05f * (float) Math.sin(t * 0.1)), w * 0.7f);

        // Faint grid, scrolling down slowly.
        gridPaint.setAlpha((int) (255 * reveal));
        float scroll = (t * 6f * dp) % gridStep;
        for (float x = (w % gridStep) / 2f; x < w; x += gridStep) c.drawLine(x, 0, x, h, gridPaint);
        for (float y = scroll - gridStep; y < h; y += gridStep) c.drawLine(0, y, w, y, gridPaint);

        // Scan sweep, top to bottom every 7 s.
        if (sweepShader == null) return;
        float band = 140f * dp;
        float sy = ((t % 7f) / 7f) * (h + band * 2) - band;
        matrix.setScale(1f, band);
        matrix.postTranslate(0, sy);
        sweepShader.setLocalMatrix(matrix);
        sweepPaint.setAlpha((int) (255 * reveal));
        c.drawRect(0, sy - band, w, sy + band, sweepPaint);

        // Rising motes that twinkle.
        for (int i = 0; i < MOTES; i++) {
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 1.6f + motePhase[i]);
            motePaint.setAlpha((int) (reveal * (40 + 110 * tw)));
            c.drawCircle(moteX[i], moteY[i], moteSize[i], motePaint);
        }

        drawScale(c);
    }

    private void drawPool(Canvas c, int i, float x, float y, float radius) {
        matrix.setScale(radius, radius);
        matrix.postTranslate(x, y);
        poolShader[i].setLocalMatrix(matrix);
        poolPaint[i].setAlpha((int) (255 * reveal));
        c.drawCircle(x, y, radius, poolPaint[i]);
    }

    private void drawScale(Canvas c) {
        if (r <= 0) return;
        float rotation = pageOffset * DEG_PER_PAGE + t * IDLE_DEG_PER_S + (1f - reveal) * 40f;
        int alpha = (int) (255 * reveal);

        ring.setAlpha((int) (alpha * 0.9f));
        c.drawCircle(cx, cy, r, ring);
        ring.setAlpha((int) (alpha * 0.55f));
        c.drawCircle(cx, cy, r - 26f * dp, ring);
        ring.setAlpha((int) (alpha * 0.3f));
        c.drawCircle(cx, cy, r - 56f * dp, ring);
        ring.setAlpha((int) (alpha * 0.16f));
        c.drawCircle(cx, cy, r * 0.45f, ring);

        for (float a = 80f; a <= 280f; a += TICK_DEG) {
            double rad = Math.toRadians(a + rotation);
            int k = Math.round(a / TICK_DEG);
            boolean major = k % 5 == 0;
            float len = (major ? 22f : 9f) * dp;
            tick.setColor(major ? cMajor : cMinor);
            tick.setAlpha((int) (alpha * (major ? 0.75f : 0.9f)));
            tick.setStrokeWidth((major ? 1.5f : 1f) * dp);
            float sin = (float) Math.sin(rad), cos = (float) Math.cos(rad);
            float r0 = r - 3f * dp;
            c.drawLine(cx + r0 * cos, cy + r0 * sin,
                    cx + (r0 - len) * cos, cy + (r0 - len) * sin, tick);
        }

        // Inner ring turns slower for a parallax feel.
        float innerR = r - 30f * dp;
        tick.setColor(cMinor);
        tick.setStrokeWidth(1f * dp);
        tick.setAlpha((int) (alpha * 0.6f));
        for (float a = 100f; a <= 260f; a += TICK_DEG * 2) {
            double rad = Math.toRadians(a + rotation * 0.6f);
            float sin = (float) Math.sin(rad), cos = (float) Math.cos(rad);
            float len = 5f * dp;
            c.drawLine(cx + innerR * cos, cy + innerR * sin,
                    cx + (innerR - len) * cos, cy + (innerR - len) * sin, tick);
        }

        float pulse = 0.75f + 0.25f * (float) Math.sin(t * 2.2f);
        glowPaint.setAlpha((int) (alpha * pulse));
        c.drawCircle(cx - r, cy, 90f * dp, glowPaint);
        needle.setAlpha(alpha);
        float nx = cx - r;
        c.drawLine(nx - 12f * dp, cy, nx + 26f * dp, cy, needle);
    }
}
