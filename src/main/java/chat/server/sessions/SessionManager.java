package chat.server.sessions;

import chat.server.auth.TokenService;
import chat.server.safeStorage.SafeMap;

import java.util.Optional;

public class SessionManager {
    private final SafeMap<String, ClientSession> sessionsByToken = new SafeMap<>();
    private final TokenService tokenService;

    public SessionManager(TokenService tokenService) {
        this.tokenService = tokenService;
    }

    public ClientSession createSession(String username) {
        String token = tokenService.createToken();
        ClientSession session = new ClientSession(username, token, tokenService.expiryTime());
        sessionsByToken.put(token, session);
        return session;
    }

    public Optional<ClientSession> resume(String token) {
        Optional<ClientSession> session = sessionsByToken.get(token);

        if (session.isEmpty()) {
            return Optional.empty();
        }

        ClientSession value = session.get();
        if (tokenService.isExpired(value.tokenExpiresAt())) {
            invalidate(token);
            return Optional.empty();
        }

        value.refreshTokenExpiry(tokenService.expiryTime());
        return Optional.of(value);
    }

    public void invalidate(String token) {
        sessionsByToken.remove(token).ifPresent(session -> {
            session.currentRoom().ifPresent(room -> room.leave(session));
            session.connection().ifPresent(connection -> {
                try {
                    connection.close();
                } catch (Exception ignored) {
                }
            });
        });
    }

    public boolean containsToken(String token) {
        return sessionsByToken.get(token).isPresent();
    }

    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        long maxIdleMillis = chat.common.Constants.TOKEN_TTL_SECONDS * 1000;

        for (ClientSession session : sessionsByToken.valuesSnapshot()) {
            boolean tokenExpired = tokenService.isExpired(session.tokenExpiresAt());
            boolean disconnectedExpired = !session.isConnected()
                    && session.getDisconnectedTimestamp() > 0
                    && now - session.getDisconnectedTimestamp() > maxIdleMillis;

            if (tokenExpired || disconnectedExpired) {
                System.out.println("[server] Session expired and removed: " + session.username());
                invalidate(session.token());
            }
        }
    }
}
