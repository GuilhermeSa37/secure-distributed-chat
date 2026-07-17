package chat.server.protocol;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ProtocolParserTest {
    private final ProtocolParser parser = new ProtocolParser();

    @Test
    void parsesCommandNameCaseInsensitively() {
        Command command = parser.parse("  login alice secret  ");

        assertEquals("LOGIN", command.name());
        assertEquals(CommandType.LOGIN, command.type());
        assertEquals(List.of("alice", "secret"), command.args());
    }

    @Test
    void preservesMessageTail() {
        Command command = parser.parse("MSG hello from the room");

        assertEquals(CommandType.MSG, command.type());
        assertEquals("hello from the room", command.rawTail());
    }

    @Test
    void returnsUnknownForUnsupportedCommands() {
        assertEquals(CommandType.UNKNOWN, parser.parse("DANCE").type());
    }

    @Test
    void handlesBlankInput() {
        Command command = parser.parse("   ");

        assertEquals("", command.name());
        assertEquals(List.of(), command.args());
        assertEquals(CommandType.UNKNOWN, command.type());
    }

    @Test
    void handlesNullInput() {
        assertEquals(CommandType.UNKNOWN, parser.parse(null).type());
    }
}
