package com.gmc1987.chronos.mobile.feature.applications;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import com.gmc1987.chronos.mobile.BuildConfig;
import com.gmc1987.chronos.mobile.core.common.AsyncRunner;
import com.gmc1987.chronos.mobile.core.common.ScreenHost;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import com.gmc1987.chronos.mobile.core.network.HttpClient;
import com.gmc1987.chronos.mobile.feature.workbench.MeetingsRepository;
import com.gmc1987.chronos.mobile.feature.repair.RepairListScreen;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import org.json.JSONObject;

/** Routes a server-authorized application to one dedicated native Android screen. */
public final class NativeApplicationScreen {
    private final Activity activity;
    private final UiKit ui;
    private final AsyncRunner async;
    private final ScreenHost host;
    private final HttpClient http;
    private final JSONObject application;
    private final Runnable back;

    public NativeApplicationScreen(Activity activity, UiKit ui, AsyncRunner async, ScreenHost host,
            HttpClient http, JSONObject application, Runnable back) {
        this.activity = activity;
        this.ui = ui;
        this.async = async;
        this.host = host;
        this.http = http;
        this.application = application;
        this.back = back;
    }

    public void show() {
        if (isExternal()) {
            openExternal();
            return;
        }
        String feature = application.optString("nativeFeatureCode");
        if (feature.isEmpty()) feature = fallbackFeature(application.optString("code"));
        switch (feature.toUpperCase(Locale.ROOT)) {
            case "WORKFLOW_TASKS":
                workflow().show(); break;
            case "COLLABORATION":
                modules("协同办公", NativeModules.collaboration()).show(); break;
            case "DOCUMENTS":
                records("公文中心", "/portal/collaboration/documents/my", "暂无公文").show(); break;
            case "FILES":
                records("文件中心", "/portal/collaboration/files/shares", "暂无共享文件").show(); break;
            case "MEETINGS":
                new MeetingsScreen(ui, async, host, new MeetingsRepository(http), back).show(); break;
            case "BUSINESS_TRIPS":
                new BusinessTripsScreen(activity, ui, async, host, http, back).show(); break;
            case "KNOWLEDGE":
                records("知识中心", "/admin/knowledge-bases?page=0&size=50", "暂无知识库").show(); break;
            case "AI_CENTER":
                records("AI 智能中心", "/agent/specs/list?page=0&size=50", "暂无智能体").show(); break;
            case "TEACHING_CENTER":
                modules("教学中心", NativeModules.teaching()).show(); break;
            case "FACILITY_REPAIR":
                new RepairListScreen(activity, ui, async, host, http, back).show(); break;
            default:
                new ApplicationScreenChrome(ui, host).message(
                        application.optString("name", "应用"),
                        "该应用尚未配置原生功能标识。", back);
                break;
        }
    }

    public void showWorkflowInstance(String instanceId) {
        workflow().showInstance(instanceId);
    }

    private WorkflowCenterScreen workflow() {
        return new WorkflowCenterScreen(activity, ui, async, host, http, back);
    }

    private NativeModuleHubScreen modules(String title, java.util.List<NativeModule> values) {
        return new NativeModuleHubScreen(activity, ui, async, host, http, title, values, back);
    }

    private NativeRecordsScreen records(String title, String path, String empty) {
        return new NativeRecordsScreen(ui, async, host, http, title, path, empty, back);
    }

    private boolean isExternal() {
        return "EXTERNAL".equalsIgnoreCase(application.optString("presentationMode"))
                || "EXTERNAL".equalsIgnoreCase(application.optString("openMode"));
    }

    private void openExternal() {
        String route = application.optString("routePath").trim();
        if (route.isEmpty()) {
            new ApplicationScreenChrome(ui, host).message("无法打开应用",
                    "服务端尚未配置外部地址。", back);
            return;
        }
        String url = route.startsWith("http://") || route.startsWith("https://")
                ? route : BuildConfig.PORTAL_BASE_URL.replaceAll("/$", "")
                        + (route.startsWith("/") ? route : "/" + route);
        activity.startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        back.run();
    }

    private static String fallbackFeature(String code) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("workflow", "WORKFLOW_TASKS"); values.put("oa", "COLLABORATION");
        values.put("document", "DOCUMENTS"); values.put("file", "FILES");
        values.put("EDUCATION_MEETINGS", "MEETINGS");
        values.put("STAFF_BUSINESS_TRIP", "BUSINESS_TRIPS");
        values.put("knowledge", "KNOWLEDGE"); values.put("ai", "AI_CENTER");
        values.put("teaching-center", "TEACHING_CENTER");
        values.put("FACILITY_REPAIR", "FACILITY_REPAIR");
        return values.getOrDefault(code, code);
    }
}
