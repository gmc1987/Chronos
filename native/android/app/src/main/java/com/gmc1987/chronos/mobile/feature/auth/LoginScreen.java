package com.gmc1987.chronos.mobile.feature.auth;

import android.content.res.ColorStateList;
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
        page.setPadding(ui.dp(24), ui.dp(32), ui.dp(24), ui.dp(24));
        page.setGravity(Gravity.CENTER_HORIZONTAL);
        page.addView(ui.space(24));
        page.addView(ui.badge("C", 68));
        TextView brand = ui.label("CHRONOS", 13, UiKit.BRAND, true);
        brand.setLetterSpacing(.22f);
        brand.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams brandParams = new LinearLayout.LayoutParams(-1, -2);
        brandParams.setMargins(0, ui.dp(14), 0, 0);
        brand.setLayoutParams(brandParams);
        page.addView(brand);
        TextView slogan = ui.label("连接校园每一项工作", 25, UiKit.TEXT, true);
        slogan.setGravity(Gravity.CENTER);
        page.addView(slogan);
        TextView description = ui.label("统一待办 · 智能办公 · 安全协同", 14, UiKit.MUTED, false);
        description.setGravity(Gravity.CENTER);
        page.addView(description);
        page.addView(ui.space(26));

        LinearLayout card = ui.card();
        int availableWidth = contentWidth(page);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                Math.min(availableWidth, ui.dp(520)), -2);
        cardParams.gravity = Gravity.CENTER_HORIZONTAL;
        cardParams.setMargins(0, ui.dp(7), 0, ui.dp(7));
        card.setLayoutParams(cardParams);
        card.setPadding(ui.dp(24), ui.dp(22), ui.dp(24), ui.dp(24));
        card.addView(ui.label("欢迎回来", 27, UiKit.TEXT, true));
        card.addView(ui.label("登录 Chronos 统一门户", 14, UiKit.MUTED, false));
        card.addView(ui.space(10));
        EditText username = ui.input("账号", false);
        EditText password = ui.input("密码", true);
        CheckBox remember = new CheckBox(username.getContext());
        remember.setText("保持登录");
        remember.setTextColor(UiKit.MUTED);
        remember.setButtonTintList(ColorStateList.valueOf(UiKit.BRAND));
        remember.setChecked(true);
        Button submit = ui.primaryButton("登录统一门户  →");
        TextView state = ui.label("", 14, UiKit.ERROR, false);
        card.addView(username);
        card.addView(password);
        card.addView(remember);
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(-1, ui.dp(54));
        submitParams.setMargins(0, ui.dp(8), 0, ui.dp(4));
        submit.setLayoutParams(submitParams);
        card.addView(submit);
        card.addView(state);
        page.addView(card);
        page.addView(ui.space(20));
        TextView secure = ui.label("✓  安全连接由 Chronos 统一身份认证提供", 12, UiKit.MUTED, false);
        secure.setGravity(Gravity.CENTER);
        page.addView(secure);
        TextView copyright = ui.label("© 2026 Chronos", 11, UiKit.MUTED, false);
        copyright.setGravity(Gravity.CENTER);
        page.addView(copyright);
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
                submit.setText("登录统一门户  →");
            });
        });
    }

    private int contentWidth(LinearLayout page) {
        return page.getResources().getDisplayMetrics().widthPixels - ui.dp(48);
    }
}
