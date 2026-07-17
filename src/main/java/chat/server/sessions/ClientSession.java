package chat.server.sessions;

import chat.common.Constants;
import chat.server.ConnectionContext;
import chat.server.rooms.Room;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantLock;

public class ClientSession {
    private final String username;
    private final String token;
    private final OutboundQueue outboundQueue = new OutboundQueue(Constants.OUTBOUND_QUEUE_CAPACITY);
    private final ReentrantLock lock = new ReentrantLock();
    private volatile boolean isConnected = true;
    private volatile long disconnectedTimestamp = 0;

    private Instant tokenExpiresAt;
    private ConnectionContext connection;
    private Room currentRoom;
    private Thread writerThread;

    public ClientSession(String username, String token, Instant tokenExpiresAt) {
        this.username = username;
        this.token = token;
        this.tokenExpiresAt = tokenExpiresAt;
    }

    public String username() {
        return username;
    }

    public String token() {
        return token;
    }

    public Instant tokenExpiresAt() {
        lock.lock();
        try {
            return tokenExpiresAt;
        } finally {
            lock.unlock();
        }
    }

    public void refreshTokenExpiry(Instant expiry) {
        lock.lock();
        try {
            this.tokenExpiresAt = expiry;
        } finally {
            lock.unlock();
        }
    }

    public void bindConnection(ConnectionContext newConnection, Thread newWriterThread) {
        // A session survives broken sockets. On RESUME, this method attaches the new
        // connection and interrupts the old writer so only the latest socket is used.
        lock.lock();
        try {
            if (this.connection != null) {
                try {
                    this.connection.close();
                } catch (Exception ignored) {
                }
            }

            if (this.writerThread != null) {
                this.writerThread.interrupt();
            }

            this.connection = newConnection;
            this.writerThread = newWriterThread;
        } finally {
            lock.unlock();
        }

        if (!this.isConnected) {
            this.outboundQueue.offer("[Server] --- You're back online!");
        }

        this.isConnected = true;
        this.disconnectedTimestamp = 0;
    }

    public void prependMessage(String line) {
        outboundQueue.offerFirst(line);
    }

    public Optional<ConnectionContext> connection() {
        lock.lock();
        try {
            return Optional.ofNullable(connection);
        } finally {
            lock.unlock();
        }
    }

    public Optional<Room> currentRoom() {
        lock.lock();
        try {
            return Optional.ofNullable(currentRoom);
        } finally {
            lock.unlock();
        }
    }

    public void setCurrentRoom(Room room) {
        lock.lock();
        try {
            this.currentRoom = room;
        } finally {
            lock.unlock();
        }
    }

    public void clearCurrentRoom(Room room) {
        lock.lock();
        try {
            if (this.currentRoom == room) {
                this.currentRoom = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public void send(String line) {
        // Rooms never write directly to sockets. They enqueue messages here, and the
        // session writer thread later drains the queue. This isolates slow clients.
        if (!isConnected) {
            System.out.println("[server] " + username + " is offline. Storing the message in the buffer...");
        }
        outboundQueue.offer(line);
    }

    public String takeOutgoingMessage() throws InterruptedException {
        return outboundQueue.take();
    }

    public String takeOutgoingMessageWithTimeout(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        return outboundQueue.poll(timeout, unit);
    }

    public void markDisconnected() {
        // Mark the connection as temporarily unavailable while preserving the session
        // for token-based recovery until expiration/cleanup.
        this.isConnected = false;
        this.disconnectedTimestamp = System.currentTimeMillis();
        this.outboundQueue.offer("[Server] --- You're offline!");
    }

    public boolean isConnected() {
        return isConnected;
    }

    public long getDisconnectedTimestamp() {
        return disconnectedTimestamp;
    }
}
