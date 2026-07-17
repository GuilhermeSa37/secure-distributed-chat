package chat.server.protocol;

import java.util.Arrays;
import java.util.List;

public class ProtocolParser {
    public Command parse(String line) {
        String trimmed = line == null ? "" : line.trim();

        if (trimmed.isEmpty()) {
            return new Command("", List.of(), "");
        }

        String[] parts = trimmed.split("\\s+", 2);
        String name = parts[0].toUpperCase();
        String tail = parts.length > 1 ? parts[1] : "";

        List<String> args = tail.isEmpty()
                ? List.of()
                : Arrays.asList(tail.split("\\s+"));

        return new Command(name, args, tail);
    }
}
