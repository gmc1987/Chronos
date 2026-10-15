package com.gmc1987.chronos.mobile;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

final class PortalState {
    enum Workspace { STUDENT, TEACHER, STAFF, MANAGEMENT }

    final JSONObject data;
    final JSONObject user;
    final JSONObject education;
    final JSONArray applications;
    final List<String> roles;
    final List<String> permissions;
    final List<String> profileTypes;
    final Workspace workspace;

    private PortalState(JSONObject data) {
        this.data = data;
        user = data.optJSONObject("user") == null ? new JSONObject() : data.optJSONObject("user");
        applications = data.optJSONArray("applications") == null ? new JSONArray() : data.optJSONArray("applications");
        JSONObject contributions = data.optJSONObject("contributions");
        JSONObject contribution = contributions == null ? null : contributions.optJSONObject("DATA");
        education = contribution == null || contribution.optJSONObject("data") == null
                ? new JSONObject() : contribution.optJSONObject("data");
        roles = strings(user.optJSONArray("roles"));
        permissions = strings(user.optJSONArray("permissions"));
        profileTypes = strings(education.optJSONArray("profileTypes"));
        workspace = classify();
    }

    static PortalState from(JSONObject envelope) {
        JSONObject data = envelope.optJSONObject("data");
        if (data == null) throw new IllegalArgumentException("门户初始化数据缺失");
        return new PortalState(data);
    }

    String displayName() {
        return user.optString("displayName", user.optString("username", "用户"));
    }

    String organization() {
        String department = user.optString("departmentName", "");
        if (!department.isEmpty()) return department;
        return user.optString("organizationName", "");
    }

    String termName() {
        return education.optString("termName", "当前学期");
    }

    boolean hasApplication(String... words) {
        for (int i = 0; i < applications.length(); i++) {
            JSONObject application = applications.optJSONObject(i);
            if (application == null) continue;
            String haystack = (application.optString("code") + " "
                    + application.optString("name") + " "
                    + application.optString("routePath")).toLowerCase(Locale.ROOT);
            for (String word : words) {
                if (haystack.contains(word.toLowerCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }

    JSONObject contribution(String code) {
        JSONObject contributions = data.optJSONObject("contributions");
        return contributions == null ? null : contributions.optJSONObject(code);
    }

    private Workspace classify() {
        boolean student = contains(profileTypes, "STUDENT");
        boolean teacher = contains(profileTypes, "TEACHER");
        boolean managementRole = containsAny(roles,
                "管理员", "校领导", "校长", "院长", "书记", "ADMIN", "MANAGER", "LEADER");
        boolean managementPermission = permissions.stream().anyMatch(value -> {
            String permission = value.toLowerCase(Locale.ROOT);
            return permission.equals("portal:manage")
                    || permission.contains("supervision:")
                    || permission.contains("data-center:")
                    || permission.contains(":admin")
                    || permission.endsWith(":manage");
        });
        boolean managementApplication = hasApplication("supervision", "督办", "data-center", "数据中心");
        if (managementRole && (managementPermission || managementApplication)) {
            return Workspace.MANAGEMENT;
        }
        if (teacher) return Workspace.TEACHER;
        if (student) return Workspace.STUDENT;
        if (managementPermission && managementApplication) return Workspace.MANAGEMENT;
        return Workspace.STAFF;
    }

    private static List<String> strings(JSONArray array) {
        List<String> values = new ArrayList<>();
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.optString(i, "");
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }

    private static boolean contains(List<String> values, String expected) {
        return values.stream().anyMatch(value -> expected.equalsIgnoreCase(value));
    }

    private static boolean containsAny(List<String> values, String... expected) {
        for (String value : values) {
            String normalized = value.toUpperCase(Locale.ROOT);
            for (String item : expected) {
                if (normalized.contains(item.toUpperCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
