package com.virtualinvestor.persist.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.event.GameEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

/**
 * The writer is shared between the world's driver thread (appends) and HTTP
 * threads (a fill-history or markouts query flushes before reading). A flush
 * racing an append must never write one sequence twice — the log refuses
 * duplicates, which used to surface as 500s on the history query.
 */
class BatchedEventLogWriterTest {

    /** In-memory log with the same duplicate refusal as the real primary key. */
    private static final class MemoryLog implements EventLog {
        private final List<SequencedEvent> rows = new ArrayList<>();

        @Override
        public synchronized void appendBatch(List<SequencedEvent> events) {
            for (SequencedEvent event : events) {
                for (SequencedEvent row : rows) {
                    if (row.seq() == event.seq()) {
                        throw new IllegalStateException("duplicate seq " + event.seq());
                    }
                }
            }
            rows.addAll(events);
        }

        @Override
        public synchronized long latestSeq() {
            return rows.stream().mapToLong(SequencedEvent::seq).max().orElse(0L);
        }

        @Override
        public synchronized List<SequencedEvent> readFrom(long fromSeq) {
            return rows.stream().filter(row -> row.seq() >= fromSeq).toList();
        }
    }

    private static GameEvent.Tick tick(long tick) {
        return new GameEvent.Tick(tick, tick * 100L);
    }

    @Test
    void concurrentAppendsAndFlushesNeverDuplicateSequences() throws Exception {
        MemoryLog log = new MemoryLog();
        BatchedEventLogWriter writer = new BatchedEventLogWriter(log, 100);

        int appenders = 4;
        int perAppender = 500;
        ExecutorService pool = Executors.newFixedThreadPool(appenders + 2);
        CountDownLatch ready = new CountDownLatch(appenders + 2);
        CountDownLatch go = new CountDownLatch(1);
        for (int a = 0; a < appenders; a++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perAppender; i++) {
                    writer.append(tick(i));
                }
                return null;
            });
        }
        // Two flushers, like the checkpoint cadence and a history query racing it.
        for (int f = 0; f < 2; f++) {
            pool.submit(() -> {
                ready.countDown();
                try {
                    go.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                for (int i = 0; i < perAppender; i++) {
                    writer.flush();
                }
                return null;
            });
        }
        assertTrue(ready.await(10L, TimeUnit.SECONDS));
        go.countDown();
        pool.shutdown();
        assertTrue(pool.awaitTermination(30L, TimeUnit.SECONDS));
        writer.flush();

        List<SequencedEvent> rows = log.readFrom(1L);
        assertEquals(appenders * perAppender, rows.size(), "every appended event lands exactly once");
        Set<Long> seqs = new HashSet<>();
        for (SequencedEvent row : rows) {
            seqs.add(row.seq());
        }
        assertEquals(rows.size(), seqs.size(), "sequence numbers are unique");
        for (long seq = 1L; seq <= rows.size(); seq++) {
            assertTrue(seqs.contains(seq), "sequence " + seq + " is present");
        }
    }
}
