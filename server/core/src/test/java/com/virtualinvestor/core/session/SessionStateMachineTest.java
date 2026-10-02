package com.virtualinvestor.core.session;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.SessionPhase;
import java.util.List;
import org.junit.jupiter.api.Test;

class SessionStateMachineTest {

    private static final SessionSchedule SCHEDULE = new SessionSchedule(10L, 5L, 100L, 10L);

    @Test
    void phaseAtReturnsExpectedPhasePerTick() {
        assertEquals(SessionPhase.PRE_OPEN, SCHEDULE.phaseAt(0L));
        assertEquals(SessionPhase.PRE_OPEN, SCHEDULE.phaseAt(9L));
        assertEquals(SessionPhase.OPENING_AUCTION, SCHEDULE.phaseAt(10L));
        assertEquals(SessionPhase.OPENING_AUCTION, SCHEDULE.phaseAt(14L));
        assertEquals(SessionPhase.CONTINUOUS, SCHEDULE.phaseAt(15L));
        assertEquals(SessionPhase.CONTINUOUS, SCHEDULE.phaseAt(114L));
        assertEquals(SessionPhase.CLOSING_AUCTION, SCHEDULE.phaseAt(115L));
        assertEquals(SessionPhase.CLOSING_AUCTION, SCHEDULE.phaseAt(124L));
        assertEquals(SessionPhase.CLOSED, SCHEDULE.phaseAt(125L));
    }

    @Test
    void transitionsOccurAtTheRightTicks() {
        SessionStateMachine machine = new SessionStateMachine(SCHEDULE, 1000L);

        List<SessionTransition> transitions = machine.advance(1125L);

        assertEquals(4, transitions.size());
        assertTransition(transitions.get(0), 1010L, SessionPhase.PRE_OPEN, SessionPhase.OPENING_AUCTION);
        assertTransition(transitions.get(1), 1015L, SessionPhase.OPENING_AUCTION, SessionPhase.CONTINUOUS);
        assertTransition(transitions.get(2), 1115L, SessionPhase.CONTINUOUS, SessionPhase.CLOSING_AUCTION);
        assertTransition(transitions.get(3), 1125L, SessionPhase.CLOSING_AUCTION, SessionPhase.CLOSED);
        assertEquals(SessionPhase.CLOSED, machine.phase());
    }

    @Test
    void incrementalAdvanceMatchesBulkAdvance() {
        SessionStateMachine incremental = new SessionStateMachine(SCHEDULE, 0L);
        int transitionCount = 0;
        for (long tick = 0L; tick <= 200L; tick++) {
            transitionCount += incremental.advance(tick).size();
        }
        assertEquals(4, transitionCount);
    }

    @Test
    void standardSchedulePartitionsTheWholeSession() {
        SessionSchedule standard = SessionSchedule.standard(18_000L);
        assertEquals(18_000L, standard.totalTicks());
        assertTrue(standard.openingAuctionTicks() >= 1L);
        assertTrue(standard.continuousTicks() > 0L);
    }

    private static void assertTransition(SessionTransition transition, long tick, SessionPhase from, SessionPhase to) {
        assertEquals(tick, transition.tick());
        assertEquals(from, transition.from());
        assertEquals(to, transition.to());
    }
}
