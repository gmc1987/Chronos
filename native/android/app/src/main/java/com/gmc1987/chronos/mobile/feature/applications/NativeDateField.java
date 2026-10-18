package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/** Native date and time picker used by staff request forms. */
final class NativeDateField {
    private final Activity activity;
    private final UiKit ui;
    private final Calendar value;
    private final boolean includeTime;
    private final Button button;

    NativeDateField(Activity activity, UiKit ui, String label, Calendar initial,
            boolean includeTime) {
        this.activity = activity;
        this.ui = ui;
        this.value = (Calendar) initial.clone();
        this.includeTime = includeTime;
        this.button = ui.secondaryButton("");
        refresh(label);
        button.setOnClickListener(view -> pickDate(label));
    }

    Button view() {
        return button;
    }

    String date() {
        return format("yyyy-MM-dd");
    }

    String dateTime() {
        return format("yyyy-MM-dd'T'HH:mm:ss");
    }

    long millis() {
        return value.getTimeInMillis();
    }

    private void pickDate(String label) {
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            value.set(year, month, day);
            if (includeTime) pickTime(label);
            else refresh(label);
        }, value.get(Calendar.YEAR), value.get(Calendar.MONTH),
                value.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void pickTime(String label) {
        new TimePickerDialog(activity, (picker, hour, minute) -> {
            value.set(Calendar.HOUR_OF_DAY, hour);
            value.set(Calendar.MINUTE, minute);
            value.set(Calendar.SECOND, 0);
            refresh(label);
        }, value.get(Calendar.HOUR_OF_DAY), value.get(Calendar.MINUTE), true).show();
    }

    private void refresh(String label) {
        button.setText(label + "：" + format(includeTime ? "yyyy-MM-dd HH:mm" : "yyyy-MM-dd"));
        button.setContentDescription(button.getText());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, ui.dp(48));
        params.setMargins(0, ui.dp(5), 0, ui.dp(5));
        button.setLayoutParams(params);
    }

    private String format(String pattern) {
        return new SimpleDateFormat(pattern, Locale.CHINA).format(value.getTime());
    }
}
