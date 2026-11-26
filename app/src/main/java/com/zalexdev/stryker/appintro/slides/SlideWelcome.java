package com.zalexdev.stryker.appintro.slides;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.PropertyValuesHolder;
import android.animation.ValueAnimator;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.widget.ViewPager2;

import com.zalexdev.stryker.R;
import com.zalexdev.stryker.appintro.IntroPage;

import java.util.ArrayList;
import java.util.List;

public class SlideWelcome extends Fragment implements IntroPage {

    private static final long ENTER_MS = 620L;
    private static final float RISE_DP = 26f;
    private static final long LOGO_IN_MS = 900L;
    private static final long FLOAT_MS = 2600L;
    private static final long PULSE_MS = 2400L;
    private static final float FLOAT_DP = 5f;

    private final List<Animator> loops = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.new_slide_welcome, container, false);

        float density = getResources().getDisplayMetrics().density;
        float rise = RISE_DP * density;
        animateLogo(view, density);
        enter(view.findViewById(R.id.welcome_eyebrow), 420L, rise);
        enter(view.findViewById(R.id.welcome_title), 520L, rise);
        enter(view.findViewById(R.id.welcome_subtitle), 620L, rise);

        return view;
    }

    @Override
    public CharSequence primaryLabel(Context context) {
        return context.getString(R.string.intro_action_start);
    }

    @Override
    public void onPrimary() {
        ViewPager2 pager = requireActivity().findViewById(R.id.view_pager);
        if (pager != null) pager.setCurrentItem(pager.getCurrentItem() + 1);
    }

    @Override
    public void onDestroyView() {
        for (Animator a : loops) a.cancel();
        loops.clear();
        super.onDestroyView();
    }

    /**
     * Logo pops in (scale + fade + slight turn), then floats gently while two
     * staggered rings ripple out from behind it.
     */
    private void animateLogo(View root, float density) {
        View logo = root.findViewById(R.id.welcome_logo);
        View ring1 = root.findViewById(R.id.welcome_logo_ring);
        View ring2 = root.findViewById(R.id.welcome_logo_ring2);
        if (logo == null) return;

        logo.setAlpha(0f);
        logo.setScaleX(0.55f);
        logo.setScaleY(0.55f);
        logo.setRotation(-14f);
        ObjectAnimator popIn = ObjectAnimator.ofPropertyValuesHolder(logo,
                PropertyValuesHolder.ofFloat(View.ALPHA, 0f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.55f, 1f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.55f, 1f),
                PropertyValuesHolder.ofFloat(View.ROTATION, -14f, 0f));
        popIn.setDuration(LOGO_IN_MS);
        popIn.setStartDelay(80L);
        popIn.setInterpolator(new OvershootInterpolator(1.4f));

        ObjectAnimator drift = ObjectAnimator.ofFloat(logo, View.TRANSLATION_Y,
                0f, -FLOAT_DP * density);
        drift.setDuration(FLOAT_MS);
        drift.setRepeatCount(ValueAnimator.INFINITE);
        drift.setRepeatMode(ValueAnimator.REVERSE);
        drift.setInterpolator(new AccelerateDecelerateInterpolator());

        AnimatorSet logoSet = new AnimatorSet();
        logoSet.playSequentially(popIn, drift);
        loops.add(logoSet);
        logoSet.start();

        startPulse(ring1, LOGO_IN_MS / 2);
        startPulse(ring2, LOGO_IN_MS / 2 + PULSE_MS / 2);
    }

    private void startPulse(View ring, long delay) {
        if (ring == null) return;
        ObjectAnimator pulse = ObjectAnimator.ofPropertyValuesHolder(ring,
                PropertyValuesHolder.ofFloat(View.SCALE_X, 0.92f, 1.45f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, 0.92f, 1.45f),
                PropertyValuesHolder.ofFloat(View.ALPHA, 0.55f, 0f));
        pulse.setDuration(PULSE_MS);
        pulse.setStartDelay(delay);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setInterpolator(new DecelerateInterpolator(1.6f));
        loops.add(pulse);
        pulse.start();
    }

    private void enter(View view, long delay, float fromY) {
        if (view == null) return;
        view.setAlpha(0f);
        view.setTranslationY(fromY);
        view.animate()
                .alpha(1f)
                .translationY(0f)
                .setStartDelay(delay)
                .setDuration(ENTER_MS)
                .setInterpolator(new DecelerateInterpolator(2f))
                .start();
    }
}
