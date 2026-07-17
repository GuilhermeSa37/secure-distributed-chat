package chat.server.auth;

import chat.server.safeStorage.SafeMap;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

public class UserStore {
    private static final int PBKDF2_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int HASH_BITS = 256;
    private static final Path USERS_FILE = Path.of("data/users.txt");

    private final SafeMap<String, UserRecord> users = new SafeMap<>();
    private final SecureRandom random = new SecureRandom();

    public UserStore() {
        loadUsers();
        ensureDefaultUser("alice", "alice");
        ensureDefaultUser("bob", "bob");
        ensureDefaultUser("eve", "eve");
    }

    public boolean register(String username, String password) {
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            return false;
        }

        UserRecord record = createRecord(username, password);
        Optional<UserRecord> existing = users.putIfAbsent(username, record);
        if (existing.isPresent()) {
            return false;
        }

        appendUser(record);
        return true;
    }

    public Optional<UserRecord> find(String username) {
        return users.get(username);
    }

    public boolean verifyPassword(String username, String password) {
        return find(username)
                .map(user -> verify(password, user))
                .orElse(false);
    }

    private void ensureDefaultUser(String username, String password) {
        if (users.get(username).isEmpty()) {
            register(username, password);
        }
    }

    private void loadUsers() {
        if (!Files.exists(USERS_FILE)) {
            return;
        }

        try {
            List<String> lines = Files.readAllLines(USERS_FILE, StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                try {
                    UserRecord record = UserRecord.fromStorageLine(line.trim());
                    users.putIfAbsent(record.username(), record);
                } catch (RuntimeException ignored) {
                    System.err.println("Ignoring invalid user record in " + USERS_FILE + ": " + line);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Could not load users from " + USERS_FILE.toAbsolutePath(), e);
        }
    }

    private synchronized void appendUser(UserRecord record) {
        try {
            Files.createDirectories(USERS_FILE.getParent());
            Files.writeString(
                    USERS_FILE,
                    record.toStorageLine() + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    Files.exists(USERS_FILE)
                            ? new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.APPEND}
                            : new java.nio.file.OpenOption[]{java.nio.file.StandardOpenOption.CREATE}
            );
        } catch (IOException e) {
            throw new IllegalStateException("Could not persist user " + record.username(), e);
        }
    }

    private UserRecord createRecord(String username, String password) {
        byte[] salt = new byte[SALT_BYTES];
        random.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt, PBKDF2_ITERATIONS);
        return new UserRecord(
                username,
                PBKDF2_ITERATIONS,
                Base64.getEncoder().encodeToString(salt),
                Base64.getEncoder().encodeToString(hash)
        );
    }

    private boolean verify(String password, UserRecord record) {
        byte[] salt = Base64.getDecoder().decode(record.saltBase64());
        byte[] expected = Base64.getDecoder().decode(record.passwordHashBase64());
        byte[] actual = pbkdf2(password, salt, record.iterations());
        return MessageDigest.isEqual(expected, actual);
    }

    private static byte[] pbkdf2(String password, byte[] salt, int iterations) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, iterations, HASH_BITS);
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable", e);
        }
    }
}
