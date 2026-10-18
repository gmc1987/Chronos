package com.gmc1987.chronos.mobile.core.common;

import android.view.View;

public interface ScreenHost {
    void show(View view);

    default void setBackAction(Runnable action) {
        // Hosts that own a navigation container can bind the Android system back button.
    }
}
