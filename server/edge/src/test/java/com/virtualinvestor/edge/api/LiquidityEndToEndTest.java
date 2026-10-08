package com.virtualinvestor.edge.api;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.virtualinvestor.core.book.DepthLevel;
import com.virtualinvestor.core.domain.OrderEventKind;
import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderStatus;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.domain.SessionPhase;
import com.virtualinvestor.edge.EdgeApplication;
import com.virtualinvestor.edge.game.GameService;
import com.virtualinvestor.edge.game.RoundRuntimeService;
import com.virtualinvestor.edge.wire.WireMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * Realistic-liquidity 4.1: the ladder, the fade and the markouts, end to end over the live
 * server. The round is created through REST, orders travel the real socket path
 * ({@code GameService.submit} is what the WebSocket handler calls), and the world steps
 * deterministically: an oversized market order walks visible levels, a sweep under measured
 * stress thins the book until the next take rejects with its reason, and the fills show up
 * with markouts on the read endpoint.
 *
 * <p>Known calibration gap (left for a follow-up, not papered over): the tolerance-gated
 * full withdrawal (2.25-4.0) is unreachable through organic flow — the deepest sweep the
 * book affords spikes toxicity to ~0.7, and the 0.97-per-tick decay erases it before the
 * 50-tick refresh wave completes. What the live book does under stress is fade-driven
 * thinning (sizes hit zero past 1/sensitivity) plus consumption, which is what this test
 * exercises. See tasks 4.1.
 */
@SpringBootTest(classes = EdgeApplication.class)
@AutoConfigureMockMvc
class LiquidityEndToEndTest {

    private static final String HUMAN = "human";
    private static final long LADDER_TIMEOUT_TICKS = 1_000L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private GameService game;

    @Autowired
    private RoundRuntimeService runtime;

    @Autowired
    private javax.sql.DataSource dataSource;

    private final List<String> createdRounds = new ArrayList<>();
    /** Pre-sweep toxicity on the swept symbol, recorded by sweep() for the spike assertion. */
    private double sweepBaselineTox;

    @AfterEach
    void cleanUp() throws Exception {
        for (String roundId : createdRounds) {
            runtime.stop(roundId);
        }
        if (!createdRounds.isEmpty()) {
            try (java.sql.Connection connection = dataSource.getConnection()) {
                try (java.sql.PreparedStatement statement = connection
                        .prepareStatement("DELETE FROM events WHERE round_id = ANY (?)")) {
                    statement.setArray(1, connection.createArrayOf("text", createdRounds.toArray()));
                    statement.executeUpdate();
                }
                try (java.sql.PreparedStatement statement = connection
                        .prepareStatement("DELETE FROM snapshots WHERE round_id = ANY (?)")) {
                    statement.setArray(1, connection.createArrayOf("text", createdRounds.toArray()));
                    statement.executeUpdate();
                }
            }
        }
        createdRounds.clear();
    }

    @Test
    void ladderFadeAndMarkoutsOverTheLiveServer() throws Exception {
        // 1. The round-creation path is REST on the live server; the scenario round
        // itself runs headless (no real-time driver) so every tick is deterministic.
        MvcResult created = mockMvc.perform(post("/api/rounds")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"startingCapital\":100000,\"durationMinutes\":60,\"preset\":\"REALISTIC\"}"))
                .andExpect(status().isCreated())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$.roundId").isNotEmpty())
                .andReturn();
        String restRound = new tools.jackson.databind.ObjectMapper()
                .readTree(created.getResponse().getContentAsString()).get("roundId").asText();
        createdRounds.add(restRound);

        com.virtualinvestor.core.game.LaunchConfig config =
                new com.virtualinvestor.core.game.LaunchConfig(100_000.0, 45L,
                        com.virtualinvestor.core.rules.RealismPreset.REALISTIC);
        String roundId = game.createRound(config, java.util.List.<String>of(), false, 1_000L).roundId();
        createdRounds.add(roundId);

        com.virtualinvestor.runtime.RunningRound live = game.runningRound(roundId).orElseThrow();
        stepUntilContinuous(live);

        // 2. Sweep the deepest early ladder: the burst's own flow spikes toxicity
        // several times above the calm baseline while makers fade, leaving the side empty.
        // (Scale-free bars: recalibration moves absolute toxicity, the spike ratio is what matters.)
        String sweepSymbol = sweep(live, roundId);
        double stressTox = live.toxicity(sweepSymbol);
        assertTrue(stressTox > 2.0 * sweepBaselineTox && stressTox - sweepBaselineTox > 0.05,
                "the sweep should leave toxicity well above baseline, calm=" + sweepBaselineTox
                        + " stress=" + stressTox);

        // 3. The stressed, thinned book rejects the next take with its reason. Requotes
        // land only on refresh ticks, so probe each empty tick until one rejects.
        probeReject(live, roundId, sweepSymbol);

        // 4. Oversized market order walks visible levels on a ladder-shaped book:
        // size past the touch, then take it. Books reshape every tick, so re-scan
        // and retry until a take prints across levels.
        String walkOrderId = walkTheBook(live, roundId);

        // 5. Markouts recorded: the walk fill resolves against later prints.
        for (int i = 0; i < 30; i++) {
            live.step();
        }
        mockMvc.perform(post("/api/rounds/markouts")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"" + roundId + "\",\"participantId\":\"" + HUMAN + "\"}"))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.orderId=='" + walkOrderId + "')].markTick").exists())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .jsonPath("$[?(@.orderId=='" + walkOrderId + "')].markout").exists());

        mockMvc.perform(post("/api/rounds/markouts")
                        .header("X-API-Version", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"roundId\":\"ghost\",\"participantId\":\"" + HUMAN + "\"}"))
                .andExpect(status().isNotFound());
    }

    private void stepUntilContinuous(com.virtualinvestor.runtime.RunningRound live) {
        for (int i = 0; i < 400
                && live.session().phase() != SessionPhase.CONTINUOUS; i++) {
            live.step();
        }
        assertEquals(SessionPhase.CONTINUOUS, live.session().phase(),
                "the round should reach continuous trading");
    }

    private record WalkTarget(String symbol, long quantity, long touch) {
    }

    /**
     * Walk a ladder-shaped book: re-scan every tick for at least two visible ask levels
     * with the touch no bigger than half the book and affordable headroom past it,
     * then take past the touch. A take that prints a single price just ate a wall that
     * formed after the scan — retry on the next shape. Shapes vary with the round seed,
     * so this adapts instead of assuming one symbol's book.
     */
    private String walkTheBook(com.virtualinvestor.runtime.RunningRound live, String roundId) {
        long cash = (long) live.participant(HUMAN).account().cash();
        for (long attempt = 0; attempt < 8L; attempt++) {
            WalkTarget walk = null;
            for (long i = 0; i < 200L; i++) {
                walk = scanWalkTarget(live, cash);
                if (walk != null) {
                    break;
                }
                live.step();
            }
            if (walk == null) {
                throw new AssertionError("no ladder-shaped book found for the walk");
            }
            String orderId = "e2e-walk-" + attempt;
            List<GameEvent> events = take(live, roundId, orderId, walk.symbol(), OrderSide.BUY,
                    walk.quantity());
            // The taker's own report is cumulative (one FILL at the last price); the
            // walk shows in the tape: one print per level crossed, at worsening prices.
            List<Double> prints = new ArrayList<>();
            long filledTotal = 0L;
            for (GameEvent event : events) {
                if (event instanceof GameEvent.TradePrinted trade
                        && trade.symbol().equals(walk.symbol())) {
                    prints.add(trade.price());
                }
                if (event instanceof GameEvent.OrderReport report && report.orderId().equals(orderId)
                        && (report.event() == OrderEventKind.FILL
                                || report.event() == OrderEventKind.PARTIAL_FILL)) {
                    filledTotal = Math.max(filledTotal, report.filledQuantity());
                }
            }
            Set<Double> distinctPrices = new HashSet<>(prints);
            if (distinctPrices.size() >= 2 && filledTotal > walk.touch()) {
                List<Double> ascending = new ArrayList<>(distinctPrices);
                java.util.Collections.sort(ascending);
                assertEquals(ascending.get(ascending.size() - 1), prints.get(prints.size() - 1),
                        "a buy walks up the asks: the last print is the worst price, tape " + prints);
                return orderId;
            }
        }
        throw new AssertionError("no take walked more than one level in 8 attempts");
    }

    /**
     * The best ladder-shaped book right now: at least 2 visible ask levels, a touch no
     * bigger than half the visible book (walls don't count — the take must have levels
     * to walk), and affordable headroom past the touch.
     */
    private WalkTarget scanWalkTarget(com.virtualinvestor.runtime.RunningRound live, long cash) {
        WalkTarget best = null;
        long bestTotal = 0L;
        for (String symbol : live.engines().keySet()) {
            List<DepthLevel> asks = live.engines().get(symbol).book().depth(OrderSide.SELL, 5);
            long total = asks.stream().mapToLong(DepthLevel::quantity).sum();
            long touch = asks.isEmpty() ? 0L : asks.get(0).quantity();
            if (asks.size() < 2 || touch <= 0L || touch * 2L > total) {
                continue;
            }
            long affordable = (long) (cash / asks.get(0).price());
            if (affordable < touch * 2L) {
                continue;
            }
            if (total > bestTotal) {
                bestTotal = total;
                best = new WalkTarget(symbol, Math.min(total, affordable), touch);
            }
        }
        return best;
    }

    private List<GameEvent> take(com.virtualinvestor.runtime.RunningRound live, String roundId,
            String orderId, String symbol, OrderSide side, long quantity) {
        game.submit(roundId, HUMAN, new WireMessage.PlaceOrderCommand(orderId, symbol, side.name(),
                "MARKET", quantity, 0.0, 0.0));
        return live.step();
    }

    /**
     * Eat the deepest early ask ladder in one market take, on whichever symbol offers
     * it. The sweep's own flow is the stress: toxicity jumps several times above
     * baseline while maker sizes fade. Returns the swept symbol.
     */
    private String sweep(com.virtualinvestor.runtime.RunningRound live, String roundId) {
        for (long i = 0; i < 200L; i++) {
            String deepest = null;
            long bestDepth = 0L;
            for (String symbol : live.engines().keySet()) {
                long depth = live.engines().get(symbol).book().aggregateQuantity(OrderSide.SELL, 5);
                if (depth > bestDepth) {
                    bestDepth = depth;
                    deepest = symbol;
                }
            }
            if (bestDepth >= 300L) {
                sweepBaselineTox = live.toxicity(deepest);
                game.submit(roundId, HUMAN, new WireMessage.PlaceOrderCommand("e2e-sweep-1", deepest,
                        OrderSide.BUY.name(), "MARKET", bestDepth, 0.0, 0.0));
                live.step();
                return deepest;
            }
            live.step();
        }
        throw new AssertionError("no ask ladder deepened for the sweep");
    }

    /**
     * Probe the thinned book until a take hits an empty tick and rejects. Each probe
     * applies on the next tick; a refresh landing exactly on a probe tick fills it
     * instead, so retry — the loop is bounded and toxicity must still read elevated
     * when the reject lands, proving the book was stressed, not merely idle.
     */
    private void probeReject(com.virtualinvestor.runtime.RunningRound live, String roundId,
            String symbol) {
        for (long i = 0; i < 20L; i++) {
            if (live.engines().get(symbol).book().aggregateQuantity(OrderSide.SELL, 5) != 0L) {
                live.step();
                continue;
            }
            List<GameEvent> events = take(live, roundId, "e2e-reject-" + i, symbol, OrderSide.BUY, 10L);
            String probeId = "e2e-reject-" + i;
            List<GameEvent.OrderReport> rejects = events.stream()
                    .filter(GameEvent.OrderReport.class::isInstance)
                    .map(GameEvent.OrderReport.class::cast)
                    .filter(report -> report.orderId().equals(probeId)
                            && report.event() == OrderEventKind.REJECTED)
                    .toList();
            if (rejects.size() == 1) {
                assertEquals(OrderStatus.REJECTED, rejects.get(0).status());
                assertEquals("no liquidity for market order", rejects.get(0).reason());
                assertTrue(live.toxicity(symbol) > 0.15,
                        "the reject should land while toxicity is still elevated, got "
                                + live.toxicity(symbol));
                return;
            }
        }
        throw new AssertionError("no take rejected within 20 ticks of the sweep");
    }
}
