package chat.server.auth;

import chat.common.Constants;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

public class TokenService {
    private final SecureRandom random = new SecureRandom();

    public String createToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public Instant expiryTime() {
        return Instant.now().plusSeconds(Constants.TOKEN_TTL_SECONDS);
    }

    public boolean isExpired(Instant expiry) {
        return Instant.now().isAfter(expiry);
    }
}
