package com.virtualinvestor.persist.session;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * A per-participant session token so a disconnected player can reclaim their seat. The stored
 * value is a hash of the token, never the token itself; validation compares hashes in constant
 * time. A real deployment would also expire tokens, which is noted as future work.
 */
public final class SessionToken {

    private final String tokenHash;

    private SessionToken(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public static SessionToken issue(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new IllegalArgumentException("rawToken must not be blank");
        }
        return new SessionToken(hash(rawToken));
    }

    public static SessionToken fromHash(String tokenHash) {
        return new SessionToken(Objects.requireNonNull(tokenHash, "tokenHash"));
    }

    public String tokenHash() {
        return tokenHash;
    }

    public boolean matches(String rawToken) {
        if (rawToken == null) {
            return false;
        }
        return MessageDigest.isEqual(
                tokenHash.getBytes(StandardCharsets.UTF_8),
                hash(rawToken).getBytes(StandardCharsets.UTF_8));
    }

    private static String hash(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
