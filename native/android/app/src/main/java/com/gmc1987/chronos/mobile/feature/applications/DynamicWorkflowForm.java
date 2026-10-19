package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.text.InputType;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Switch;
import android.widget.Toast;
import com.gmc1987.chronos.mobile.MainActivity;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.DocumentPicker;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Builds a native Android form from the workflow field schema. */
public final class DynamicWorkflowForm {
    private static final long MAX_FILE_BYTES = 20L * 1024L * 1024L;
    private final LinearLayout view;
    private final Map<String, FieldReader> readers = new LinkedHashMap<>();
    private final List<FileField> fileFields = new ArrayList<>();

    public DynamicWorkflowForm(Activity activity, UiKit ui, AsyncRunner async, HttpClient http,
            String businessId, JSONArray fields, JSONObject initial) {
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
            boolean readOnly = !field.optString("permission", "EDIT").equalsIgnoreCase("EDIT");
            Object old = initial == null ? null : initial.opt(key);
            view.addView(ui.label(title, 13, UiKit.MUTED, false));
            if ("BOOLEAN".equals(type)) {
                Switch control = new Switch(activity);
                control.setText(title); control.setChecked(asBoolean(old)); control.setEnabled(!readOnly);
                view.addView(control); readers.put(key, new FieldReader(field, control::isChecked));
            } else if ("SELECT".equals(type) || "RADIO".equals(type)) {
                addChoice(activity, field, key, old, readOnly);
            } else if ("CHECKBOX".equals(type)) {
                addChecks(activity, ui, field, key, old, readOnly);
            } else if ("FILE".equals(type)) {
                JSONArray attachments = old instanceof JSONArray ? (JSONArray) old : new JSONArray();
                FileField files = new FileField(activity, ui, async, http, businessId,
                        attachments, readOnly);
                fileFields.add(files); view.addView(files.view);
                readers.put(key, new FieldReader(field, () -> attachments));
            } else if ("DATE".equals(type) || "DATETIME".equals(type)) {
                EditText input = ui.input(title, false);
                input.setText(old == null || old == JSONObject.NULL ? "" : String.valueOf(old));
                input.setFocusable(false); input.setEnabled(!readOnly);
                if (!readOnly) input.setOnClickListener(ignored -> pickDate(activity, input,
                        "DATETIME".equals(type)));
                view.addView(input);
                readers.put(key, new FieldReader(field, () -> input.getText().toString().trim()));
            } else {
                EditText input = "TEXTAREA".equals(type)
                        ? ui.multilineInput(title, 4) : ui.input(title, false);
                input.setText(old == null || old == JSONObject.NULL ? "" : String.valueOf(old));
                input.setEnabled(!readOnly);
                if ("NUMBER".equals(type)) input.setInputType(InputType.TYPE_CLASS_NUMBER
                        | InputType.TYPE_NUMBER_FLAG_DECIMAL | InputType.TYPE_NUMBER_FLAG_SIGNED);
                view.addView(input);
                readers.put(key, new FieldReader(field, () -> input.getText().toString().trim()));
            }
        }
    }

    public LinearLayout view() { return view; }
    public JSONObject values(boolean validate) {
        verifyNoUpload();
        return collect(validate, false);
    }
    JSONObject editableValues() {
        verifyNoUpload();
        return collect(false, true);
    }
    private void verifyNoUpload() {
        for (FileField files : fileFields) if (files.uploading) {
            throw new IllegalArgumentException("附件正在上传，请稍后提交");
        }
    }

    private void addChoice(Activity activity, JSONObject field, String key, Object old,
            boolean readOnly) {
        List<Option> options = options(field);
        List<String> labels = new ArrayList<>(); labels.add("请选择");
        for (Option option : options) labels.add(option.label);
        Spinner spinner = new Spinner(activity);
        spinner.setAdapter(new ArrayAdapter<>(activity,
                android.R.layout.simple_spinner_dropdown_item, labels));
        spinner.setEnabled(!readOnly);
        String current = old == null ? "" : String.valueOf(old);
        for (int n = 0; n < options.size(); n++) if (options.get(n).value.equals(current)) {
            spinner.setSelection(n + 1);
        }
        view.addView(spinner);
        readers.put(key, new FieldReader(field, () -> spinner.getSelectedItemPosition() == 0
                ? "" : options.get(spinner.getSelectedItemPosition() - 1).value));
    }

    private void addChecks(Activity activity, UiKit ui, JSONObject field, String key, Object old,
            boolean readOnly) {
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
            for (CheckBox check : checks) if (check.isChecked()) result.put(String.valueOf(check.getTag()));
            return result;
        }));
    }

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

    private static void pickDate(Activity activity, EditText input, boolean includeTime) {
        Calendar calendar = Calendar.getInstance();
        new DatePickerDialog(activity, (picker, year, month, day) -> {
            calendar.set(year, month, day);
            if (!includeTime) {
                input.setText(new SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(calendar.getTime()));
                return;
            }
            new TimePickerDialog(activity, (time, hour, minute) -> {
                calendar.set(Calendar.HOUR_OF_DAY, hour); calendar.set(Calendar.MINUTE, minute);
                calendar.set(Calendar.SECOND, 0);
                input.setText(new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA)
                        .format(calendar.getTime()));
            }, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE), true).show();
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
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

    private static final class FileField {
        final LinearLayout view; final JSONArray attachments;
        boolean uploading;
        private final Activity activity; private final UiKit ui; private final AsyncRunner async;
        private final HttpClient http; private final String businessId; private final boolean readOnly;
        private final DocumentPicker picker;

        FileField(Activity activity, UiKit ui, AsyncRunner async, HttpClient http,
                String businessId, JSONArray attachments, boolean readOnly) {
            this.activity = activity; this.ui = ui; this.async = async; this.http = http;
            this.businessId = businessId; this.attachments = attachments; this.readOnly = readOnly;
            this.picker = ((MainActivity) activity).documentPicker(); this.view = ui.vertical(); render();
        }

        private void render() {
            view.removeAllViews();
            if (attachments.length() == 0) view.addView(ui.label("暂无附件", 14, UiKit.MUTED, false));
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject item = attachments.optJSONObject(i); if (item == null) continue;
                LinearLayout row = ui.horizontal();
                Button download = ui.secondaryButton(fileName(item));
                download.setOnClickListener(ignored -> download(item));
                row.addView(download, new LinearLayout.LayoutParams(0, ui.dp(46), 1));
                if (!readOnly) {
                    Button remove = ui.destructiveButton("删除");
                    remove.setOnClickListener(ignored -> remove(item));
                    row.addView(remove, new LinearLayout.LayoutParams(ui.dp(82), ui.dp(46)));
                }
                view.addView(row);
            }
            if (!readOnly) {
                Button add = ui.secondaryButton(uploading ? "正在上传…" : "选择附件");
                add.setEnabled(!uploading && attachments.length() < 5);
                add.setOnClickListener(ignored -> picker.open(activity, this::upload));
                view.addView(add);
            }
        }

        private void upload(Uri uri) {
            uploading = true; render();
            async.run(() -> {
                String name = queryName(uri); byte[] bytes = readAll(uri);
                String mime = activity.getContentResolver().getType(uri);
                String path = "/files?businessType=" + (businessId == null
                        ? "WORKFLOW_FORM_DRAFT" : "WORKFLOW_FORM");
                if (businessId != null) path += "&businessId="
                        + URLEncoder.encode(businessId, StandardCharsets.UTF_8.name());
                return ApplicationJson.dataObject(http.upload(path, name,
                        mime == null ? "application/octet-stream" : mime, bytes));
            }, uploaded -> { attachments.put(uploaded); uploading = false; render(); },
                    error -> { uploading = false; render(); showError(error); });
        }

        private void remove(JSONObject item) {
            String id = item.optString("id"); if (id.isEmpty()) return;
            async.run(() -> http.delete("/files/" + id), ignored -> {
                for (int i = 0; i < attachments.length(); i++) {
                    JSONObject candidate = attachments.optJSONObject(i);
                    if (candidate != null && id.equals(candidate.optString("id"))) {
                        attachments.remove(i); break;
                    }
                }
                render();
            }, this::showError);
        }

        private void download(JSONObject item) {
            String id = item.optString("id"); if (id.isEmpty()) return;
            async.run(() -> http.download("/files/" + id + "/content"), bytes ->
                    picker.create(activity, fileName(item), item.optString("contentType"), uri ->
                            async.run(() -> {
                                try (OutputStream output = activity.getContentResolver().openOutputStream(uri)) {
                                    if (output == null) throw new IllegalStateException("无法保存附件");
                                    output.write(bytes);
                                }
                                return true;
                            }, ignored -> Toast.makeText(activity, "附件已保存", Toast.LENGTH_SHORT).show(),
                                    this::showError)), this::showError);
        }

        private String queryName(Uri uri) {
            try (Cursor cursor = activity.getContentResolver().query(uri, null, null, null, null)) {
                if (cursor != null && cursor.moveToFirst()) {
                    int index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (index >= 0) return cursor.getString(index);
                }
            }
            return "attachment";
        }
        private byte[] readAll(Uri uri) throws Exception {
            try (InputStream input = activity.getContentResolver().openInputStream(uri);
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                if (input == null) throw new IllegalArgumentException("无法读取所选附件");
                byte[] buffer = new byte[8192]; int count;
                while ((count = input.read(buffer)) != -1) {
                    output.write(buffer, 0, count);
                    if (output.size() > MAX_FILE_BYTES) throw new IllegalArgumentException("单个附件不能超过 20MB");
                }
                return output.toByteArray();
            }
        }
        private void showError(Exception error) {
            Toast.makeText(activity, ApplicationJson.message(error), Toast.LENGTH_LONG).show();
        }
        private static String fileName(JSONObject item) {
            String name = item.optString("name");
            return name.isEmpty() ? item.optString("originalName", "附件") : name;
        }
    }

    private interface ValueProvider { Object get(); }
    private static final class FieldReader {
        final JSONObject field; final ValueProvider provider;
        FieldReader(JSONObject field, ValueProvider provider) { this.field = field; this.provider = provider; }
    }
    private static final class Option {
        final String value; final String label;
        Option(String value, String label) { this.value = value; this.label = label; }
    }
}
