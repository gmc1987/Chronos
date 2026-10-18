package com.gmc1987.chronos.mobile.feature.applications;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cards and detail rows used by native record lists. */
final class NativeRecordViews {
    private static final Map<String, String> LABELS = labels();
    private final UiKit ui;

    NativeRecordViews(UiKit ui) {
        this.ui = ui;
    }

    LinearLayout card(JSONObject source) {
        JSONObject row = source.optJSONObject("meeting");
        if (row == null) row = source;
        LinearLayout card = ui.card();
        String[] preferred = {"flowName", "title", "name", "purpose", "documentTitle",
                "resourceName", "fileName", "leaveType", "requestType", "status", "nodeName",
                "destination", "startDate", "startTime", "endDate", "endTime", "createTime"};
        int shown = 0;
        for (String key : preferred) {
            if (!row.has(key) || row.isNull(key)) continue;
            String value = String.valueOf(row.opt(key));
            if (value.isEmpty() || "null".equals(value)) continue;
            card.addView(ui.label((shown == 0 ? "" : label(key) + "：") + value,
                    shown == 0 ? 17 : 13, shown == 0 ? UiKit.TEXT : UiKit.MUTED, shown == 0));
            if (++shown >= 7) break;
        }
        if (shown == 0) card.addView(ui.label("业务记录", 17, UiKit.TEXT, true));
        return card;
    }

    void appendDetail(LinearLayout page, Object value, String prefix) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                appendDetail(page, object.opt(key), prefix.isEmpty() ? label(key)
                        : prefix + " · " + label(key));
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) page.addView(detailRow(prefix, "暂无"));
            for (int i = 0; i < array.length(); i++) {
                appendDetail(page, array.opt(i), prefix + " " + (i + 1));
            }
        } else {
            page.addView(detailRow(prefix,
                    value == null || value == JSONObject.NULL ? "—" : String.valueOf(value)));
        }
    }

    private View detailRow(String key, String value) {
        LinearLayout card = ui.card();
        card.addView(ui.label(key, 13, UiKit.MUTED, false));
        TextView content = ui.label(value, 15, UiKit.TEXT, false);
        content.setTextIsSelectable(true);
        card.addView(content);
        return card;
    }

    static String label(String key) {
        return LABELS.getOrDefault(key, key);
    }

    private static Map<String, String> labels() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("status", "状态"); values.put("nodeName", "节点");
        values.put("initiator", "发起人"); values.put("startTime", "开始时间");
        values.put("endTime", "结束时间"); values.put("startDate", "开始日期");
        values.put("endDate", "结束日期"); values.put("createTime", "创建时间");
        values.put("description", "说明"); values.put("purpose", "事由");
        values.put("destination", "目的地"); values.put("leaveType", "假别");
        values.put("requestType", "申请类型"); values.put("resourceName", "资源");
        return values;
    }
}
