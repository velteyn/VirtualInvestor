package com.virtualinvestor.runtime;

import com.virtualinvestor.core.command.Command;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * The only cross-thread touchpoint between the edge and the world (design D2). Client commands
 * are enqueued by the edge and drained by the loop at the top of each tick, so the world stays
 * single-writer and deterministic.
 */
public final class CommandQueue {

    private final ConcurrentLinkedQueue<Command> queue = new ConcurrentLinkedQueue<>();

    public void enqueue(Command command) {
        queue.add(Objects.requireNonNull(command, "command"));
    }

    /** Remove and return everything currently queued, in enqueue order. */
    public List<Command> drain() {
        List<Command> drained = new ArrayList<>();
        Command command;
        while ((command = queue.poll()) != null) {
            drained.add(command);
        }
        return drained;
    }

    public int size() {
        return queue.size();
    }
}
