package com.gmc1987.chronos.mobile.feature.applications;

import android.app.AlertDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;

/** Common native page chrome shared by application screens. */
final class ApplicationScreenChrome {
    private final UiKit ui;
    private final ScreenHost host;

    ApplicationScreenChrome(UiKit ui, ScreenHost host) {
        this.ui = ui;
        this.host = host;
    }

    LinearLayout page(String title, Runnable back) {
        host.setBackAction(back);
        LinearLayout page = ui.page(title);
        Button button = ui.secondaryButton("‹ 返回");
        button.setOnClickListener(view -> {
            host.setBackAction(null);
            back.run();
        });
        page.addView(button, 0, new LinearLayout.LayoutParams(-1, ui.dp(44)));
        return page;
    }

    void loading(String message) {
        host.show(ui.centered(message, true));
    }

    void error(String title, Exception error, Runnable retry, Runnable back) {
        LinearLayout page = page(title, back);
        page.addView(ui.error(ApplicationJson.message(error), retry));
        host.show(ui.scroll(page));
    }

    void message(String title, String message, Runnable done) {
        LinearLayout page = page(title, done);
        page.addView(ui.empty(message));
        host.show(ui.scroll(page));
    }

    void dialog(String title, String message) {
        new AlertDialog.Builder(ui.card().getContext()).setTitle(title).setMessage(message)
                .setPositiveButton("知道了", null).show();
    }
}
