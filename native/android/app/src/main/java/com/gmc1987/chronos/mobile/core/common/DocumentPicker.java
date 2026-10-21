package com.gmc1987.chronos.mobile.core.common;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import java.util.function.Consumer;

/** Coordinates the platform document picker without introducing an AndroidX dependency. */
public final class DocumentPicker {
    private static final int OPEN_REQUEST = 4811;
    private static final int SAVE_REQUEST = 4812;
    private Consumer<Uri> openCallback;
    private Consumer<Uri> saveCallback;

    public void open(Activity activity, Consumer<Uri> callback) {
        openCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("*/*");
        activity.startActivityForResult(intent, OPEN_REQUEST);
    }

    public void openImage(Activity activity, Consumer<Uri> callback) {
        openCallback = callback;
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType("image/*");
        activity.startActivityForResult(intent, OPEN_REQUEST);
    }

    public void create(Activity activity, String fileName, String mimeType, Consumer<Uri> callback) {
        saveCallback = callback;
        Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT)
                .addCategory(Intent.CATEGORY_OPENABLE)
                .setType(mimeType == null || mimeType.isEmpty() ? "application/octet-stream" : mimeType)
                .putExtra(Intent.EXTRA_TITLE, fileName);
        activity.startActivityForResult(intent, SAVE_REQUEST);
    }

    public boolean handle(int requestCode, int resultCode, Intent data) {
        Consumer<Uri> callback;
        if (requestCode == OPEN_REQUEST) {
            callback = openCallback;
            openCallback = null;
        } else if (requestCode == SAVE_REQUEST) {
            callback = saveCallback;
            saveCallback = null;
        } else {
            return false;
        }
        if (resultCode == Activity.RESULT_OK && data != null && data.getData() != null
                && callback != null) callback.accept(data.getData());
        return true;
    }
}
