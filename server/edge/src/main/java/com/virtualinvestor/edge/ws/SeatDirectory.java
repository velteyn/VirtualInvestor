package com.virtualinvestor.edge.ws;

import java.util.Optional;

/**
 * Resolves which seat a player holds in a round (multiplayer-seats 2.3). Joins
 * bind the resolved seat, never the stated participant id, so each socket
 * trades exactly as its own seat. Tests fake it with a map.
 */
public interface SeatDirectory {

    Optional<String> seatFor(String roundId, String playerId);
}
