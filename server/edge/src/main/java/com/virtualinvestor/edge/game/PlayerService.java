package com.virtualinvestor.edge.game;

import com.virtualinvestor.edge.ws.IdentityDirectory;
import com.virtualinvestor.persist.player.PlayerStore;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Registered-player identity for the edge (player-accounts 2.1): registration,
 * approval-gated login, and the login-token map the socket layer uses to bind a
 * connection to a player (task 2.2 consumes {@link #playerForToken}).
 */
@Service
public class PlayerService implements IdentityDirectory {

    private final PlayerStore store;
    private final Map<String, String> loginTokens = new ConcurrentHashMap<>();

    public PlayerService(PlayerStore store) {
        this.store = store;
    }

    public PlayerStore.Player register(String username, String password, String bankId) {
        return store.register(username, password, bankId);
    }

    public record Login(PlayerStore.Player player, String sessionToken) {
    }

    /**
     * Log in: unknown users and wrong passwords look identical (invalid
     * credentials); pending or rejected players get not-approved instead.
     */
    public Login login(String username, String password) {
        Optional<PlayerStore.Player> player = store.findByUsername(username);
        if (player.isEmpty() || !store.checkPassword(username, password)) {
            throw new IllegalArgumentException("invalid credentials");
        }
        if (player.get().status() != PlayerStore.Status.APPROVED) {
            throw new IllegalStateException("player is not approved");
        }
        String token = UUID.randomUUID().toString();
        loginTokens.put(token, player.get().playerId());
        return new Login(player.get(), token);
    }

    public Optional<String> playerForToken(String sessionToken) {
        return Optional.ofNullable(loginTokens.get(sessionToken));
    }

    @Override
    public Optional<IdentityDirectory.Identity> identityForToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            return Optional.empty();
        }
        return playerForToken(sessionToken).map(IdentityDirectory.Identity::new);
    }

    public void invalidatePlayerSessions(String playerId) {
        loginTokens.entrySet().removeIf(entry -> entry.getValue().equals(playerId));
    }

    public PlayerStore store() {
        return store;
    }
}
