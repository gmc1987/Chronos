package com.gmc1987.chronos.mobile.core.common;

import android.app.Activity;

public final class AsyncRunner {
    private final Activity activity;

    public AsyncRunner(Activity activity) {
        this.activity = activity;
    }

    public <T> void run(Work<T> work, Success<T> success, Failure failure) {
        new Thread(() -> {
            try {
                T result = work.get();
                activity.runOnUiThread(() -> success.accept(result));
            } catch (Exception error) {
                activity.runOnUiThread(() -> failure.accept(error));
            }
        }).start();
    }

    public interface Work<T> { T get() throws Exception; }
    public interface Success<T> { void accept(T value); }
    public interface Failure { void accept(Exception error); }
}
