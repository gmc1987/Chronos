package com.gmc1987.chronos.mobile;

import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts persisted session tokens with an app-private Android Keystore key. */
final class SecureTokenStore {
    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "chronos.mobile.session.v1";
    private static final String PREFIX = "secure_";
    private final SharedPreferences preferences;

    SecureTokenStore(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    synchronized String get(String name) {
        String stored = preferences.getString(PREFIX + name, null);
        if (stored == null || stored.isEmpty()) return null;
        try {
            String[] parts = stored.split("\\.", 2);
            if (parts.length != 2) return null;
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key(),
                    new GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)));
            byte[] plaintext = cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP));
            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (Exception failure) {
            preferences.edit().remove(PREFIX + name).apply();
            return null;
        }
    }

    synchronized void put(String name, String value) {
        if (value == null) {
            preferences.edit().remove(PREFIX + name).apply();
            return;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key());
            String encoded = Base64.encodeToString(cipher.getIV(), Base64.NO_WRAP) + "."
                    + Base64.encodeToString(
                            cipher.doFinal(value.getBytes(StandardCharsets.UTF_8)), Base64.NO_WRAP);
            preferences.edit().putString(PREFIX + name, encoded).apply();
        } catch (Exception failure) {
            throw new IllegalStateException("无法安全保存会话", failure);
        }
    }

    synchronized void clear() {
        preferences.edit()
                .remove(PREFIX + "access_token")
                .remove(PREFIX + "refresh_token")
                .remove("access_token")
                .remove("refresh_token")
                .apply();
    }

    private SecretKey key() throws Exception {
        KeyStore store = KeyStore.getInstance(ANDROID_KEYSTORE);
        store.load(null);
        java.security.Key existing = store.getKey(KEY_ALIAS, null);
        if (existing instanceof SecretKey) return (SecretKey) existing;
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        generator.init(new KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build());
        return generator.generateKey();
    }
}
