package com.gmc1987.chronos.mobile.feature.applications;

import android.app.AlertDialog;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
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
        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(20), ui.dp(10), ui.dp(20), ui.dp(28));

        LinearLayout navigation = ui.horizontal();
        Button button = ui.navigationButton("‹ 返回");
        button.setOnClickListener(view -> {
            host.setBackAction(null);
            back.run();
        });
        navigation.addView(button, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(44)));

        TextView heading = ui.label(title, 22, UiKit.TEXT, true);
        heading.setGravity(Gravity.CENTER);
        navigation.addView(heading, new LinearLayout.LayoutParams(0, ui.dp(44), 1));

        // Keep the page title visually centered while the back action stays at the upper-left.
        View trailingBalance = new View(button.getContext());
        navigation.addView(trailingBalance, new LinearLayout.LayoutParams(ui.dp(72), ui.dp(44)));
        page.addView(navigation, new LinearLayout.LayoutParams(-1, ui.dp(50)));
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
