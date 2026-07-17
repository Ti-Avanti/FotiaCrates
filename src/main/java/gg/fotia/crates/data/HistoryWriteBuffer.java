package gg.fotia.crates.data;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Bounded FIFO buffer that keeps history persistence off the server thread.
 */
public final class HistoryWriteBuffer {

    private final int capacity;
    private final ArrayDeque<Record> records = new ArrayDeque<>();

    public HistoryWriteBuffer(int capacity) {
        this.capacity = Math.max(1, capacity);
    }

    public synchronized boolean offer(Record record) {
        if (records.size() >= capacity) {
            return false;
        }
        records.addLast(record);
        return true;
    }

    public synchronized List<Record> drain(int maxRecords) {
        int amount = Math.max(0, Math.min(maxRecords, records.size()));
        List<Record> batch = new ArrayList<>(amount);
        for (int index = 0; index < amount; index++) {
            batch.add(records.removeFirst());
        }
        return batch;
    }

    public synchronized List<Record> drainAll() {
        List<Record> batch = new ArrayList<>(records);
        records.clear();
        return batch;
    }

    public synchronized int requeueFront(List<Record> retryRecords) {
        int droppedNewest = 0;
        for (int index = retryRecords.size() - 1; index >= 0; index--) {
            if (records.size() >= capacity) {
                records.removeLast();
                droppedNewest++;
            }
            records.addFirst(retryRecords.get(index));
        }
        return droppedNewest;
    }

    public synchronized boolean isEmpty() {
        return records.isEmpty();
    }

    public synchronized int size() {
        return records.size();
    }

    public record Record(UUID playerId, String playerName, String crateId, String rewardId,
                         String rewardName, long timestamp) {
    }
}
