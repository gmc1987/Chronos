package com.gmc1987.chronos.mobile.feature.workbench;

import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import org.json.JSONArray;
import org.json.JSONObject;

/** Lets a teacher issue the short-lived signed proof required for a supervisor check-in. */
public final class TeachingSupervisionScreen {
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final SupervisionRepository repository;
    private final Runnable back;

    public TeachingSupervisionScreen(UiKit ui, AsyncRunner async, ScreenHost host,
            SupervisionRepository repository, Runnable back) {
        this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        LinearLayout page = ui.page("课堂督导核验");
        LinearLayout body = ui.vertical();
        body.addView(ui.centered("正在加载课堂督导任务…", true));
        page.addView(body);
        host.show(ui.scroll(page));
        async.run(repository::teachingTasks, response -> render(body, response), error -> {
            body.removeAllViews();
            body.addView(ui.error(ErrorMessages.display(error, "课堂督导任务加载失败"), this::show));
        });
    }

    private void render(LinearLayout body, JSONObject response) {
        body.removeAllViews();
        JSONArray rows = response.optJSONArray("data");
        if (rows == null || rows.length() == 0) {
            body.addView(ui.empty("当前没有涉及本人的督导任务")); return;
        }
        for (int i = 0; i < rows.length(); i++) {
            JSONObject task = rows.optJSONObject(i);
            if (task == null) continue;
            LinearLayout card = ui.card();
            card.addView(ui.label("督导任务 · " + task.optString("status"), 18, UiKit.TEXT, true));
            card.addView(ui.label("排课记录 " + task.optString("scheduleEntryId", "—"),
                    13, UiKit.MUTED, false));
            Button issue = ui.secondaryButton("生成一次性签到核验码");
            issue.setOnClickListener(view -> async.run(
                    () -> repository.issueProof(task.optString("id")), result -> {
                        JSONObject proof = result.optJSONObject("data");
                        if (proof != null) {
                            android.content.ClipboardManager clipboard =
                                    (android.content.ClipboardManager) view.getContext()
                                            .getSystemService(android.content.Context.CLIPBOARD_SERVICE);
                            clipboard.setPrimaryClip(android.content.ClipData.newPlainText(
                                    "督导签到核验码", proof.optString("signedProof")));
                            android.widget.Toast.makeText(view.getContext(),
                                    "核验码已复制，请交给现场督导使用",
                                    android.widget.Toast.LENGTH_LONG).show();
                        }
                    }, error -> android.widget.Toast.makeText(view.getContext(),
                            ErrorMessages.display(error, "核验码生成失败"),
                            android.widget.Toast.LENGTH_LONG).show()));
            card.addView(issue);
            body.addView(card);
        }
    }
}
