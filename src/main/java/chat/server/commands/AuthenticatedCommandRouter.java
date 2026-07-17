package chat.server.commands;

import chat.server.ChatServer;
import chat.server.ConnectionContext;
import chat.server.protocol.Command;
import chat.server.protocol.CommandType;
import chat.server.rooms.AIRoom;
import chat.server.rooms.Room;
import chat.server.sessions.ClientSession;

import java.io.IOException;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

public class AuthenticatedCommandRouter {
    private final ChatServer server;
    private final Map<CommandType, AuthenticatedCommandHandler> handlers = new EnumMap<>(CommandType.class);

    public AuthenticatedCommandRouter(ChatServer server) {
        this.server = server;

        handlers.put(CommandType.LIST_ROOMS, this::listRooms);
        handlers.put(CommandType.JOIN, this::joinRoom);
        handlers.put(CommandType.CREATE_AI_ROOM, this::createAiRoom);
        handlers.put(CommandType.MSG, this::sendMessage);
        handlers.put(CommandType.LEAVE, this::leaveRoom);
        handlers.put(CommandType.HELP, this::help);
        handlers.put(CommandType.LOGOUT, this::logout);
        handlers.put(CommandType.AI, this::triggerAi);
        handlers.put(CommandType.QUIT, this::quit);
    }

    public boolean dispatch(ClientSession session, ConnectionContext connection, Command command) throws IOException {
        AuthenticatedCommandHandler handler = handlers.get(command.type());
        if (handler == null) {
            connection.writeLine("ERR INVALID_COMMAND Unknown command. Use HELP to list commands.");
            return true;
        }

        return handler.handle(session, connection, command);
    }

    private boolean listRooms(ClientSession session, ConnectionContext connection, Command command) {
        connection.writeLine("OK ROOMS " + String.join(",", server.roomManager().listRoomNames()));
        return true;
    }

    private boolean joinRoom(ClientSession session, ConnectionContext connection, Command command) {
        if (command.rawTail().isBlank()) {
            connection.writeLine("ERR BAD_ARGUMENTS Usage: JOIN roomName");
            return true;
        }

        session.currentRoom().ifPresent(room -> room.leave(session));
        Room room = server.roomManager().getOrCreateNormalRoom(command.rawTail().trim());
        room.join(session);
        return true;
    }

    private boolean createAiRoom(ClientSession session, ConnectionContext connection, Command command) {
        String[] parts = command.rawTail().split("\\|", 2);
        if (parts.length < 2 || parts[0].isBlank() || parts[1].isBlank()) {
            connection.writeLine("ERR BAD_ARGUMENTS Usage: CREATE_AI_ROOM roomName | prompt");
            return true;
        }

        String roomName = parts[0].trim();
        String prompt = parts[1].trim();

        boolean created = server.roomManager().createAIRoom(roomName, prompt);
        connection.writeLine(created ? "OK AI_ROOM_CREATED " + roomName : "ERR ROOM_ALREADY_EXISTS Room already exists");
        return true;
    }

    private boolean sendMessage(ClientSession session, ConnectionContext connection, Command command) {
        if (command.rawTail().isBlank()) {
            connection.writeLine("ERR BAD_ARGUMENTS Usage: MSG text");
            return true;
        }

        Optional<Room> room = session.currentRoom();
        if (room.isEmpty()) {
            connection.writeLine("ERR ROOM_REQUIRED Join a room first");
            return true;
        }

        room.get().postUserMessage(session, command.rawTail());
        return true;
    }

    private boolean leaveRoom(ClientSession session, ConnectionContext connection, Command command) {
        Optional<Room> currentRoom = session.currentRoom();
        if (currentRoom.isEmpty()) {
            connection.writeLine("ERR ROOM_REQUIRED You are not in a room");
            return true;
        }

        currentRoom.get().leave(session);
        connection.writeLine("OK LEFT");
        return true;
    }

    private boolean help(ClientSession session, ConnectionContext connection, Command command) {
        connection.writeLine("OK HELP");
        connection.writeLine("LIST_ROOMS - List available rooms");
        connection.writeLine("JOIN room - Enter an existing room or create a normal room if it does not exist");
        connection.writeLine("CREATE_AI_ROOM room | prompt - Create an AI room with an instruction prompt");
        connection.writeLine("MSG text - Send a message to the current room");
        connection.writeLine("AI - Manually request a Bot response when inside an AI room");
        connection.writeLine("LEAVE - Leave the current room");
        connection.writeLine("LOGOUT - Invalidate the current token and end the session");
        connection.writeLine("QUIT - Close the client connection");
        return true;
    }

    private boolean triggerAi(ClientSession session, ConnectionContext connection, Command command) {
        Optional<Room> room = session.currentRoom();
        if (room.isEmpty()) {
            connection.writeLine("ERR ROOM_REQUIRED You are not in a room");
            return true;
        }

        if (room.get() instanceof AIRoom aiRoom) {
            String prompt = command.rawTail();
            connection.writeLine("OK AI Generating AI response...");
            if(prompt.isEmpty()) {
                aiRoom.triggerAI();
            } else {
                aiRoom.triggerAI(prompt);
            }
            return true;
        }

        connection.writeLine("ERR ROOM_TYPE_MISMATCH You need to be in an AI room");
        return true;
    }

    private boolean logout(ClientSession session, ConnectionContext connection, Command command) {
        connection.writeLine("OK LOGOUT");
        server.sessionManager().invalidate(session.token());
        connection.close();
        return false;
    }

    private boolean quit(ClientSession session, ConnectionContext connection, Command command) {
        connection.writeLine("OK BYE");
        connection.close();
        return false;
    }

    @FunctionalInterface
    private interface AuthenticatedCommandHandler {
        boolean handle(ClientSession session, ConnectionContext connection, Command command) throws IOException;
    }
}
