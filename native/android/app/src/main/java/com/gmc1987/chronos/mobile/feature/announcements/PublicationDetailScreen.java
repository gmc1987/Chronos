package com.gmc1987.chronos.mobile.feature.announcements;

import android.app.Activity;
import android.net.Uri;
import android.widget.Button;
import android.widget.LinearLayout;
import com.gmc1987.chronos.mobile.MainActivity;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ErrorMessages;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.io.OutputStream;
import org.json.JSONArray;
import org.json.JSONObject;

/** Native publication body, read receipt and authenticated attachment download. */
public final class PublicationDetailScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final AnnouncementsRepository repository;
    private final String id;
    private final Runnable back;

    public PublicationDetailScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            AnnouncementsRepository repository, String id, Runnable back) {
        this.activity = activity; this.ui = ui; this.async = async; this.host = host;
        this.repository = repository; this.id = id; this.back = back;
    }

    public void show() {
        host.setBackAction(back);
        host.show(ui.centered("正在加载通知详情…", true));
        async.run(() -> {
            JSONObject result = repository.detail(id);
            repository.markRead(id);
            return result;
        }, this::render, error -> {
            LinearLayout page = ui.page("通知详情");
            page.addView(ui.error(ErrorMessages.display(error, "通知详情加载失败"), this::show));
            host.show(ui.scroll(page));
        });
    }

    private void render(JSONObject response) {
        JSONObject value = response.optJSONObject("data");
        LinearLayout page = ui.page("通知详情");
        if (value == null) { page.addView(ui.empty("通知不存在或已撤回")); host.show(ui.scroll(page)); return; }
        page.addView(ui.label(value.optString("title", "未命名通知"), 22, UiKit.TEXT, true));
        page.addView(ui.label(value.optString("publishedAt", ""), 13, UiKit.MUTED, false));
        if (!value.optString("summary").isEmpty()) {
            page.addView(ui.label(value.optString("summary"), 16, UiKit.MUTED, true));
        }
        page.addView(ui.label(value.optString("content", "暂无正文"), 16, UiKit.TEXT, false));
        JSONArray attachments = value.optJSONArray("attachments");
        if (attachments != null && attachments.length() > 0) {
            page.addView(ui.label("附件", 18, UiKit.TEXT, true));
            for (int i = 0; i < attachments.length(); i++) {
                JSONObject attachment = attachments.optJSONObject(i);
                if (attachment == null) continue;
                Button button = ui.secondaryButton(attachment.optString("originalName", "下载附件"));
                button.setOnClickListener(view -> chooseDestination(attachment));
                page.addView(button);
            }
        }
        host.show(ui.scroll(page));
    }

    private void chooseDestination(JSONObject attachment) {
        if (!(activity instanceof MainActivity)) return;
        String name = attachment.optString("originalName", "attachment");
        String mime = attachment.optString("contentType", "application/octet-stream");
        ((MainActivity) activity).documentPicker().create(activity, name, mime,
                uri -> download(attachment.optString("id"), uri));
    }

    private void download(String attachmentId, Uri destination) {
        async.run(() -> repository.attachment(attachmentId), bytes -> {
            try (OutputStream output = activity.getContentResolver().openOutputStream(destination)) {
                if (output == null) throw new IllegalStateException("无法写入所选文件");
                output.write(bytes);
            } catch (Exception error) {
                android.widget.Toast.makeText(activity, error.getMessage(), android.widget.Toast.LENGTH_LONG).show();
                return;
            }
            android.widget.Toast.makeText(activity, "附件已保存", android.widget.Toast.LENGTH_SHORT).show();
        }, error -> android.widget.Toast.makeText(activity,
                ErrorMessages.display(error, "附件下载失败"), android.widget.Toast.LENGTH_LONG).show());
    }
}
