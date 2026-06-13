package dev.rock.web.auth;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher();

    @Test
    void hashIsArgon2idAndVerifies() {
        String hash = hasher.hash("correct horse battery staple".toCharArray());

        assertTrue(hash.startsWith("$argon2id$"), "Argon2id encoded hash (TRS §9)");
        assertTrue(hasher.verify("correct horse battery staple", hash));
        assertFalse(hasher.verify("wrong password", hash));
    }

    @Test
    void saltMakesHashesUnique() {
        String a = hasher.hash("same".toCharArray());
        String b = hasher.hash("same".toCharArray());

        assertNotEquals(a, b, "random salt → different hashes for the same password");
        assertTrue(hasher.verify("same", a));
        assertTrue(hasher.verify("same", b));
    }
}
