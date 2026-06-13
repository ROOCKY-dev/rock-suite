package dev.rock.web.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import dev.rock.api.annotations.RockInternal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Minimal JWT (HS256) — issue/verify with expiry and access/refresh token
 * types (TRS §9: tokens must expire, refresh tokens supported). JDK-crypto
 * only; no JWT library. Signature compared in constant time.
 */
@RockInternal
public final class JwtCodec {

    public enum TokenType { ACCESS, REFRESH }

    public record Claims(String subject, String role, TokenType type, Instant expiresAt) {
    }

    private static final Base64.Encoder B64 = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64D = Base64.getUrlDecoder();
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String HEADER = B64.encodeToString(
            "{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

    private final byte[] secret;

    public JwtCodec(String secret) {
        if (secret == null || secret.length() < 16) {
            throw new IllegalArgumentException("JWT secret must be at least 16 chars");
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    public String issue(String subject, String role, TokenType type, Duration ttl) {
        Instant exp = Instant.now().plus(ttl);
        ObjectNode payload = JSON.createObjectNode();
        payload.put("sub", subject);
        payload.put("role", role);
        payload.put("typ", type.name());
        payload.put("iat", Instant.now().getEpochSecond());
        payload.put("exp", exp.getEpochSecond());
        String body = HEADER + "." + B64.encodeToString(toBytes(payload));
        return body + "." + sign(body);
    }

    /** Verifies signature, expiry, and (optionally) the required token type. */
    public Optional<Claims> verify(String token, TokenType required) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        String body = parts[0] + "." + parts[1];
        if (!constantTimeEquals(parts[2], sign(body))) {
            return Optional.empty();
        }
        try {
            JsonNode payload = JSON.readTree(B64D.decode(parts[1]));
            Instant exp = Instant.ofEpochSecond(payload.get("exp").asLong());
            if (exp.isBefore(Instant.now())) {
                return Optional.empty();
            }
            TokenType type = TokenType.valueOf(payload.get("typ").asText());
            if (required != null && type != required) {
                return Optional.empty();
            }
            return Optional.of(new Claims(
                    payload.get("sub").asText(), payload.get("role").asText(), type, exp));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return B64.encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) {
            throw new IllegalStateException("JWT signing failed", e);
        }
    }

    private static byte[] toBytes(ObjectNode node) {
        try {
            return JSON.writeValueAsBytes(node);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean constantTimeEquals(String a, String b) {
        return MessageDigest.isEqual(
                a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
    }
}
