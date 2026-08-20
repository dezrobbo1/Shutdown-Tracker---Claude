package com.shutdowntracker.api.storage;

import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/** SHA-256 over a stream, in the lower-case hex form every hash column in this schema uses. */
public final class Sha256 {

    private static final int BUFFER_SIZE = 8192;

    private Sha256() {
    }

    /** Consumes and closes {@code content}. */
    public static String hex(InputStream content) throws IOException {
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 digest is not available.", exception);
        }

        byte[] buffer = new byte[BUFFER_SIZE];
        try (InputStream input = content) {
            int read;
            while ((read = input.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }
}
