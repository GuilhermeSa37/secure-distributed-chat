package chat.server.protocol;

import java.util.List;

public record Command(String name, List<String> args, String rawTail) {
    public CommandType type() {
        return CommandType.fromWireName(name);
    }
}
