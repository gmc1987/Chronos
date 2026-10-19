package com.gmc1987.chronos.mobile.core.common;

import android.app.Activity;
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions;
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import java.util.function.Consumer;

/** Permissionless QR scanner backed by the official Google Code Scanner UI. */
public final class QrCodeScanner {
    private final Activity activity;

    public QrCodeScanner(Activity activity) { this.activity = activity; }

    public void scan(Consumer<String> success, Consumer<String> failure) {
        GmsBarcodeScannerOptions options = new GmsBarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .enableAutoZoom()
                .build();
        GmsBarcodeScanning.getClient(activity, options).startScan()
                .addOnSuccessListener(value -> {
                    String raw = value.getRawValue();
                    if (raw == null || raw.trim().isEmpty()) failure.accept("二维码内容为空");
                    else success.accept(raw.trim());
                })
                .addOnCanceledListener(() -> { })
                .addOnFailureListener(error -> failure.accept(
                        error.getMessage() == null ? "扫码组件暂不可用，请手工粘贴核验码"
                                : error.getMessage()));
    }
}
