package chat.server.auth;

public record AuthResult(boolean success, String username, String message) {
    public static AuthResult ok(String username) {
        return new AuthResult(true, username, "OK");
    }

    public static AuthResult error(String message) {
        return new AuthResult(false, null, message);
    }
}
