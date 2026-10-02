package com.virtualinvestor.core.state;

import com.virtualinvestor.core.clock.WorldClock;
import com.virtualinvestor.core.codec.RecordTextCodec;
import com.virtualinvestor.core.event.GameEvent;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;

/**
 * Produces a stable fingerprint of world state. Because the world is a pure function of the
 * seed and the ordered event log, the log is a faithful representation of state: hashing it
 * (with the current tick) lets a replay prove it reconstructed exactly the original run.
 */
public final class WorldStateHasher {

    private WorldStateHasher() {
    }

    public static String hash(WorldClock clock, List<GameEvent> events) {
        MessageDigest digest = newDigest();
        update(digest, "tick=" + clock.tick());
        for (GameEvent event : events) {
            update(digest, RecordTextCodec.encode(event));
        }
        return toHex(digest.digest());
    }

    public static String hash(List<GameEvent> events) {
        MessageDigest digest = newDigest();
        for (GameEvent event : events) {
            update(digest, RecordTextCodec.encode(event));
        }
        return toHex(digest.digest());
    }

    private static void update(MessageDigest digest, String line) {
        digest.update(line.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }

    private static MessageDigest newDigest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static String toHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            hex.append(Character.forDigit((b >> 4) & 0xF, 16));
            hex.append(Character.forDigit(b & 0xF, 16));
        }
        return hex.toString();
    }
}
