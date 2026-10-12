package com.virtualinvestor.edge.game;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.RoundState;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.edge.ws.RecordingWebSocketSession;
import com.virtualinvestor.runtime.RunningRound;
import org.junit.jupiter.api.Test;

/**
 * Group 7.1: a full bot-only round on the server moves prices, updates books, fires news and
 * settles with a finalized leaderboard, streaming frames throughout.
 */
class BotOnlyRoundIntegrationTest {

    @Test
    void botOnlyRoundRunsMovesBooksFiresNewsAndSettles() {
        MarketBroadcaster broadcaster = new MarketBroadcaster();
        RecordingWebSocketSession subscriber = new RecordingWebSocketSession();
        broadcaster.register(subscriber);
        broadcaster.join(subscriber, "round-bots");

        RoundRuntimeService runtime = new RoundRuntimeService(broadcaster);
        // A 1-minute step gives a 60-tick session, runnable in a test.
        RunningRound round = runtime.start("round-bots", 20261002L,
                new LaunchConfig(100_000.0, 60L, RealismPreset.REALISTIC), false, 60_000L);

        double firstPrice = round.engines().get("NVDO").lastPrice();
        int trades = 0;
        int news = 0;

        // A 60-tick session at 1-minute steps; run to settlement.
        for (int i = 0; i < 120 && !round.settled(); i++) {
            for (GameEvent event : round.step()) {
                if (event instanceof GameEvent.TradePrinted) {
                    trades++;
                } else if (event instanceof GameEvent.NewsConfirmed || event instanceof GameEvent.NewsReleased) {
                    news++;
                }
            }
        }

        assertTrue(round.settled(), "the round should settle by the close");
        assertEquals(RoundState.SETTLED, round.state());
        assertTrue(trades > 0, "bots should trade");
        assertTrue(round.engines().get("NVDO").lastPrice() != firstPrice, "the price should move");
        assertTrue(news > 0, "news should fire");
        assertEquals(5, round.rivals().size(), "the leaderboard has five rivals");

        // Something reached the subscriber: trade/news/depth frames.
        String stream = String.join("\n", subscriber.sent());
        assertTrue(stream.contains("\"type\":\"trade\""), "trade frames should stream");
    }
}
