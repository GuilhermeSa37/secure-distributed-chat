package chat.server.rooms;

import java.time.Instant;

public record Message(String author, String text, Instant timestamp) {
    public String format(String roomName) {
        return "MSG " + roomName + " " + author + ": " + text;
    }

    public String asContextLine() {
        return author + ": " + text;
    }
}
