package com.gmc1987.chronos.mobile.core.common;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class JsonLists {
    private JsonLists() {}

    public static List<JSONObject> objects(Object data) {
        JSONArray array;
        if (data instanceof JSONArray) array = (JSONArray) data;
        else if (data instanceof JSONObject) {
            JSONObject object = (JSONObject) data;
            array = object.optJSONArray("content");
            if (array == null) array = object.optJSONArray("records");
            if (array == null) return Collections.singletonList(object);
        } else return Collections.emptyList();
        List<JSONObject> values = new ArrayList<>();
        for (int i = 0; i < array.length(); i++) {
            JSONObject value = array.optJSONObject(i);
            if (value != null) values.add(value);
        }
        return values;
    }

    public static String first(JSONObject value, String... keys) {
        for (String key : keys) {
            String item = value.optString(key, "");
            if (!item.isEmpty()) return item.length() > 100 ? item.substring(0, 100) + "…" : item;
        }
        return "详情";
    }
}
