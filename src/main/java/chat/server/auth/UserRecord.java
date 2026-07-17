package chat.server.auth;

public record UserRecord(String username, int iterations, String saltBase64, String passwordHashBase64) {
    public String toStorageLine() {
        return username + ":" + iterations + ":" + saltBase64 + ":" + passwordHashBase64;
    }

    public static UserRecord fromStorageLine(String line) {
        String[] parts = line.split(":", 4);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Invalid user storage line");
        }
        return new UserRecord(parts[0], Integer.parseInt(parts[1]), parts[2], parts[3]);
    }
}
