package com.gmc1987.chronos.mobile.core.common;

import com.gmc1987.chronos.mobile.core.network.ApiException;

public final class ErrorMessages {
    private ErrorMessages() {}

    public static String display(Exception error, String fallback) {
        if (error instanceof ApiException && ((ApiException) error).status() == 401) {
            return "会话已过期，请重新登录";
        }
        return error.getMessage() == null || error.getMessage().isEmpty()
                ? fallback : error.getMessage();
    }
}
