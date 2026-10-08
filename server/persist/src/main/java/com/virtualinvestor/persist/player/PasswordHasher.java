package com.virtualinvestor.persist.player;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.HexFormat;
import java.util.Objects;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * Password hashing without a new dependency (player-accounts D1): PBKDF2-HMAC-SHA256
 * from the JDK with a per-user salt. Verification compares in constant time.
 */
public final class PasswordHasher {

    private static final String ALGORITHM = "PBKDF2WithHmacSHA256";
    private static final int ITERATIONS = 210_000;
    private static final int KEY_BITS = 256;
    private static final int SALT_BYTES = 16;

    private PasswordHasher() {
    }

    public record Hash(String saltHex, String hashHex) {
    }

    public static Hash hash(String password) {
        byte[] salt = new byte[SALT_BYTES];
        new SecureRandom().nextBytes(salt);
        return new Hash(HexFormat.of().formatHex(salt), derive(password, salt));
    }

    public static boolean matches(String password, String saltHex, String hashHex) {
        if (password == null || saltHex == null || hashHex == null) {
            return false;
        }
        byte[] expected;
        byte[] actual;
        try {
            expected = HexFormat.of().parseHex(hashHex);
            actual = HexFormat.of().parseHex(derive(password, HexFormat.of().parseHex(saltHex)));
        } catch (IllegalArgumentException e) {
            return false;
        }
        return MessageDigest.isEqual(expected, actual);
    }

    private static String derive(String password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(
                    Objects.requireNonNull(password, "password").toCharArray(), salt, ITERATIONS, KEY_BITS);
            byte[] key = SecretKeyFactory.getInstance(ALGORITHM).generateSecret(spec).getEncoded();
            spec.clearPassword();
            return HexFormat.of().formatHex(key);
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2 unavailable", e);
        }
    }
}
