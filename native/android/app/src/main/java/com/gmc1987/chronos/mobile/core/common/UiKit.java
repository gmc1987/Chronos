package com.gmc1987.chronos.mobile.core.common;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

public final class UiKit {
    public static final int BRAND = Color.rgb(8, 123, 104);
    public static final int BRAND_DARK = Color.rgb(6, 61, 57);
    public static final int BACKGROUND = Color.rgb(244, 247, 248);
    public static final int TEXT = Color.rgb(23, 32, 42);
    public static final int MUTED = Color.rgb(113, 128, 138);
    public static final int ERROR = Color.rgb(190, 62, 46);

    private final Context context;

    public UiKit(Context context) {
        this.context = context;
    }

    public LinearLayout page(String title) {
        LinearLayout page = vertical();
        page.setPadding(dp(16), dp(22), dp(16), dp(24));
        page.addView(label(title, 24, BRAND_DARK, true));
        return page;
    }

    public LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(14), dp(16), dp(14));
        card.setBackgroundResource(android.R.drawable.dialog_holo_light_frame);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(5), 0, dp(5));
        card.setLayoutParams(params);
        card.setElevation(dp(1));
        return card;
    }

    public TextView label(String value, int size, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextSize(size);
        view.setTextColor(color);
        view.setLineSpacing(0, 1.15f);
        view.setPadding(dp(2), dp(5), dp(2), dp(5));
        if (bold) view.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        return view;
    }

    public EditText input(String hint, boolean password) {
        EditText input = new EditText(context);
        input.setHint(hint);
        input.setTextColor(TEXT);
        input.setHintTextColor(MUTED);
        input.setMinHeight(dp(52));
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        return input;
    }

    public Button primaryButton(String title) {
        Button button = button(title, Color.WHITE, BRAND, 15);
        button.setMinHeight(dp(48));
        return button;
    }

    public Button secondaryButton(String title) {
        Button button = button(title, BRAND, Color.rgb(235, 247, 244), 13);
        button.setMinHeight(dp(44));
        return button;
    }

    public View empty(String text) {
        TextView view = label(text, 14, MUTED, false);
        view.setGravity(Gravity.CENTER);
        view.setMinHeight(dp(88));
        return view;
    }

    public View error(String text, Runnable retry) {
        LinearLayout state = vertical();
        state.addView(label(text, 14, ERROR, false));
        Button button = secondaryButton("重试");
        button.setOnClickListener(view -> retry.run());
        state.addView(button);
        return state;
    }

    public LinearLayout centered(String text, boolean loading) {
        LinearLayout state = vertical();
        state.setGravity(Gravity.CENTER);
        state.setPadding(dp(24), dp(48), dp(24), dp(48));
        if (loading) state.addView(new ProgressBar(context));
        TextView value = label(text, 15, loading ? MUTED : ERROR, false);
        value.setGravity(Gravity.CENTER);
        state.addView(value);
        return state;
    }

    public ScrollView scroll(View child) {
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(true);
        scroll.setBackgroundColor(BACKGROUND);
        scroll.addView(child);
        return scroll;
    }

    public LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    public LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    public Space space(int height) {
        Space space = new Space(context);
        space.setLayoutParams(new LinearLayout.LayoutParams(1, dp(height)));
        return space;
    }

    public int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }

    private Button button(String title, int textColor, int background, int size) {
        Button button = new Button(context);
        button.setText(title);
        button.setTextColor(textColor);
        button.setTextSize(size);
        button.setAllCaps(false);
        button.setBackgroundColor(background);
        return button;
    }
}
