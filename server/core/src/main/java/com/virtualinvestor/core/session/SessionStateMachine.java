package com.virtualinvestor.core.session;

import com.virtualinvestor.core.domain.SessionPhase;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Tracks the session phase of a round over the world clock. Call {@link #advance(long)} with
 * the current absolute tick; it returns any phase transitions that occurred since the last
 * call, in order, so the world can append the corresponding events.
 */
public final class SessionStateMachine {

    private final SessionSchedule schedule;
    private final long startTick;

    private long lastTick;
    private SessionPhase phase;

    public SessionStateMachine(SessionSchedule schedule, long startTick) {
        this.schedule = Objects.requireNonNull(schedule, "schedule");
        this.startTick = startTick;
        this.lastTick = startTick - 1L;
        this.phase = SessionPhase.PRE_OPEN;
    }

    public SessionSchedule schedule() {
        return schedule;
    }

    public SessionPhase phase() {
        return phase;
    }

    public SessionPhase phaseAt(long absoluteTick) {
        return schedule.phaseAt(offset(absoluteTick));
    }

    /** Begin tracking at the session start tick. */
    public void begin() {
        lastTick = startTick - 1L;
        phase = SessionPhase.PRE_OPEN;
    }

    /**
     * Advance observation to an absolute tick and return every phase transition crossed.
     */
    public List<SessionTransition> advance(long tick) {
        List<SessionTransition> transitions = new ArrayList<>();
        for (long current = lastTick + 1L; current <= tick; current++) {
            SessionPhase next = schedule.phaseAt(offset(current));
            if (next != phase) {
                transitions.add(new SessionTransition(current, phase, next));
                phase = next;
            }
        }
        lastTick = Math.max(lastTick, tick);
        return List.copyOf(transitions);
    }

    private long offset(long absoluteTick) {
        return absoluteTick - startTick;
    }
}
