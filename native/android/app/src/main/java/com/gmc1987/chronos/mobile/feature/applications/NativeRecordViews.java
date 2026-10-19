package com.gmc1987.chronos.mobile.feature.applications;

import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.gmc1987.chronos.mobile.core.common.UiKit;
import java.util.Iterator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** Cards and detail rows used by native record lists. */
final class NativeRecordViews {
    private static final Map<String, String> LABELS = labels();
    private static final Set<String> HIDDEN = new HashSet<>(java.util.Arrays.asList(
            "businessKey", "rowVersion", "variablesJson", "operations", "returnTargets"));
    private final UiKit ui;

    NativeRecordViews(UiKit ui) {
        this.ui = ui;
    }

    LinearLayout card(JSONObject source) {
        JSONObject row = source.optJSONObject("meeting");
        if (row == null) row = source;
        LinearLayout card = ui.card();
        boolean contact = row.has("employeeName") && row.has("employeeCode");
        String[] preferred = contact
                ? new String[]{"employeeName", "employeeCode", "departmentName", "positionName"}
                : new String[]{"flowName", "title", "name", "purpose", "documentTitle",
                        "resourceName", "fileName", "leaveType", "requestType", "status", "nodeName",
                        "initiator", "assignee", "destination", "startDate", "startTime", "endDate",
                        "endTime", "createTime"};
        int shown = 0;
        for (String key : preferred) {
            if (hidden(key) || !LABELS.containsKey(key) || !row.has(key) || row.isNull(key)) continue;
            String value = displayValue(key, String.valueOf(row.opt(key)));
            if (value.isEmpty() || "null".equals(value)) continue;
            card.addView(ui.label((shown == 0 ? "" : label(key) + "：") + value,
                    shown == 0 ? 17 : 13, shown == 0 ? UiKit.TEXT : UiKit.MUTED, shown == 0));
            if (++shown >= 7) break;
        }
        if (shown == 0) card.addView(ui.label("业务记录", 17, UiKit.TEXT, true));
        return card;
    }

    void appendDetail(LinearLayout page, Object value, String prefix) {
        appendDetail(page, value, prefix, null);
    }

    private void appendDetail(LinearLayout page, Object value, String prefix, String fieldKey) {
        if (value instanceof JSONObject) {
            JSONObject object = (JSONObject) value;
            Iterator<String> keys = object.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                if (hidden(key) || !LABELS.containsKey(key)) continue;
                appendDetail(page, object.opt(key), prefix.isEmpty() ? label(key)
                        : prefix + " · " + label(key), key);
            }
        } else if (value instanceof JSONArray) {
            JSONArray array = (JSONArray) value;
            if (array.length() == 0) page.addView(detailRow(prefix, "暂无"));
            for (int i = 0; i < array.length(); i++) {
                appendDetail(page, array.opt(i), prefix + " " + (i + 1), fieldKey);
            }
        } else {
            page.addView(detailRow(prefix, value == null || value == JSONObject.NULL
                    ? "—" : displayValue(fieldKey, String.valueOf(value))));
        }
    }

    private static boolean hidden(String key) {
        return HIDDEN.contains(key) || "id".equals(key) || key.endsWith("Id") || key.endsWith("ID");
    }

    private static String displayValue(String key, String value) {
        if (key != null && (key.endsWith("Time") || key.endsWith("At")
                || "dueAt".equals(key) || "finishedAt".equals(key)) && value.length() >= 19) {
            return value.substring(0, 19).replace('T', ' ');
        }
        if ("status".equals(key) || "instanceStatus".equals(key)
                || "financeStatus".equals(key) || "cancellationStatus".equals(key)) {
            Map<String, String> statuses = new LinkedHashMap<>();
            statuses.put("PENDING", "待处理"); statuses.put("CLAIMABLE", "待认领");
            statuses.put("APPROVED", "已通过"); statuses.put("REJECTED", "已拒绝");
            statuses.put("RUNNING", "审批中"); statuses.put("COMPLETED", "已完成");
            statuses.put("WITHDRAWN", "已撤回"); statuses.put("CANCELLED", "已取消");
            statuses.put("TERMINATED", "已终止"); statuses.put("NONE", "无");
            statuses.put("NOT_REQUIRED", "无需处理");
            return statuses.getOrDefault(value, value);
        }
        return value;
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
        values.put("flowName", "流程名称"); values.put("flowCode", "流程编码");
        values.put("title", "标题"); values.put("name", "名称");
        values.put("status", "状态"); values.put("instanceStatus", "流程状态");
        values.put("nodeName", "当前节点"); values.put("nodeKey", "节点编码");
        values.put("currentNodeKey", "当前节点编码");
        values.put("initiator", "发起人"); values.put("startTime", "开始时间");
        values.put("endTime", "结束时间"); values.put("startDate", "开始日期");
        values.put("endDate", "结束日期"); values.put("createTime", "创建时间");
        values.put("startAt", "开始时间"); values.put("endAt", "结束时间");
        values.put("lastUpdateTime", "更新时间"); values.put("dueAt", "办理期限");
        values.put("completedAt", "完成时间"); values.put("finishedAt", "结束时间");
        values.put("withdrawnAt", "撤回时间"); values.put("cancelledAt", "取消时间");
        values.put("description", "说明"); values.put("summary", "摘要");
        values.put("purpose", "事由"); values.put("documentTitle", "公文标题");
        values.put("fileName", "文件名称"); values.put("resourceName", "使用资源");
        values.put("destination", "目的地"); values.put("leaveType", "假别");
        values.put("requestType", "申请类型"); values.put("passengerCount", "乘车人数");
        values.put("copyCount", "份数"); values.put("applicantType", "申请人类型");
        values.put("requestedDays", "请假天数"); values.put("reason", "申请原因");
        values.put("actualEndDate", "实际结束日期"); values.put("approvedBy", "审批人");
        values.put("assignee", "处理人"); values.put("comment", "审批意见");
        values.put("version", "版本"); values.put("category", "分类"); values.put("tags", "标签");
        values.put("employeeName", "姓名"); values.put("employeeCode", "工号");
        values.put("departmentName", "归属部门"); values.put("positionName", "职级");
        values.put("workPhone", "电话"); values.put("workEmail", "邮箱");
        values.put("financeStatus", "财务状态"); values.put("financeReference", "财务凭证");
        values.put("estimatedAmount", "预计总额"); values.put("currency", "币种");
        values.put("transportAmount", "交通费"); values.put("accommodationAmount", "住宿费");
        values.put("mealAmount", "餐费"); values.put("otherAmount", "其他费用");
        values.put("budgetProjectCode", "预算项目编码"); values.put("costCenterCode", "成本中心编码");
        values.put("cancellationStatus", "销假状态"); values.put("cancellationReason", "销假原因");
        values.put("cancellationComment", "销假意见"); values.put("taskKind", "任务类型");
        values.put("slaStatus", "时限状态"); values.put("reminderCount", "催办次数");
        values.put("escalationLevel", "升级级别"); values.put("returnPolicy", "退回策略");
        values.put("rejectPolicy", "拒绝策略"); values.put("claimable", "可认领");
        values.put("claimed", "已认领"); values.put("direction", "收发类型");
        values.put("urgency", "紧急程度");
        return values;
    }
}
