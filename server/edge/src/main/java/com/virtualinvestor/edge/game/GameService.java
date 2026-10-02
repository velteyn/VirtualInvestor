package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.game.RivalRoster;
import com.virtualinvestor.core.game.RoundLifecycle;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.instrument.SymbolSearch;
import com.virtualinvestor.core.participant.BotParticipant;
import com.virtualinvestor.core.participant.HumanParticipant;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Ranking;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Service;

/**
 * Spring-managed game service (design D2): owns round creation, the session-token registry and
 * queries built on the framework-free core. It holds no simulation logic of its own; the hot
 * world loop will be driven separately.
 */
@Service
public class GameService {

    private static final long STEP_MILLIS = 100L;

    private final InstrumentUniverse universe;
    private final SymbolSearch symbolSearch;
    private final Map<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final Map<String, RoundView> rounds = new ConcurrentHashMap<>();
    private long roundCounter = 0L;

    public GameService() {
        this.universe = InstrumentUniverse.defaultUniverse();
        this.symbolSearch = new SymbolSearch(universe);
    }

    public InstrumentUniverse universe() {
        return universe;
    }

    public List<Instrument> search(String query, int limit) {
        return symbolSearch.search(query, limit);
    }

    /** Create a round with a fresh seed, participants and a session token for the player. */
    public RoundView createRound(LaunchConfig config) {
        long seed = 20261002L + roundCounter;
        String roundId = "round-" + (++roundCounter);
        RoundLifecycle lifecycle = new RoundLifecycle(roundId, seed, config);

        HumanParticipant player = new HumanParticipant("human", "You", config.startingCapital());
        List<BotParticipant> rivals = RivalRoster.rivals(config.startingCapital());
        List<BotParticipant> institutions = RivalRoster.institutions(config.startingCapital());

        Map<String, Double> startingCapitals = new LinkedHashMap<>();
        startingCapitals.put(player.id(), config.startingCapital());
        for (BotParticipant rival : rivals) {
            startingCapitals.put(rival.id(), config.startingCapital());
        }

        String rawToken = "session-" + roundId + "-" + seed;
        sessionTokens.put(player.id(), rawToken);

        RoundView view = new RoundView(roundId, seed, config, lifecycle, player, rivals, institutions,
                startingCapitals, rawToken);
        rounds.put(roundId, view);
        return view;
    }

    public Optional<RoundView> round(String roundId) {
        return Optional.ofNullable(rounds.get(roundId));
    }

    public List<Standing> leaderboard(String roundId, Map<String, Double> marks) {
        RoundView view = rounds.get(roundId);
        if (view == null) {
            return List.of();
        }
        List<Participant> everyone = new ArrayList<>();
        everyone.add(view.player());
        everyone.addAll(view.rivals());
        everyone.addAll(view.institutions());
        return Ranking.standings(everyone, marks, view.startingCapitals());
    }

    /** A round's live view, including its participants and current standings. */
    public static final class RoundView {
        private final String roundId;
        private final long seed;
        private final LaunchConfig config;
        private final RoundLifecycle lifecycle;
        private final HumanParticipant player;
        private final List<BotParticipant> rivals;
        private final List<BotParticipant> institutions;
        private final Map<String, Double> startingCapitals;
        private final String sessionToken;
        private final long durationTicks;

        RoundView(String roundId, long seed, LaunchConfig config, RoundLifecycle lifecycle,
                  HumanParticipant player, List<BotParticipant> rivals, List<BotParticipant> institutions,
                  Map<String, Double> startingCapitals, String sessionToken) {
            this.roundId = roundId;
            this.seed = seed;
            this.config = config;
            this.lifecycle = lifecycle;
            this.player = player;
            this.rivals = rivals;
            this.institutions = institutions;
            this.startingCapitals = startingCapitals;
            this.sessionToken = sessionToken;
            this.durationTicks = config.durationTicks(STEP_MILLIS);
        }

        public String roundId() {
            return roundId;
        }

        public long seed() {
            return seed;
        }

        public LaunchConfig config() {
            return config;
        }

        public RoundLifecycle lifecycle() {
            return lifecycle;
        }

        public HumanParticipant player() {
            return player;
        }

        public List<BotParticipant> rivals() {
            return rivals;
        }

        public List<BotParticipant> institutions() {
            return institutions;
        }

        public Map<String, Double> startingCapitals() {
            return startingCapitals;
        }

        public String sessionToken() {
            return sessionToken;
        }

        public long durationTicks() {
            return durationTicks;
        }
    }

    /** Expose the preset list for the launch pad. */
    public static List<RealismPreset> presets() {
        return List.of(RealismPreset.values());
    }
}
