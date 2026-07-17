package chat.server.sessions;

import org.junit.jupiter.api.Test;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class OutboundQueueTest {
    @Test
    void dropsOldestMessageWhenCapacityIsReached() throws InterruptedException {
        OutboundQueue queue = new OutboundQueue(2);
        queue.offer("first");
        queue.offer("second");
        queue.offer("third");

        assertEquals("second", queue.poll(10, TimeUnit.MILLISECONDS));
        assertEquals("third", queue.poll(10, TimeUnit.MILLISECONDS));
    }

    @Test
    void offerFirstKeepsQueueBounded() throws InterruptedException {
        OutboundQueue queue = new OutboundQueue(2);
        queue.offer("first");
        queue.offer("second");
        queue.offerFirst("priority");

        assertEquals("priority", queue.poll(10, TimeUnit.MILLISECONDS));
        assertEquals("first", queue.poll(10, TimeUnit.MILLISECONDS));
        assertNull(queue.poll(1, TimeUnit.MILLISECONDS));
    }

    @Test
    void pollTimesOutWhenQueueIsEmpty() throws InterruptedException {
        OutboundQueue queue = new OutboundQueue(1);

        assertNull(queue.poll(1, TimeUnit.MILLISECONDS));
    }
}
