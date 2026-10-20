package com.gmc1987.chronos.mobile.core.common;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
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
    public static int BACKGROUND = Color.rgb(244, 247, 248);
    public static int SURFACE = Color.WHITE;
    public static int TEXT = Color.rgb(23, 32, 42);
    public static int MUTED = Color.rgb(113, 128, 138);
    public static final int ERROR = Color.rgb(190, 62, 46);
    public static int BORDER = Color.rgb(225, 231, 235);
    public static int FIELD = Color.rgb(248, 250, 251);
    public static int BRAND_SOFT = Color.rgb(235, 247, 244);

    private final Context context;

    public UiKit(Context context) {
        this.context = context;
        boolean dark = (context.getResources().getConfiguration().uiMode
                & android.content.res.Configuration.UI_MODE_NIGHT_MASK)
                == android.content.res.Configuration.UI_MODE_NIGHT_YES;
        if (dark) {
            BACKGROUND = Color.rgb(16, 23, 27);
            SURFACE = Color.rgb(28, 38, 43);
            TEXT = Color.rgb(235, 241, 243);
            MUTED = Color.rgb(166, 180, 187);
            BORDER = Color.rgb(57, 72, 78);
            FIELD = Color.rgb(35, 47, 52);
            BRAND_SOFT = Color.rgb(25, 67, 60);
        }
    }

    public LinearLayout page(String title) {
        LinearLayout page = vertical();
        page.setPadding(dp(20), dp(22), dp(20), dp(28));
        TextView heading = label(title, 26, TEXT, true);
        heading.setPadding(dp(2), dp(4), dp(2), dp(14));
        page.addView(heading);
        return page;
    }

    public LinearLayout card() {
        LinearLayout card = vertical();
        card.setPadding(dp(16), dp(16), dp(16), dp(16));
        card.setBackground(surface(18, SURFACE, Color.argb(32, 0, 0, 0), 1));
        int availableWidth = context.getResources().getDisplayMetrics().widthPixels - dp(40);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                Math.min(availableWidth, dp(760)), -2);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.setMargins(0, dp(7), 0, dp(7));
        card.setLayoutParams(params);
        card.setElevation(dp(4));
        card.setClipToOutline(true);
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
        input.setTextSize(16);
        input.setSingleLine(true);
        input.setMinHeight(dp(54));
        input.setPadding(dp(16), 0, dp(16), 0);
        StateListDrawable background = new StateListDrawable();
        background.addState(
                new int[]{android.R.attr.state_focused},
                surface(13, FIELD, BRAND, 2));
        background.addState(new int[]{}, surface(13, FIELD, BORDER, 1));
        input.setBackground(background);
        if (password) {
            input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        }
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(54));
        params.setMargins(0, dp(6), 0, dp(6));
        input.setLayoutParams(params);
        return input;
    }

    /** Creates an actually expandable text area for reasons, remarks and form descriptions. */
    public EditText multilineInput(String hint, int minimumLines) {
        EditText input = input(hint, false);
        input.setSingleLine(false);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(Math.max(3, minimumLines));
        input.setMaxLines(Math.max(6, minimumLines));
        input.setPadding(dp(16), dp(14), dp(16), dp(14));
        input.setInputType(InputType.TYPE_CLASS_TEXT
                | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.setMargins(0, dp(6), 0, dp(6));
        input.setLayoutParams(params);
        return input;
    }

    public Button primaryButton(String title) {
        Button button = button(title, Color.WHITE, BRAND, 15);
        button.setMinHeight(dp(54));
        button.setElevation(dp(4));
        return button;
    }

    public Button secondaryButton(String title) {
        Button button = button(title, BRAND, Color.rgb(235, 247, 244), 13);
        button.setMinHeight(dp(42));
        return button;
    }

    /** Compact text action used in a native navigation bar, matching the iOS back affordance. */
    public Button navigationButton(String title) {
        Button button = button(title, BRAND, Color.TRANSPARENT, 15);
        button.setTypeface(Typeface.DEFAULT, Typeface.NORMAL);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setMinHeight(dp(44));
        button.setPadding(dp(4), 0, dp(8), 0);
        button.setElevation(0);
        return button;
    }

    public Button destructiveButton(String title) {
        Button button = button(title, ERROR, Color.rgb(253, 241, 239), 15);
        button.setMinHeight(dp(52));
        return button;
    }

    public Button tabButton(String title, boolean selected) {
        return tabButton(title, 0, selected);
    }

    public Button tabButton(String title, int iconResource, boolean selected) {
        Button button = button(
                title,
                selected ? BRAND : MUTED,
                selected ? BRAND_SOFT : Color.TRANSPARENT,
                11);
        button.setTypeface(Typeface.DEFAULT, selected ? Typeface.BOLD : Typeface.NORMAL);
        button.setMinHeight(dp(56));
        button.setPadding(dp(6), dp(5), dp(6), dp(4));
        button.setCompoundDrawablePadding(dp(2));
        if (iconResource != 0) {
            Drawable icon = context.getDrawable(iconResource);
            if (icon != null) {
                icon = icon.mutate();
                icon.setTint(selected ? BRAND : MUTED);
                icon.setBounds(0, 0, dp(22), dp(22));
                button.setCompoundDrawables(null, icon, null, null);
            }
        }
        // Android's default Button animator raises selected/pressed controls even when elevation is 0.
        // Tab items must remain completely flat in every interaction state.
        button.setStateListAnimator(null);
        button.setElevation(0);
        button.setTranslationZ(0);
        button.setOutlineProvider(null);
        return button;
    }

    public TextView badge(String text, int size) {
        TextView badge = label(text, Math.max(18, size / 2), Color.WHITE, true);
        badge.setGravity(Gravity.CENTER);
        badge.setPadding(0, 0, 0, 0);
        badge.setBackground(surface(Math.max(11, size / 4), BRAND, 0, 0));
        badge.setElevation(dp(3));
        badge.setLayoutParams(new LinearLayout.LayoutParams(dp(size), dp(size)));
        return badge;
    }

    public View divider() {
        View divider = new View(context);
        divider.setBackgroundColor(Color.rgb(238, 242, 244));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(1));
        params.setMargins(0, dp(8), 0, dp(8));
        divider.setLayoutParams(params);
        return divider;
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

    public GradientDrawable surface(int radius, int fill, int strokeColor, int strokeWidth) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(fill);
        drawable.setCornerRadius(dp(radius));
        if (strokeWidth > 0) drawable.setStroke(dp(strokeWidth), strokeColor);
        return drawable;
    }

    private Button button(String title, int textColor, int background, int size) {
        Button button = new Button(context);
        button.setText(title);
        button.setTextColor(textColor);
        button.setTextSize(size);
        button.setAllCaps(false);
        button.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        button.setGravity(Gravity.CENTER);
        button.setPadding(dp(14), 0, dp(14), 0);
        GradientDrawable content = surface(13, background, 0, 0);
        button.setBackground(new RippleDrawable(
                ColorStateList.valueOf(Color.argb(30, 8, 123, 104)), content, null));
        return button;
    }
}
