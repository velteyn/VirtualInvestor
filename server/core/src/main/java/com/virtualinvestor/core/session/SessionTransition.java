package com.virtualinvestor.core.session;

import com.virtualinvestor.core.domain.SessionPhase;

/** A recorded change of session phase at an absolute world tick. */
public record SessionTransition(long tick, SessionPhase from, SessionPhase to) {
}
