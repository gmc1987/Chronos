package com.gmc1987.chronos.mobile.feature.auth;

import com.gmc1987.chronos.mobile.core.network.HttpClient;
import com.gmc1987.chronos.mobile.core.network.SessionStore;
import java.io.IOException;
import org.json.JSONObject;

public final class AuthRepository {
    private final HttpClient http;
    private final SessionStore session;

    public AuthRepository(HttpClient http, SessionStore session) {
        this.http = http;
        this.session = session;
    }

    public boolean hasSession() {
        return session.hasSession();
    }

    public void login(String username, String password, boolean remember) throws Exception {
        JSONObject response = http.post("/auth/login",
                new JSONObject().put("username", username).put("password", password), false);
        String access = HttpClient.token(response, "accessToken");
        if (access == null) throw new IOException("服务端未返回访问令牌");
        session.save(access, HttpClient.token(response, "refreshToken"), remember);
    }

    public void logout() {
        try {
            if (session.refreshToken() != null) {
                http.post("/auth/revoke",
                        new JSONObject().put("refreshToken", session.refreshToken()), false);
            }
        } catch (Exception ignored) {
            // Local credentials must still be removed when revoke is unreachable.
        } finally {
            session.clear();
        }
    }

    public void clear() {
        session.clear();
    }
}
