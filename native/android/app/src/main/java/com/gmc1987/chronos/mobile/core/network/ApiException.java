package com.gmc1987.chronos.mobile.core.network;

import java.io.IOException;

public final class ApiException extends IOException {
    private final int status;

    public ApiException(int status, String message) {
        super(message);
        this.status = status;
    }

    public int status() {
        return status;
    }
}
