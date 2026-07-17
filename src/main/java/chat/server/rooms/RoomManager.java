package chat.server.rooms;

import chat.server.ai.OllamaClient;
import chat.server.safeStorage.SafeMap;

import java.util.List;

public class RoomManager {
    private final SafeMap<String, Room> rooms = new SafeMap<>();
    private final OllamaClient ollamaClient;

    public RoomManager(OllamaClient ollamaClient) {
        this.ollamaClient = ollamaClient;
        rooms.put("General", new NormalRoom("General"));
        rooms.put("Library", new NormalRoom("Library"));
    }

    public Room getOrCreateNormalRoom(String name) {
        return rooms.computeIfAbsent(name, () -> new NormalRoom(name));
    }

    public boolean createAIRoom(String name, String prompt) {
        return rooms.putIfAbsent(name, new AIRoom(name, prompt, ollamaClient)).isEmpty();
    }

    public List<String> listRoomNames() {
        return rooms.keysSnapshot();
    }
}
