package chat.server.safeStorage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public class SafeMap<K, V> {
    private final Map<K, V> map = new HashMap<>();
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    public Optional<V> get(K key) {
        lock.readLock().lock();
        try {
            return Optional.ofNullable(map.get(key));
        } finally {
            lock.readLock().unlock();
        }
    }

    public void put(K key, V value) {
        lock.writeLock().lock();
        try {
            map.put(key, value);
        } finally {
            lock.writeLock().unlock();
        }
    }


    public Optional<V> putIfAbsent(K key, V value) {
        lock.writeLock().lock();
        try {
            V existing = map.get(key);
            if (existing == null) {
                map.put(key, value);
                return Optional.empty();
            }
            return Optional.of(existing);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public boolean containsKey(K key) {
        lock.readLock().lock();
        try {
            return map.containsKey(key);
        } finally {
            lock.readLock().unlock();
        }
    }

    public V computeIfAbsent(K key, Supplier<V> supplier) {
        lock.writeLock().lock();
        try {
            V value = map.get(key);
            if (value == null) {
                value = supplier.get();
                map.put(key, value);
            }
            return value;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<K> keysSnapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(map.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<V> valuesSnapshot() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(map.values());
        } finally {
            lock.readLock().unlock();
        }
    }

    public Optional<V> remove(K key) {
        lock.writeLock().lock();
        try {
            return Optional.ofNullable(map.remove(key));
        } finally {
            lock.writeLock().unlock();
        }
    }
}
