package com.virtualinvestor.persist.event;

import java.util.List;

/**
 * The append-only event log for one round: the durable source of truth. There is deliberately
 * no update or delete operation.
 */
public interface EventLog {

    /** Append a batch of already-sequenced events. Duplicate sequence numbers are refused. */
    void appendBatch(List<SequencedEvent> events);

    /** The highest sequence number stored, or 0 when the log is empty. */
    long latestSeq();

    /** Read events with sequence number at or after {@code fromSeq}, in order. */
    List<SequencedEvent> readFrom(long fromSeq);
}
