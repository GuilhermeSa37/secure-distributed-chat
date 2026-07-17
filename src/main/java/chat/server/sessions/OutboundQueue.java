package chat.server.sessions;

import java.util.ArrayDeque;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class OutboundQueue {
    // Custom bounded queue implemented with locks/conditions instead of java.util.concurrent collections.
    // Explicit lock-based implementation used to make queue synchronization and overflow behavior visible.
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();

    private final ArrayDeque<String> queue = new ArrayDeque<>();
    private final int capacity;

    public OutboundQueue(int capacity) {
        this.capacity = capacity;
    }

    public void offer(String message) {
        // Called by rooms/server events. Signal wakes the writer thread if it is waiting.
        lock.lock();
        try {
            if (queue.size() >= capacity) {
                // Drop oldest message instead of letting one slow client grow memory forever.
                queue.removeFirst();
            }

            queue.addLast(message);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public void offerFirst(String message) {
        lock.lock();
        try {
            if (queue.size() >= capacity) {
                queue.removeLast();
            }
            queue.addFirst(message);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    public String take() throws InterruptedException {
        lock.lock();
        try {
            while (queue.isEmpty()) {
                notEmpty.await();
            }

            return queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }

    public String poll(long timeout, java.util.concurrent.TimeUnit unit) throws InterruptedException {
        // Used by the writer thread. It sleeps while empty and returns null on timeout,
        // which lets the writer send a heartbeat PING.
        lock.lock();
        try {
            long nanos = unit.toNanos(timeout);
            while (queue.isEmpty()) {
                if (nanos <= 0) return null;
                nanos = notEmpty.awaitNanos(nanos);
            }
            return queue.removeFirst();
        } finally {
            lock.unlock();
        }
    }
}
