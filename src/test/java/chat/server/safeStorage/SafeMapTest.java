package chat.server.safeStorage;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SafeMapTest {
    @Test
    void putIfAbsentDoesNotReplaceAnExistingValue() {
        SafeMap<String, Integer> map = new SafeMap<>();

        assertTrue(map.putIfAbsent("room", 1).isEmpty());
        assertEquals(1, map.putIfAbsent("room", 2).orElseThrow());
        assertEquals(1, map.get("room").orElseThrow());
    }

    @Test
    void computeIfAbsentCreatesOnlyTheMissingValue() {
        SafeMap<String, Integer> map = new SafeMap<>();

        assertEquals(3, map.computeIfAbsent("key", () -> 3));
        assertEquals(3, map.computeIfAbsent("key", () -> 4));
    }
}
