package dev.rock.web.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.rock.web.auth.JwtCodec.TokenType;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class JwtCodecTest {

    private final JwtCodec jwt = new JwtCodec("a-sufficiently-long-test-secret-key");

    @Test
    void issuedTokenVerifiesWithClaims() {
        String token = jwt.issue("alice", "ADMIN", TokenType.ACCESS, Duration.ofMinutes(15));

        var claims = jwt.verify(token, TokenType.ACCESS).orElseThrow();
        assertEquals("alice", claims.subject());
        assertEquals("ADMIN", claims.role());
        assertEquals(TokenType.ACCESS, claims.type());
    }

    @Test
    void wrongTokenTypeIsRejected() {
        String refresh = jwt.issue("alice", "USER", TokenType.REFRESH, Duration.ofDays(7));

        assertTrue(jwt.verify(refresh, TokenType.ACCESS).isEmpty(), "a refresh token is not an access token");
        assertTrue(jwt.verify(refresh, TokenType.REFRESH).isPresent());
    }

    @Test
    void expiredTokenIsRejected() {
        String token = jwt.issue("alice", "USER", TokenType.ACCESS, Duration.ofSeconds(-1));

        assertTrue(jwt.verify(token, TokenType.ACCESS).isEmpty());
    }

    @Test
    void tamperedSignatureIsRejected() {
        String token = jwt.issue("alice", "USER", TokenType.ACCESS, Duration.ofMinutes(15));
        String tampered = token.substring(0, token.length() - 2) + "xy";

        assertTrue(jwt.verify(tampered, TokenType.ACCESS).isEmpty());
    }

    @Test
    void aDifferentSecretCannotVerify() {
        String token = jwt.issue("alice", "USER", TokenType.ACCESS, Duration.ofMinutes(15));
        JwtCodec attacker = new JwtCodec("a-totally-different-secret-key-value");

        assertTrue(attacker.verify(token, TokenType.ACCESS).isEmpty());
    }

    @Test
    void shortSecretRejected() {
        assertThrows(IllegalArgumentException.class, () -> new JwtCodec("tooshort"));
    }
}
