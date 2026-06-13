package dev.rock.web.auth;

import com.password4j.Password;
import com.password4j.types.Argon2;
import dev.rock.api.annotations.RockInternal;

/**
 * Argon2id password hashing (TRS §9 — passwords SHALL NEVER be plaintext;
 * required algorithm Argon2id). Parameters chosen for an interactive login on
 * modest server hardware (TRS §3 4-core/8GB target): 64 MB memory, 3 iterations,
 * parallelism 1. The encoded hash carries its own parameters, so verification
 * stays correct if parameters are tuned later.
 */
@RockInternal
public final class PasswordHasher {

    private static final int MEMORY_KIB = 65_536; // 64 MB
    private static final int ITERATIONS = 3;
    private static final int PARALLELISM = 1;
    private static final int HASH_LENGTH = 32;

    private static final com.password4j.Argon2Function ARGON2 =
            com.password4j.Argon2Function.getInstance(MEMORY_KIB, ITERATIONS, PARALLELISM, HASH_LENGTH, Argon2.ID);

    public String hash(char[] password) {
        try {
            // The encoded PHC string ($argon2id$v=19$m=...,t=...,p=...$salt$hash)
            // carries its own parameters, so verify() works across param tuning.
            return Password.hash(new String(password)).addRandomSalt(16).with(ARGON2).getResult();
        } finally {
            java.util.Arrays.fill(password, '\0');
        }
    }

    public boolean verify(String password, String encodedHash) {
        return Password.check(password, encodedHash).with(ARGON2);
    }
}
