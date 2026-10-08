package com.virtualinvestor.edge.game;

import com.virtualinvestor.persist.player.PlayerStore;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Game-master authentication (player-accounts 3.1): the master password arrives
 * through the environment and is never stored; a successful login issues an admin
 * token from a dedicated realm. Player login tokens never resolve here and admin
 * tokens never resolve in {@link PlayerService}, so the realms cannot cross.
 */
public class AdminService {

    private final PlayerStore store;
    private final String masterPassword;
    private final Map<String, Boolean> adminTokens = new ConcurrentHashMap<>();

    public AdminService(PlayerStore store) {
        this(store, System.getenv().getOrDefault("VI_ADMIN_PASSWORD", ""));
    }

    public AdminService(PlayerStore store, String masterPassword) {
        this.store = Objects.requireNonNull(store, "store");
        this.masterPassword = Objects.requireNonNull(masterPassword, "masterPassword");
    }

    /** Log in the game master; an empty configured password means login is impossible. */
    public String login(String password) {
        if (masterPassword.isEmpty() || password == null
                || !MessageDigest.isEqual(masterPassword.getBytes(), password.getBytes())) {
            throw new IllegalArgumentException("invalid admin credentials");
        }
        String token = UUID.randomUUID().toString();
        adminTokens.put(token, Boolean.TRUE);
        return token;
    }

    public boolean isAdmin(String token) {
        return token != null && adminTokens.containsKey(token);
    }

    public PlayerStore store() {
        return store;
    }
}
