package chat.server.protocol;

import java.util.Locale;

public enum CommandType {
    LOGIN,
    REGISTER,
    RESUME,
    LIST_ROOMS,
    JOIN,
    CREATE_AI_ROOM,
    MSG,
    LEAVE,
    HELP,
    LOGOUT,
    QUIT,
    PONG,
    AI,
    UNKNOWN;

    public static CommandType fromWireName(String name) {
        try {
            return CommandType.valueOf(name.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return UNKNOWN;
        }
    }
}
