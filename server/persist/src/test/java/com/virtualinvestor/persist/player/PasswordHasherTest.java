package com.virtualinvestor.persist.player;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    @Test
    void hashVerifiesAndSaltsUniquely() {
        PasswordHasher.Hash first = PasswordHasher.hash("secret-1");
        PasswordHasher.Hash second = PasswordHasher.hash("secret-1");

        assertTrue(PasswordHasher.matches("secret-1", first.saltHex(), first.hashHex()));
        assertFalse(PasswordHasher.matches("wrong", first.saltHex(), first.hashHex()));
        assertNotEquals(first.saltHex(), second.saltHex(), "salts must differ per hash");
        assertNotEquals(first.hashHex(), second.hashHex());
    }

    @Test
    void malformedInputNeverMatches() {
        assertFalse(PasswordHasher.matches(null, "ab", "cd"));
        assertFalse(PasswordHasher.matches("secret-1", "not-hex!!!", "cd"));
        assertFalse(PasswordHasher.matches("secret-1", null, null));
    }
}
