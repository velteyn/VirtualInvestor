package com.virtualinvestor.edge.ws;

import java.util.Optional;

/**
 * Resolves a login token to the player behind it (player-accounts 2.2). Only tokens
 * for approved players resolve; unknown, pending-without-token, rejected or
 * invalidated tokens resolve to empty and the socket is refused. The production
 * implementation is the player service; tests fake it with a map.
 */
public interface IdentityDirectory {

    record Identity(String playerId) {
    }

    Optional<Identity> identityForToken(String sessionToken);
}
