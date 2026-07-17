package chat.server.rooms;

import chat.server.sessions.ClientSession;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public abstract class Room {
    private final String name;
    private final ReentrantLock lock = new ReentrantLock();

    private final List<Message> messages = new ArrayList<>();
    private final Set<ClientSession> members = new HashSet<>();

    protected Room(String name) {
        this.name = name;
    }

    public String name() {
        return name;
    }

    public void join(ClientSession session) {
        List<Message> history;

        lock.lock();
        try {
            members.add(session);
            history = new ArrayList<>(messages);
        } finally {
            lock.unlock();
        }

        session.setCurrentRoom(this);
        session.send("OK JOINED " + name);

        for (Message message : history) {
            session.send(message.format(name));
        }

        broadcastSystem(session.username() + " enters the room");
    }

    public void leave(ClientSession session) {
        boolean removed;

        lock.lock();
        try {
            removed = members.remove(session);
        } finally {
            lock.unlock();
        }

        if (removed) {
            session.clearCurrentRoom(this);
            broadcastSystem(session.username() + " leaves the room");
        }
    }

    public void postUserMessage(ClientSession sender, String text) {
        Message message = new Message(sender.username(), text, Instant.now());
        addAndBroadcast(message);
        afterUserMessage(message);
    }

    protected void postBotMessage(String text) {
        Message message = new Message("Bot", text, Instant.now());
        addAndBroadcast(message);
    }

    protected List<Message> messagesSnapshot() {
        lock.lock();
        try {
            return new ArrayList<>(messages);
        } finally {
            lock.unlock();
        }
    }

    protected List<Message> recentMessagesSnapshot(int limit) {
        lock.lock();
        try {
            int size = messages.size();
            int fromIndex = Math.max(0, size - limit);
            return new ArrayList<>(messages.subList(fromIndex, size));
        } finally {
            lock.unlock();
        }
    }

    private void addAndBroadcast(Message message) {
        List<ClientSession> recipients;

        lock.lock();
        try {
            messages.add(message);
            recipients = new ArrayList<>(members);
        } finally {
            lock.unlock();
        }

        for (ClientSession recipient : recipients) {
            recipient.send(message.format(name));
        }
    }

    private void broadcastSystem(String text) {
        Message message = new Message("System", "[" + text + "]", Instant.now());
        addAndBroadcast(message);
    }

    protected void afterUserMessage(Message message) {
        // Normal rooms do nothing. Subclasses may override this hook if they need post-message behavior.
    }
}
