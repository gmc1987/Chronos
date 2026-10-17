package com.gmc1987.chronos.mobile.core.common;

import java.util.ArrayList;
import java.util.List;
import org.json.JSONObject;

public final class RowFormatters {
    private RowFormatters() {}

    public static List<String> grades(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : JsonLists.objects(data)) {
            rows.add(item.optString("courseName", "课程") + " · "
                    + item.optString("totalScore", "—") + " · "
                    + item.optString("gradeLevel", "未评级"));
        }
        return rows;
    }

    public static List<String> exams(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : JsonLists.objects(data)) {
            rows.add(item.optString("subjectName", item.optString("planName", "考试"))
                    + "\n" + item.optString("examDate", "时间待定") + " "
                    + item.optString("startTime", "") + "-" + item.optString("endTime", "")
                    + " · " + item.optString("classroomName", "考场待定")
                    + " · 座位 " + item.optString("seatNo", "—"));
        }
        return rows;
    }

    public static List<String> meetings(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : JsonLists.objects(data)) {
            JSONObject meeting = item.optJSONObject("meeting");
            JSONObject room = item.optJSONObject("room");
            if (meeting == null) meeting = item;
            rows.add(meeting.optString("title", meeting.optString("subject", "会议"))
                    + " · " + meeting.optString("startTime", meeting.optString("startAt", "时间待定"))
                    + " · " + (room == null ? "地点待定" : room.optString("roomName", "地点待定")));
        }
        return rows;
    }

    public static List<String> publications(Object data) {
        if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            if (object.opt("content") != null) return generic(object.opt("content"));
            if (object.opt("records") != null) return generic(object.opt("records"));
        }
        return generic(data);
    }

    public static List<String> generic(Object data) {
        List<String> rows = new ArrayList<>();
        for (JSONObject item : JsonLists.objects(data)) {
            rows.add(JsonLists.first(item, "title", "subject", "name", "taskName", "content"));
        }
        return rows;
    }
}
