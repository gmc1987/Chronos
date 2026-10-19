package com.gmc1987.chronos.mobile.feature.family;

import android.app.Activity;
import android.view.Gravity;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class FamilyFeedbackScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final FamilyRepository repository;
    private final Runnable back;
    private final List<JSONObject> children = new ArrayList<>();

    public FamilyFeedbackScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            FamilyRepository repository, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        host.show(ui.centered("正在加载学生信息…", true));
        async.run(repository::children, this::render,
                error -> host.show(ui.error(ErrorMessages.display(error, "学生信息加载失败"), this::show)));
    }

    private void render(JSONObject response) {
        LinearLayout page = ui.page("家长反馈");
        JSONArray values = response.optJSONArray("data");
        List<String> names = new ArrayList<>();
        children.clear();
        if (values != null) for (int i = 0; i < values.length(); i++) {
            JSONObject child = values.optJSONObject(i);
            if (child == null) continue;
            children.add(child);
            names.add(child.optString("studentName", "学生"));
        }
        Spinner student = new Spinner(activity);
        student.setAdapter(new ArrayAdapter<>(activity, android.R.layout.simple_spinner_dropdown_item, names));
        page.addView(student);
        EditText title = ui.input("反馈主题", false);
        EditText content = ui.input("反馈内容", false);
        content.setSingleLine(false);
        content.setMinLines(5);
        content.setGravity(Gravity.TOP | Gravity.START);
        page.addView(title);
        page.addView(content);
        Button submit = ui.primaryButton("提交反馈");
        submit.setEnabled(!children.isEmpty());
        submit.setOnClickListener(view -> {
            if (title.getText().toString().trim().isEmpty() || content.getText().toString().trim().isEmpty()) {
                android.widget.Toast.makeText(activity, "请填写反馈主题和内容", android.widget.Toast.LENGTH_SHORT).show();
                return;
            }
            JSONObject child = children.get(student.getSelectedItemPosition());
            submit.setEnabled(false);
            async.run(() -> repository.submitFeedback(child.optString("id"),
                            title.getText().toString().trim(), content.getText().toString().trim()),
                    ignored -> {
                        android.widget.Toast.makeText(activity, "反馈已提交", android.widget.Toast.LENGTH_SHORT).show();
                        back.run();
                    }, error -> {
                        submit.setEnabled(true);
                        android.widget.Toast.makeText(activity,
                                ErrorMessages.display(error, "反馈提交失败"),
                                android.widget.Toast.LENGTH_LONG).show();
                    });
        });
        page.addView(submit);
        host.show(ui.scroll(page));
    }
}
