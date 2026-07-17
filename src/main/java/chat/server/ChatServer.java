package chat.server;

import chat.server.ai.OllamaClient;
import chat.server.auth.AuthService;
import chat.server.auth.TokenService;
import chat.server.auth.UserStore;
import chat.server.rooms.RoomManager;
import chat.server.sessions.SessionManager;

public class ChatServer {
    private final TokenService tokenService = new TokenService();
    private final UserStore userStore = new UserStore();

    private final AuthService authService = new AuthService(userStore);
    private final SessionManager sessionManager = new SessionManager(tokenService);
    private final RoomManager roomManager = new RoomManager(new OllamaClient("http://localhost:11434"));

    public ChatServer() {
        // A lightweight virtual thread periodically removes expired disconnected sessions.
        // This keeps recoverable sessions alive for RESUME while preventing stale tokens from leaking memory forever.
        Thread.startVirtualThread(this::sessionCleanupLoop);
    }

    private void sessionCleanupLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(30_000);
                sessionManager.cleanupExpiredSessions();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                System.err.println("[server] Error during session cleanup: " + e.getMessage());
            }
        }
    }

    public AuthService authService() {
        return authService;
    }

    public SessionManager sessionManager() {
        return sessionManager;
    }

    public RoomManager roomManager() {
        return roomManager;
    }
}
