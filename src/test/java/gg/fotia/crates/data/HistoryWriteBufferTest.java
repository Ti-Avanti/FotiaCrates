package gg.fotia.crates.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HistoryWriteBufferTest {

    @Test
    void drainsAcceptedRecordsInFifoBatchesWithoutBlocking() {
        HistoryWriteBuffer buffer = new HistoryWriteBuffer(2);
        UUID playerId = UUID.randomUUID();
        HistoryWriteBuffer.Record first = new HistoryWriteBuffer.Record(playerId, "Player", "common", "first", "First", 1L);
        HistoryWriteBuffer.Record second = new HistoryWriteBuffer.Record(playerId, "Player", "common", "second", "Second", 2L);

        assertTrue(buffer.offer(first));
        assertTrue(buffer.offer(second));
        assertFalse(buffer.offer(new HistoryWriteBuffer.Record(playerId, "Player", "common", "third", "Third", 3L)));

        assertEquals(List.of(first), buffer.drain(1));
        assertEquals(List.of(second), buffer.drain(10));
        assertTrue(buffer.drain(1).isEmpty());
    }

    @Test
    void drainsAllQueuedRecordsForShutdownOrReload() {
        HistoryWriteBuffer buffer = new HistoryWriteBuffer(3);
        UUID playerId = UUID.randomUUID();
        HistoryWriteBuffer.Record first = new HistoryWriteBuffer.Record(playerId, "Player", "common", "first", "First", 1L);
        HistoryWriteBuffer.Record second = new HistoryWriteBuffer.Record(playerId, "Player", "common", "second", "Second", 2L);
        HistoryWriteBuffer.Record third = new HistoryWriteBuffer.Record(playerId, "Player", "common", "third", "Third", 3L);

        assertTrue(buffer.offer(first));
        assertTrue(buffer.offer(second));
        assertTrue(buffer.offer(third));

        assertEquals(List.of(first, second, third), buffer.drainAll());
        assertTrue(buffer.isEmpty());
    }
}
