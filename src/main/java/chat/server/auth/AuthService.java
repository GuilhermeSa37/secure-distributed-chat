package chat.server.auth;

public class AuthService {
    private final UserStore userStore;

    public AuthService(UserStore userStore) {
        this.userStore = userStore;
    }

    public AuthResult login(String username, String password) {
        if (userStore.verifyPassword(username, password)) {
            return AuthResult.ok(username);
        }

        return AuthResult.error("Invalid username or password");
    }

    public AuthResult register(String username, String password) {
        boolean created = userStore.register(username, password);
        if (created) {
            return AuthResult.ok(username);
        }

        return AuthResult.error("Username already exists");
    }
}
