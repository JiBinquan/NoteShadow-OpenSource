package com.noteshadow.app;

import android.content.Context;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.AtomicFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/** Encrypts the user-supplied API credential in app-private, non-backed-up storage. */
final class DeepSeekCredentialStore {
    private static final String ALIAS = "noteshadow_deepseek_api";
    private static final String FILE = "deepseek_credential.bin";
    private final File credentialFile;
    private final String keyAlias;

    DeepSeekCredentialStore(Context context) {
        this(context, FILE, ALIAS);
    }

    DeepSeekCredentialStore(Context context, String fileName, String alias) {
        credentialFile = new File(context.getNoBackupFilesDir(), fileName);
        keyAlias = alias;
    }

    boolean isConfigured() { return credentialFile.isFile() && credentialFile.length() > 12; }

    void save(String credential) throws Exception {
        String value = credential == null ? "" : credential.trim();
        if (value.length() < 8 || value.length() > 512 || value.contains("\n") || value.contains("\r")) {
            throw new IllegalArgumentException("密钥格式无效");
        }
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, key());
        byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
        AtomicFile atomic = new AtomicFile(credentialFile);
        FileOutputStream output = atomic.startWrite();
        try {
            output.write(cipher.getIV());
            output.write(encrypted);
            output.getFD().sync();
            atomic.finishWrite(output);
        } catch (Exception failure) {
            atomic.failWrite(output);
            throw failure;
        }
    }

    String load() throws Exception {
        if (!isConfigured()) return "";
        byte[] bytes = new byte[(int) credentialFile.length()];
        try (FileInputStream input = new FileInputStream(credentialFile)) {
            int offset = 0;
            while (offset < bytes.length) {
                int n = input.read(bytes, offset, bytes.length - offset);
                if (n < 0) throw new IllegalStateException("密钥文件损坏");
                offset += n;
            }
        }
        byte[] iv = new byte[12];
        System.arraycopy(bytes, 0, iv, 0, iv.length);
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, key(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(bytes, iv.length, bytes.length - iv.length), StandardCharsets.UTF_8);
    }

    boolean clear() { return !credentialFile.exists() || credentialFile.delete(); }

    /** Imports and removes one installer-staged key in app-specific external storage. */
    boolean importStaged(Context context) throws Exception {
        File external = context.getExternalFilesDir(null);
        if (external == null) return false;
        File staged = new File(external, "ds-key-import.txt");
        if (!staged.isFile()) return false;
        try (InputStream input = new FileInputStream(staged)) {
            byte[] buffer = new byte[513];
            int count = 0, next;
            while (count < buffer.length && (next = input.read(buffer, count, buffer.length - count)) > 0) count += next;
            if (count == buffer.length || input.read() >= 0) throw new IllegalStateException("密钥文件过长");
            save(new String(buffer, 0, count, StandardCharsets.UTF_8));
            return true;
        } finally {
            if (!staged.delete()) throw new IllegalStateException("临时密钥清理失败");
        }
    }

    private SecretKey key() throws Exception {
        return keyFor(keyAlias);
    }

    private static SecretKey keyFor(String alias) throws Exception {
        KeyStore store = KeyStore.getInstance("AndroidKeyStore");
        store.load(null);
        if (store.containsAlias(alias)) return ((KeyStore.SecretKeyEntry) store.getEntry(alias, null)).getSecretKey();
        KeyGenerator generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
        generator.init(new KeyGenParameterSpec.Builder(alias,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true).build());
        return generator.generateKey();
    }
}
