package com.gmc1987.chronos.mobile.feature.auth;

import android.view.Gravity;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;

public final class LoginScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final AuthRepository repository;

    public LoginScreen(UiKit ui, AsyncRunner async, ScreenHost host, AuthRepository repository) {
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.repository = repository;
    }

    public void show(Runnable loggedIn) {
        LinearLayout page = ui.vertical();
        page.setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(24));
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(ui.space(72));
        page.addView(ui.label("CHRONOS", 28, UiKit.BRAND_DARK, true));
        page.addView(ui.label("统一教育门户", 18, UiKit.TEXT, true));
        page.addView(ui.label("连接学校服务，查看课程与协同事项", 14, UiKit.MUTED, false));
        page.addView(ui.space(28));
        LinearLayout card = ui.card();
        EditText username = ui.input("账号", false);
        EditText password = ui.input("密码", true);
        CheckBox remember = new CheckBox(username.getContext());
        remember.setText("保持登录");
        remember.setChecked(true);
        Button submit = ui.primaryButton("登录");
        TextView state = ui.label("", 14, UiKit.ERROR, false);
        card.addView(username);
        card.addView(password);
        card.addView(remember);
        card.addView(submit);
        card.addView(state);
        page.addView(card);
        host.show(ui.scroll(page));
        submit.setOnClickListener(view -> {
            String user = username.getText().toString().trim();
            String pass = password.getText().toString();
            if (user.isEmpty() || pass.isEmpty()) {
                state.setText("请输入账号和密码");
                return;
            }
            submit.setEnabled(false);
            submit.setText("正在登录…");
            state.setText("");
            async.run(() -> {
                repository.login(user, pass, remember.isChecked());
                return true;
            }, ignored -> loggedIn.run(), error -> {
                state.setText(ErrorMessages.display(error, "登录失败，请检查账号、密码和网络"));
                submit.setEnabled(true);
                submit.setText("登录");
            });
        });
    }
}
