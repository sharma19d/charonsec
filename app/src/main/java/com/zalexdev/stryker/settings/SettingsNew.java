package com.zalexdev.stryker.settings;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;

import com.zalexdev.stryker.R;

public class SettingsNew extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.settings_host, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        if (getChildFragmentManager().findFragmentById(R.id.settings_nav_host) == null) {
            getChildFragmentManager().beginTransaction()
                    .replace(R.id.settings_nav_host, new SettingsHomeFragment())
                    .commit();
        }
    }

    public void openChild(Fragment fragment, String tag) {
        drawPopOnTop(false);
        getChildFragmentManager().beginTransaction()
                .setCustomAnimations(R.anim.ios_push_enter, R.anim.ios_push_exit,
                        R.anim.ios_pop_enter, R.anim.ios_pop_exit)
                .replace(R.id.settings_nav_host, fragment, tag)
                .addToBackStack(tag)
                .commit();
    }

    public boolean popChild() {
        if (getChildFragmentManager().getBackStackEntryCount() > 0) {
            drawPopOnTop(true);
            getChildFragmentManager().popBackStack();
            return true;
        }
        return false;
    }

    /** A pushed page slides over its parent; a popped page slides away on top of it. */
    private void drawPopOnTop(boolean pop) {
        View v = getView();
        if (v instanceof androidx.fragment.app.FragmentContainerView) {
            ((androidx.fragment.app.FragmentContainerView) v).setDrawDisappearingViewsLast(pop);
        }
    }
}
