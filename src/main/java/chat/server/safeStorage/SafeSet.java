package chat.server.safeStorage;

import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;

public class SafeSet<T> {
    private final Set<T> set = new HashSet<>();
    private final ReentrantLock lock = new ReentrantLock();

    public boolean add(T value) {
        lock.lock();
        try {
            return set.add(value);
        } finally {
            lock.unlock();
        }
    }

    public boolean remove(T value) {
        lock.lock();
        try {
            return set.remove(value);
        } finally {
            lock.unlock();
        }
    }

    public Set<T> snapshot() {
        lock.lock();
        try {
            return new HashSet<>(set);
        } finally {
            lock.unlock();
        }
    }
}
