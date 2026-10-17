package com.gmc1987.chronos.mobile.core.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

public final class PortalState {
    public enum Workspace { STUDENT, TEACHER, STAFF, MANAGEMENT }

    private final JSONObject data;
    private final JSONObject user;
    private final JSONObject education;
    private final JSONArray applications;
    private final List<String> roles;
    private final List<String> permissions;
    private final List<String> profileTypes;
    private final Workspace workspace;

    private PortalState(JSONObject data) {
        this.data = data;
        user = object(data, "user");
        applications = data.optJSONArray("applications") == null
                ? new JSONArray() : data.optJSONArray("applications");
        JSONObject contribution = object(object(data, "contributions"), "DATA");
        education = object(contribution, "data");
        roles = strings(user.optJSONArray("roles"));
        permissions = strings(user.optJSONArray("permissions"));
        profileTypes = strings(education.optJSONArray("profileTypes"));
        workspace = classify();
    }

    public static PortalState from(JSONObject envelope) {
        JSONObject value = envelope.optJSONObject("data");
        if (value == null) throw new IllegalArgumentException("门户初始化数据缺失");
        return new PortalState(value);
    }

    public JSONObject user() { return user; }
    public JSONObject education() { return education; }
    public JSONArray applications() { return applications; }
    public Workspace workspace() { return workspace; }
    public String displayName() {
        return user.optString("displayName", user.optString("username", "用户"));
    }
    public String organization() {
        String department = user.optString("departmentName", "");
        return department.isEmpty() ? user.optString("organizationName", "") : department;
    }
    public String termName() { return education.optString("termName", "当前学期"); }
    public JSONObject contribution(String code) {
        return object(data.optJSONObject("contributions"), code);
    }

    public JSONObject findApplication(String... words) {
        for (int i = 0; i < applications.length(); i++) {
            JSONObject application = applications.optJSONObject(i);
            if (application == null) continue;
            String haystack = searchable(application);
            for (String word : words) {
                if (haystack.contains(word.toLowerCase(Locale.ROOT))) return application;
            }
        }
        return null;
    }

    public List<String> applicationNames(String... words) {
        List<String> names = new ArrayList<>();
        for (int i = 0; i < applications.length(); i++) {
            JSONObject application = applications.optJSONObject(i);
            if (application == null) continue;
            String haystack = searchable(application);
            for (String word : words) {
                if (haystack.contains(word.toLowerCase(Locale.ROOT))) {
                    names.add(application.optString("name", "应用"));
                    break;
                }
            }
        }
        return names;
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
        boolean managementApplication = findApplication(
                "supervision", "督办", "data-center", "数据中心") != null;
        if (managementRole && (managementPermission || managementApplication)) {
            return Workspace.MANAGEMENT;
        }
        if (teacher) return Workspace.TEACHER;
        if (student) return Workspace.STUDENT;
        if (managementPermission && managementApplication) return Workspace.MANAGEMENT;
        return Workspace.STAFF;
    }

    private static JSONObject object(JSONObject parent, String key) {
        if (parent == null || parent.optJSONObject(key) == null) return new JSONObject();
        return parent.optJSONObject(key);
    }
    private static String searchable(JSONObject value) {
        return (value.optString("code") + " " + value.optString("name") + " "
                + value.optString("routePath")).toLowerCase(Locale.ROOT);
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
            for (String item : expected) {
                if (value.toUpperCase(Locale.ROOT).contains(item.toUpperCase(Locale.ROOT))) return true;
            }
        }
        return false;
    }
}
