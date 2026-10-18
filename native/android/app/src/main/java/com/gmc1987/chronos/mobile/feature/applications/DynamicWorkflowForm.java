package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds a native Android form from the workflow field schema. */
final class DynamicWorkflowForm {
    private final LinearLayout view;
    private final Map<String, FieldReader> readers = new LinkedHashMap<>();

    DynamicWorkflowForm(Activity activity, UiKit ui, JSONArray fields, JSONObject initial) {
        view = ui.vertical();
        if (fields == null) return;
        for (int i = 0; i < fields.length(); i++) {
            JSONObject field = fields.optJSONObject(i);
            if (field == null) continue;
            String key = field.optString("fieldKey");
            if (key.isEmpty()) continue;
            String title = field.optString("fieldLabel", key)
                    + (field.optBoolean("required") ? " *" : "");
            String type = field.optString("fieldType", "TEXT").toUpperCase(Locale.ROOT);
            boolean readOnly = "READ".equalsIgnoreCase(field.optString("permission"));
            Object old = initial == null ? null : initial.opt(key);
            view.addView(ui.label(title, 13, UiKit.MUTED, false));
            if ("BOOLEAN".equals(type)) {
                Switch control = new Switch(activity);
                control.setText(title); control.setChecked(asBoolean(old)); control.setEnabled(!readOnly);
                view.addView(control); readers.put(key, new FieldReader(field, control::isChecked));
            } else if ("SELECT".equals(type) || "RADIO".equals(type)) {
                List<Option> options = options(field);
                List<String> labels = new ArrayList<>(); labels.add("请选择");
                for (Option option : options) labels.add(option.label);
                Spinner spinner = new Spinner(activity);
                spinner.setAdapter(new ArrayAdapter<>(activity,
                        android.R.layout.simple_spinner_dropdown_item, labels));
                spinner.setEnabled(!readOnly);
                String current = old == null ? "" : String.valueOf(old);
                for (int n = 0; n < options.size(); n++) {
                    if (options.get(n).value.equals(current)) spinner.setSelection(n + 1);
                }
                view.addView(spinner);
                readers.put(key, new FieldReader(field, () -> spinner.getSelectedItemPosition() == 0
                        ? "" : options.get(spinner.getSelectedItemPosition() - 1).value));
            } else if ("CHECKBOX".equals(type)) {
                LinearLayout group = ui.vertical();
                List<CheckBox> checks = new ArrayList<>();
                JSONArray selected = old instanceof JSONArray ? (JSONArray) old : new JSONArray();
                for (Option option : options(field)) {
                    CheckBox check = new CheckBox(activity);
                    check.setText(option.label); check.setTag(option.value);
                    check.setChecked(contains(selected, option.value)); check.setEnabled(!readOnly);
                    group.addView(check); checks.add(check);
                }
                view.addView(group);
                readers.put(key, new FieldReader(field, () -> {
                    JSONArray result = new JSONArray();
                    for (CheckBox check : checks) {
                        if (check.isChecked()) result.put(String.valueOf(check.getTag()));
                    }
                    return result;
                }));
            } else if ("FILE".equals(type)) {
                JSONArray attachments = old instanceof JSONArray ? (JSONArray) old : new JSONArray();
                view.addView(ui.label(attachments.length() == 0
                        ? "暂无附件" : attachments.length() + " 个附件", 14, UiKit.TEXT, false));
                readers.put(key, new FieldReader(field, () -> attachments));
            } else {
                EditText input = ui.input(title, false);
                input.setText(old == null || old == JSONObject.NULL ? "" : String.valueOf(old));
                input.setEnabled(!readOnly);
                if ("TEXTAREA".equals(type)) { input.setSingleLine(false); input.setMinLines(4); }
                if ("NUMBER".equals(type)) input.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                view.addView(input);
                readers.put(key, new FieldReader(field, () -> input.getText().toString().trim()));
            }
        }
    }

    LinearLayout view() { return view; }
    JSONObject values(boolean validate) { return collect(validate, false); }
    JSONObject editableValues() { return collect(false, true); }

    private JSONObject collect(boolean validate, boolean editableOnly) {
        JSONObject values = new JSONObject();
        for (Map.Entry<String, FieldReader> entry : readers.entrySet()) {
            if (editableOnly && !"EDIT".equalsIgnoreCase(
                    entry.getValue().field.optString("permission"))) continue;
            Object value = entry.getValue().provider.get();
            boolean empty = value == null || "".equals(value)
                    || value instanceof JSONArray && ((JSONArray) value).length() == 0;
            if (validate && entry.getValue().field.optBoolean("required") && empty) {
                throw new IllegalArgumentException("请填写“"
                        + entry.getValue().field.optString("fieldLabel", entry.getKey()) + "”");
            }
            try { values.put(entry.getKey(), value == null ? JSONObject.NULL : value); }
            catch (Exception ignored) {}
        }
        return values;
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean ? (Boolean) value
                : "true".equalsIgnoreCase(String.valueOf(value));
    }

    private static boolean contains(JSONArray array, String value) {
        for (int i = 0; i < array.length(); i++) if (value.equals(array.optString(i))) return true;
        return false;
    }

    private static List<Option> options(JSONObject field) {
        List<Option> result = new ArrayList<>();
        try {
            JSONArray values = new JSONArray(field.optString("optionsJson"));
            for (int i = 0; i < values.length(); i++) {
                JSONObject item = values.optJSONObject(i);
                if (item != null) result.add(new Option(
                        String.valueOf(item.opt("value")), String.valueOf(item.opt("label"))));
            }
        } catch (Exception ignored) {}
        return result;
    }

    private interface ValueProvider { Object get(); }
    private static final class FieldReader {
        final JSONObject field; final ValueProvider provider;
        FieldReader(JSONObject field, ValueProvider provider) {
            this.field = field; this.provider = provider;
        }
    }
    private static final class Option {
        final String value; final String label;
        Option(String value, String label) { this.value = value; this.label = label; }
    }
}
