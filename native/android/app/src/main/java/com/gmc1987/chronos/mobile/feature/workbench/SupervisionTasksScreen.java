package com.gmc1987.chronos.mobile.feature.workbench;

import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import com.gmc1987.chronos.mobile.feature.applications.DynamicWorkflowForm;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native supervisor task intake and trusted teacher QR check-in flow. */
public final class SupervisionTasksScreen {
    private final UiKit ui;
    private final android.app.Activity activity;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final SupervisionRepository repository;
    private final HttpClient http;
    private final Runnable back;

    public SupervisionTasksScreen(android.app.Activity activity, UiKit ui, AsyncRunner async,
            ScreenHost host, SupervisionRepository repository, HttpClient http, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.http = http; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("督导任务");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载督导任务…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(repository::list, response -> render(body, response), error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "督导任务加载失败"), this::show));
        });
    }

    private void render(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray rows = response.optJSONArray("data");
        if (rows == null || rows.length() == 0) {
            body.addView(ui.empty("当前没有督导任务")); return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject task = rows.optJSONObject(i);
            if (task == null) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label("督导任务 · " + task.optString("status", "PENDING"),
                    18, UiKit.TEXT, true));
            card.addView(ui.label("教师 " + task.optString("teacherId", "—")
                    + " · 排课 " + task.optString("scheduleEntryId", "—"),
                    13, UiKit.MUTED, false));
            String id = task.optString("id");
            if ("PENDING".equals(task.optString("status"))) {
                addAction(card, "接受任务", () -> repository.accept(id));
            }
            if ("PENDING".equals(task.optString("status"))
                    || "ACCEPTED".equals(task.optString("status"))) {
                EditText proof = ui.multilineInput("扫描教师二维码后填入核验码", 3);
                card.addView(proof);
                addAction(card, "核验并签到", () -> {
                    String token = proof.getText().toString().trim();
                    if (token.isEmpty()) throw new IllegalArgumentException("请先扫描或填写教师核验码");
                    return repository.checkIn(id, token);
                });
            }
            if ("CHECKED_IN".equals(task.optString("status"))) {
                Button evaluate = ui.primaryButton("填写并提交督导评价");
                evaluate.setOnClickListener(view -> showEvaluation(task));
                card.addView(evaluate);
            }
            if ("SUBMITTED".equals(task.optString("status"))) {
                addAction(card, "完成督导任务", () -> repository.complete(id));
            }
            body.addView(card);
        }
    }

    private void showEvaluation(JSONObject task) {
        LinearLayout page = ui.page("督导评价");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载评价表单…", true));
        page.addView(body);
        host.setBackAction(this::show);
        host.show(ui.scroll(page));
        async.run(repository::evaluationForms, response -> {
            JSONArray forms = response.optJSONArray("data");
            if (forms == null || forms.length() == 0) {
                body.removeAllViews(); body.addView(ui.empty("尚未发布督导评价表单")); return;
            }
            JSONObject form = forms.optJSONObject(0);
            String formId = form == null ? "" : form.optString("id");
            loadEvaluationFields(body, task.optString("id"), formId);
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "评价表单加载失败"),
                    () -> showEvaluation(task)));
        });
    }

    private void loadEvaluationFields(LinearLayout body, String taskId, String formId) {
        async.run(() -> repository.evaluationFields(formId), response -> {
            body.removeAllViews();
            JSONArray fields = response.optJSONArray("data");
            DynamicWorkflowForm form = new DynamicWorkflowForm(
                    activity, ui, async, http, taskId, fields, new JSONObject());
            body.addView(form.view());
            Button submit = ui.primaryButton("提交评价并完成任务");
            submit.setOnClickListener(view -> {
                try {
                    JSONObject values = form.values(true);
                    submit.setEnabled(false);
                    async.run(() -> {
                        repository.saveEvaluation(taskId, formId, values);
                        repository.submitEvaluation(taskId, formId);
                        return repository.complete(taskId);
                    }, ignored -> show(), error -> {
                        submit.setEnabled(true);
                        Toast.makeText(activity, ErrorMessages.display(error, "督导评价提交失败"),
                                Toast.LENGTH_LONG).show();
                    });
                } catch (Exception validation) {
                    Toast.makeText(activity, validation.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
            body.addView(submit);
        }, error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "评价字段加载失败"),
                    () -> loadEvaluationFields(body, taskId, formId)));
        });
    }

    private void addAction(LinearLayout card, String title, Action action) {
        Button button = ui.secondaryButton(title);
        button.setOnClickListener(view -> {
            button.setEnabled(false);
            async.run(action::run, ignored -> {
                Toast.makeText(view.getContext(), title + "成功", Toast.LENGTH_SHORT).show();
                show();
            }, error -> {
                button.setEnabled(true);
                Toast.makeText(view.getContext(), ErrorMessages.display(error, title + "失败"),
                        Toast.LENGTH_LONG).show();
            });
        });
        card.addView(button);
    }

    private interface Action { JSONObject run() throws Exception; }
}
