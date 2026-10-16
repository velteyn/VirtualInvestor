package com.virtualinvestor.runtime;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Market-scenarios soak: short fast rounds must release scenario rumors and,
 * on violent company shocks, trip a REALISTIC circuit breaker end to end.
 */
class ScenarioSoakTest {

    private static final long STEP_MILLIS = 1_000L;

    @Test
    void scenariosReleaseAndHaltTheMarket() {
        Set<String> rumorIds = new HashSet<>();
        boolean haltTripped = false;
        for (long seed = 1L; seed <= 6L; seed++) {
            RunningRound round = new RunningRound("round-scen-" + seed, seed,
                    new LaunchConfig(100_000.0, 15L, RealismPreset.REALISTIC), STEP_MILLIS);
            round.start();
            for (int i = 0; i < 400; i++) {
                for (GameEvent event : round.step()) {
                    if (event instanceof GameEvent.NewsReleased news
                            && news.stage() == NewsStage.RUMOR
                            && news.newsId().startsWith("scen-")) {
                        rumorIds.add(seed + ":" + news.newsId());
                    }
                }
                long tick = round.clock().tick();
                for (var breaker : round.breakers().values()) {
                    if (breaker.isHalted(tick)) {
                        haltTripped = true;
                    }
                }
            }
        }
        assertTrue(!rumorIds.isEmpty(), "scenario rumors must release in live rounds");
        assertTrue(haltTripped, "a violent scenario must trip a breaker across seeds 1-6");
    }
}
