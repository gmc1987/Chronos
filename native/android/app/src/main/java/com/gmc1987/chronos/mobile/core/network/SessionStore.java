package com.gmc1987.chronos.mobile.core.network;

import android.content.SharedPreferences;

public final class SessionStore {
    private final SharedPreferences preferences;
    private final SecureTokenStore tokenStore;
    private String accessToken;
    private String refreshToken;

    public SessionStore(SharedPreferences preferences) {
        this.preferences = preferences;
        this.tokenStore = new SecureTokenStore(preferences);
        if (preferences.getBoolean("remember", false)) {
            accessToken = tokenStore.get("access_token");
            refreshToken = tokenStore.get("refresh_token");
        }
        // Remove credentials written by versions that persisted tokens in plain text.
        preferences.edit().remove("access_token").remove("refresh_token").apply();
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
            tokenStore.put("access_token", access);
            tokenStore.put("refresh_token", refresh);
        } else {
            tokenStore.clear();
        }
        editor.apply();
    }

    public void update(String access, String refresh) {
        accessToken = access;
        if (refresh != null) refreshToken = refresh;
        if (preferences.getBoolean("remember", false)) {
            tokenStore.put("access_token", accessToken);
            tokenStore.put("refresh_token", refreshToken);
        }
    }

    public void clear() {
        accessToken = null;
        refreshToken = null;
        tokenStore.clear();
        preferences.edit().remove("remember").apply();
    }
}
