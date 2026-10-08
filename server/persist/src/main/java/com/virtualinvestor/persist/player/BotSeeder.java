package com.virtualinvestor.persist.player;

import com.virtualinvestor.core.game.RivalRoster;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Ensures one approved BOT player row per roster name (player-accounts 1.3), so bots
 * are registered identities on the same footing as humans. Idempotent: re-running
 * changes nothing. Bank assignment is deterministic by roster index (design D5);
 * round setup calls this before the roster is built.
 */
public final class BotSeeder {

    private BotSeeder() {
    }

    public static List<PlayerStore.Player> ensureRoster(PlayerStore store, List<String> bankIds) {
        return ensure(store, bankIds, RivalRoster.RIVAL_NAMES);
    }

    /** Same treatment for market institutions: every filler pays too. */
    public static List<PlayerStore.Player> ensureInstitutions(PlayerStore store, List<String> bankIds) {
        return ensure(store, bankIds, RivalRoster.INSTITUTION_NAMES);
    }

    private static List<PlayerStore.Player> ensure(PlayerStore store, List<String> bankIds, List<String> names) {
        Objects.requireNonNull(store, "store");
        if (bankIds == null || bankIds.isEmpty()) {
            throw new IllegalArgumentException("at least one bank is required");
        }
        List<PlayerStore.Player> bots = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            String username = "bot-" + names.get(i).toLowerCase(java.util.Locale.ROOT).replace(' ', '-');
            bots.add(store.ensureBot(username, bankIds.get(i % bankIds.size())));
        }
        return List.copyOf(bots);
    }
}
