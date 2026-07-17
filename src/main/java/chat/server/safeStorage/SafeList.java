package chat.server.safeStorage;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

public class SafeList<T> {
    private final List<T> list = new ArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();

    public void add(T value) {
        lock.lock();
        try {
            list.add(value);
        } finally {
            lock.unlock();
        }
    }

    public List<T> snapshot() {
        lock.lock();
        try {
            return new ArrayList<>(list);
        } finally {
            lock.unlock();
        }
    }

    public int size() {
        lock.lock();
        try {
            return list.size();
        } finally {
            lock.unlock();
        }
    }
}
