package com.gmc1987.chronos.mobile.core.network;

import android.content.SharedPreferences;

public final class SessionStore {
    private final SharedPreferences preferences;
    private String accessToken;
    private String refreshToken;

    public SessionStore(SharedPreferences preferences) {
        this.preferences = preferences;
        if (preferences.getBoolean("remember", false)) {
            accessToken = preferences.getString("access_token", null);
            refreshToken = preferences.getString("refresh_token", null);
        }
    }

    public boolean hasSession() {
        return accessToken != null && !accessToken.isEmpty();
    }

    public String accessToken() {
        return accessToken;
    }

    public String refreshToken() {
        return refreshToken;
    }

    public void save(String access, String refresh, boolean remember) {
        accessToken = access;
        refreshToken = refresh;
        SharedPreferences.Editor editor = preferences.edit().putBoolean("remember", remember);
        if (remember) {
            editor.putString("access_token", access);
            if (refresh != null) editor.putString("refresh_token", refresh);
        } else {
            editor.remove("access_token").remove("refresh_token");
        }
        editor.apply();
    }

    public void update(String access, String refresh) {
        accessToken = access;
        if (refresh != null) refreshToken = refresh;
        if (preferences.getBoolean("remember", false)) {
            preferences.edit()
                    .putString("access_token", accessToken)
                    .putString("refresh_token", refreshToken)
                    .apply();
        }
    }

    public void clear() {
        accessToken = null;
        refreshToken = null;
        preferences.edit().clear().apply();
    }
}
