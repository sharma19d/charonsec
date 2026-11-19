package com.zalexdev.stryker.ui.dial;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ValueAnimator;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.core.graphics.drawable.DrawableCompat;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.ui.Feedback;

import java.util.ArrayList;
import java.util.List;

/**
 * Navigation dial: a large ring whose centre sits below the screen, so only its
 * top arc is visible. Sections sit on the ring like stations on a tuner; the
 * user turns it with a drag or fling and it clicks into detents. The section
 * under the fixed needle at 12 o'clock is the selected one.
 *
 * The ring is endless: item angles wrap at {@code count * spacing}, and the
 * wrap point is always below the visible arc, so sections leave at one end and
 * come back from the other.
 */
public class RotaryDialView extends View {

    public static final class Item {
        public final String label;
        public final Drawable icon;
        public boolean locked;

        public Item(@NonNull String label, @NonNull Drawable icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public interface Listener {
        /** The dial came to rest on {@code index} (also fired when re-tapping the current one). */
        void onSettled(int index, boolean changed);
    }

    private static final float SPACING_DEG = 30f;
    private static final int TICKS_PER_STEP = 6;
    private static final float FLING_TIME_S = 0.32f;
    private static final float MAX_FLING_DEG = SPACING_DEG * 6;

    private final List<Item> items = new ArrayList<>();
    private Drawable[] iconIdle = new Drawable[0];
    private Drawable[] iconActive = new Drawable[0];
    private String[] indexLabels = new String[0];
    @Nullable private Listener listener;

    // State
    private float rotation;          // degrees; item i is at angle i*SPACING - rotation
    private int selected;
    private int lastDetent;
    @Nullable private ValueAnimator anim;

    // Geometry (px), computed in onSizeChanged
    private float cx, cy, rimR, tickOuterR, iconR, visibleHalfDeg;
    private final float dp;

    // Paints (allocated once)
    private final Paint facePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rimPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint indexPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint lockPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path needlePath = new Path();

    private final int cBone, cBoneDim, cBoneFaint, cSignal, cLine, cLineStrong, cFace;

    // Touch
    private VelocityTracker velocity;
    private float lastTouchDeg;
    private float downX, downY;
    private boolean dragging;
    private final int touchSlop;

    public RotaryDialView(Context context) { this(context, null); }

    public RotaryDialView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        Resources r = getResources();
        dp = r.getDisplayMetrics().density;
        touchSlop = ViewConfiguration.get(context).getScaledTouchSlop();

        cBone = ContextCompat.getColor(context, R.color.ch_bone);
        cBoneDim = ContextCompat.getColor(context, R.color.ch_bone_dim);
        cBoneFaint = ContextCompat.getColor(context, R.color.ch_bone_faint);
        cSignal = ContextCompat.getColor(context, R.color.ch_signal);
        cLine = ContextCompat.getColor(context, R.color.ch_line);
        cLineStrong = ContextCompat.getColor(context, R.color.ch_line_strong);
        cFace = ContextCompat.getColor(context, R.color.ch_ink_1);

        // Translucent so the frosted bar behind the dial shows through.
        facePaint.setColor((cFace & 0x00FFFFFF) | 0x70000000);
        rimPaint.setStyle(Paint.Style.STROKE);
        rimPaint.setStrokeWidth(1f * dp);
        tickPaint.setStrokeWidth(1f * dp);
        tickPaint.setStrokeCap(Paint.Cap.BUTT);
        needlePaint.setColor(cSignal);
        needlePaint.setStrokeWidth(2f * dp);

        Typeface display = safeFont(context, R.font.barlow_condensed_semibold);
        Typeface mono = safeFont(context, R.font.plex_mono_regular);
        labelPaint.setTypeface(display);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(17f * r.getDisplayMetrics().scaledDensity);
        labelPaint.setLetterSpacing(0.14f);
        labelPaint.setColor(cBone);
        indexPaint.setTypeface(mono);
        indexPaint.setTextAlign(Paint.Align.CENTER);
        indexPaint.setTextSize(10f * r.getDisplayMetrics().scaledDensity);
        indexPaint.setColor(cBoneFaint);
        lockPaint.setColor(cBoneFaint);

        setClickable(true);
        setFocusable(true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    private static Typeface safeFont(Context c, int res) {
        try {
            Typeface t = ResourcesCompat.getFont(c, res);
            if (t != null) return t;
        } catch (Exception ignored) {
        }
        return Typeface.DEFAULT_BOLD;
    }

    // ---------------------------------------------------------------- API

    public void setListener(@Nullable Listener l) { listener = l; }

    public void setItems(@NonNull List<Item> list) {
        items.clear();
        items.addAll(list);
        int n = items.size();
        iconIdle = new Drawable[n];
        iconActive = new Drawable[n];
        indexLabels = new String[n];
        for (int i = 0; i < n; i++) {
            indexLabels[i] = String.format(java.util.Locale.ROOT, "%02d / %02d", i + 1, n);
            Drawable base = items.get(i).icon;
            iconIdle[i] = tinted(base, cBoneDim);
            iconActive[i] = tinted(base, cSignal);
        }
        selected = Math.min(selected, Math.max(0, n - 1));
        rotation = selected * SPACING_DEG;
        lastDetent = Math.round(rotation / SPACING_DEG);
        invalidate();
    }

    private static Drawable tinted(Drawable d, int color) {
        Drawable.ConstantState cs = d.getConstantState();
        Drawable copy = DrawableCompat.wrap(cs != null ? cs.newDrawable().mutate() : d.mutate());
        DrawableCompat.setTint(copy, color);
        return copy;
    }

    public void setLocked(int index, boolean locked) {
        if (index < 0 || index >= items.size()) return;
        if (items.get(index).locked != locked) {
            items.get(index).locked = locked;
            invalidate();
        }
    }

    public int getSelected() { return selected; }

    /** Turn to {@code index} by the shortest way round, without firing the listener. */
    public void setSelected(int index, boolean animate) {
        if (index < 0 || index >= items.size()) return;
        selected = index;
        float target = rotation + wrapDelta(index * SPACING_DEG - rotation, span());
        if (animate && isLaidOut()) {
            animateTo(target, 420, false);
        } else {
            cancelAnim();
            setRotationDeg(target, false);
        }
        updateDescription();
    }

    // ---------------------------------------------------------------- geometry

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        float needleSpace = 14f * dp;
        float capH = h - needleSpace;
        // Radius grows with width so the arc stays gentle on wide screens.
        rimR = Math.max(w * 0.80f, capH * 1.6f);
        cx = w / 2f;
        cy = needleSpace + rimR;
        tickOuterR = rimR - 4f * dp;
        iconR = rimR - 46f * dp;
        // Angle at which the icon ring meets the bottom edge.
        float cos = (cy - h) / iconR;
        visibleHalfDeg = (float) Math.toDegrees(Math.acos(Math.max(-1f, Math.min(1f, cos))));

        float glowR = 58f * dp;
        glowPaint.setShader(new RadialGradient(cx, cy - iconR, glowR,
                new int[]{(cSignal & 0x00FFFFFF) | 0x38000000, (cSignal & 0x00FFFFFF)},
                null, Shader.TileMode.CLAMP));

        needlePath.reset();
        float tipY = cy - rimR + 2f * dp;
        needlePath.moveTo(cx, tipY);
        needlePath.lineTo(cx - 6f * dp, tipY - 10f * dp);
        needlePath.lineTo(cx + 6f * dp, tipY - 10f * dp);
        needlePath.close();
    }

    private float span() { return Math.max(1, items.size()) * SPACING_DEG; }

    /** Angle of item i relative to the needle, wrapped into [-span/2, span/2). */
    private float itemAngle(int i) {
        float span = span();
        float a = i * SPACING_DEG - rotation;
        a = ((a + span / 2f) % span + span) % span - span / 2f;
        return a;
    }

    /** Shortest signed move for {@code d} on a ring that repeats every {@code period} degrees. */
    private static float wrapDelta(float d, float period) {
        d %= period;
        if (d > period / 2f) d -= period;
        if (d < -period / 2f) d += period;
        return d;
    }

    private static float shortestDelta(float from, float to) {
        float d = (to - from) % 360f;
        if (d > 180f) d -= 360f;
        if (d < -180f) d += 360f;
        return d;
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(@NonNull Canvas c) {
        int n = items.size();
        if (n == 0 || rimR <= 0) return;

        // Dial face and rim.
        c.drawCircle(cx, cy, rimR, facePaint);
        rimPaint.setColor(cLineStrong);
        c.drawCircle(cx, cy, rimR, rimPaint);
        rimPaint.setColor(cLine);
        c.drawCircle(cx, cy, tickOuterR - 16f * dp, rimPaint);

        // Tuning scale: ticks rotate with the dial; every TICKS_PER_STEP-th lines up with a section.
        float tickDeg = SPACING_DEG / TICKS_PER_STEP;
        float limit = visibleHalfDeg + 12f;
        int kStart = (int) Math.floor((rotation - limit) / tickDeg);
        int kEnd = (int) Math.ceil((rotation + limit) / tickDeg);
        for (int k = kStart; k <= kEnd; k++) {
            float a = k * tickDeg - rotation;
            float abs = Math.abs(a);
            boolean major = Math.floorMod(k, TICKS_PER_STEP) == 0;
            float len = (major ? 11f : 5f) * dp;
            float near = Math.max(0f, 1f - abs / (tickDeg * 1.5f));
            tickPaint.setColor(near > 0f ? blend(major ? cBoneDim : cBoneFaint, cSignal, near)
                    : (major ? cBoneDim : cBoneFaint));
            tickPaint.setAlpha((int) (255 * edgeFade(abs)));
            double rad = Math.toRadians(a);
            float sin = (float) Math.sin(rad), cos = (float) Math.cos(rad);
            c.drawLine(cx + tickOuterR * sin, cy - tickOuterR * cos,
                    cx + (tickOuterR - len) * sin, cy - (tickOuterR - len) * cos, tickPaint);
        }

        // Soft light pooled under the needle.
        c.drawCircle(cx, cy - iconR, 58f * dp, glowPaint);

        // Section icons.
        int iconPx = (int) (24f * dp);
        int nearest = nearestIndex();
        for (int i = 0; i < n; i++) {
            float a = itemAngle(i);
            float abs = Math.abs(a);
            if (abs > visibleHalfDeg + 8f) continue;
            double rad = Math.toRadians(a);
            float x = cx + iconR * (float) Math.sin(rad);
            float y = cy - iconR * (float) Math.cos(rad);
            float focus = Math.max(0f, 1f - abs / SPACING_DEG);        // 1 under the needle
            float scale = 1f + 0.28f * focus;
            float fade = edgeFade(abs);
            boolean locked = items.get(i).locked;
            int half = (int) (iconPx * scale / 2f);
            int alphaBase = (int) (255 * fade * (locked ? 0.38f : 1f));

            Drawable idle = iconIdle[i];
            idle.setBounds((int) x - half, (int) y - half, (int) x + half, (int) y + half);
            idle.setAlpha((int) (alphaBase * (1f - focus)));
            idle.draw(c);
            Drawable act = iconActive[i];
            act.setBounds((int) x - half, (int) y - half, (int) x + half, (int) y + half);
            act.setAlpha((int) (alphaBase * focus));
            act.draw(c);

            if (locked) {
                lockPaint.setAlpha((int) (200 * fade));
                c.drawCircle(x + half * 0.9f, y - half * 0.9f, 2.5f * dp, lockPaint);
            }
        }

        // Fixed needle at 12 o'clock.
        c.drawPath(needlePath, needlePaint);
        c.drawLine(cx, cy - rimR + 2f * dp, cx, cy - tickOuterR + 13f * dp, needlePaint);

        // Name of the section under the needle, in the hub.
        Item cur = items.get(nearest);
        float hubY = cy - iconR + 40f * dp;
        labelPaint.setColor(cur.locked ? cBoneFaint : cBone);
        c.drawText(cur.label, cx, hubY, labelPaint);
        c.drawText(indexLabels[nearest], cx, hubY + 15f * dp, indexPaint);
    }

    /** Icons and ticks fade out as they approach the ends of the visible arc. */
    private float edgeFade(float absDeg) {
        float start = visibleHalfDeg * 0.55f;
        if (absDeg <= start) return 1f;
        float t = (absDeg - start) / Math.max(1f, visibleHalfDeg - start);
        return Math.max(0f, 1f - t);
    }

    private static int blend(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return 0xFF000000
                | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8)
                | (int) (ab + (bb - ab) * t);
    }

    private int nearestIndex() {
        int n = items.size();
        if (n == 0) return 0;
        int d = Math.round(rotation / SPACING_DEG);
        return Math.floorMod(d, n);
    }

    // ---------------------------------------------------------------- motion

    private void setRotationDeg(float deg, boolean haptics) {
        rotation = deg;
        int detent = Math.round(rotation / SPACING_DEG);
        if (detent != lastDetent) {
            lastDetent = detent;
            if (haptics) Feedback.get(getContext()).tick();
        }
        invalidate();
    }

    private void cancelAnim() {
        if (anim != null) {
            anim.removeAllListeners();
            anim.cancel();
            anim = null;
        }
    }

    private void animateTo(float target, long durationMs, boolean settleAfter) {
        cancelAnim();
        ValueAnimator va = ValueAnimator.ofFloat(rotation, target);
        va.setDuration(durationMs);
        float dist = Math.abs(target - rotation);
        va.setInterpolator(dist <= SPACING_DEG * 1.2f
                ? new OvershootInterpolator(1.1f)
                : new DecelerateInterpolator(1.8f));
        va.addUpdateListener(a -> setRotationDeg((float) a.getAnimatedValue(), true));
        if (settleAfter) {
            va.addListener(new AnimatorListenerAdapter() {
                @Override
                public void onAnimationEnd(Animator animation) {
                    anim = null;
                    settle();
                }
            });
        }
        anim = va;
        va.start();
    }

    private void snapFrom(float velocityDegPerS) {
        float projected = rotation + Math.max(-MAX_FLING_DEG,
                Math.min(MAX_FLING_DEG, velocityDegPerS * FLING_TIME_S));
        float target = Math.round(projected / SPACING_DEG) * SPACING_DEG;
        float dist = Math.abs(target - rotation);
        long dur = (long) Math.max(240, Math.min(900, 240 + dist * 4.2f));
        animateTo(target, dur, true);
    }

    private void settle() {
        Feedback.get(getContext()).confirm();
        int idx = nearestIndex();
        boolean changed = idx != selected;
        selected = idx;
        updateDescription();
        if (changed) sendAccessibilityEvent(AccessibilityEvent.TYPE_VIEW_SELECTED);
        if (listener != null) listener.onSettled(idx, changed);
    }

    /** Step one section left (-1) or right (+1). */
    public void step(int dir) {
        float base = Math.round(rotation / SPACING_DEG) * SPACING_DEG;
        animateTo(base + dir * SPACING_DEG, 320, true);
    }

    // ---------------------------------------------------------------- touch

    private float touchAngle(float x, float y) {
        return (float) Math.toDegrees(Math.atan2(x - cx, cy - y));
    }

    private boolean onDial(float x, float y) {
        float dx = x - cx, dy = y - cy;
        return dx * dx + dy * dy <= rimR * rimR;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent e) {
        if (!isEnabled()) return false;
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!onDial(x, y)) return false;
                cancelAnim();
                getParent().requestDisallowInterceptTouchEvent(true);
                if (velocity == null) velocity = VelocityTracker.obtain();
                velocity.clear();
                velocity.addMovement(e);
                downX = x;
                downY = y;
                lastTouchDeg = touchAngle(x, y);
                dragging = false;
                return true;

            case MotionEvent.ACTION_MOVE: {
                if (velocity != null) velocity.addMovement(e);
                if (!dragging && Math.hypot(x - downX, y - downY) > touchSlop) {
                    dragging = true;
                    lastTouchDeg = touchAngle(x, y);
                }
                if (dragging) {
                    float deg = touchAngle(x, y);
                    float delta = shortestDelta(lastTouchDeg, deg);
                    lastTouchDeg = deg;
                    setRotationDeg(rotation - delta, true);
                }
                return true;
            }

            case MotionEvent.ACTION_UP:
                if (dragging) {
                    float w = 0f;
                    if (velocity != null) {
                        velocity.addMovement(e);
                        velocity.computeCurrentVelocity(1000);
                        float rx = x - cx, ry = y - cy;
                        float r2 = Math.max(1f, rx * rx + ry * ry);
                        // Angular velocity (clockwise positive on screen), rad/s -> deg/s.
                        w = (float) Math.toDegrees(
                                (rx * velocity.getYVelocity() - ry * velocity.getXVelocity()) / r2);
                    }
                    snapFrom(-w);
                } else {
                    onTap(x, y);
                }
                recycleVelocity();
                dragging = false;
                return true;

            case MotionEvent.ACTION_CANCEL:
                recycleVelocity();
                if (dragging) snapFrom(0f);
                dragging = false;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void onTap(float x, float y) {
        int n = items.size();
        float hit = 30f * dp;
        for (int i = 0; i < n; i++) {
            float a = itemAngle(i);
            if (Math.abs(a) > visibleHalfDeg) continue;
            double rad = Math.toRadians(a);
            float ix = cx + iconR * (float) Math.sin(rad);
            float iy = cy - iconR * (float) Math.cos(rad);
            if (Math.hypot(x - ix, y - iy) <= hit) {
                performClick();
                float target = rotation + a;
                if (Math.abs(a) < 0.5f) {
                    settle();
                } else {
                    animateTo(target, (long) Math.min(620, 260 + Math.abs(a) * 5f), true);
                }
                return;
            }
        }
        // Tap in the hub re-opens the current section.
        if (Math.abs(touchAngle(x, y)) < SPACING_DEG / 2f) {
            performClick();
            settle();
        }
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }

    private void recycleVelocity() {
        if (velocity != null) {
            velocity.recycle();
            velocity = null;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        cancelAnim();
        recycleVelocity();
        super.onDetachedFromWindow();
    }

    // ---------------------------------------------------------------- accessibility

    private void updateDescription() {
        if (items.isEmpty()) return;
        Item it = items.get(selected);
        setContentDescription("Navigation dial, " + it.label
                + (it.locked ? ", locked" : "") + ", " + (selected + 1) + " of " + items.size());
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setScrollable(true);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override
    public boolean performAccessibilityAction(int action, Bundle args) {
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD) { step(1); return true; }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) { step(-1); return true; }
        return super.performAccessibilityAction(action, args);
    }
}
