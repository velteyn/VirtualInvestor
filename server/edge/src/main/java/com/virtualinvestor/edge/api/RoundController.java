package com.virtualinvestor.edge.api;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.rules.MarketRules;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.game.GameService;
import com.virtualinvestor.edge.wire.WireMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Round control over REST, versioned with Spring MVC's API versioning
 * ({@code @RequestMapping(version = ...)}). Symbol search and leaderboard live here too because
 * they are request/response, unlike the streaming WebSocket gateway.
 */
@RestController
@RequestMapping("/api")
public class RoundController {

    private static final Logger log = LoggerFactory.getLogger(RoundController.class);

    private final GameService game;

    public RoundController(GameService game) {
        this.game = game;
    }

    public record CreateRoundRequest(
            double startingCapital, long durationMinutes, String preset, String sessionToken) {
    }

    public record RoundResponse(
            String roundId,
            long seed,
            double startingCapital,
            long durationMinutes,
            String preset,
            String state,
            long durationTicks,
            String sessionToken) {
    }

    @PostMapping(path = "/rounds", version = "1")
    public ResponseEntity<RoundResponse> createRound(@RequestBody CreateRoundRequest request) {
        RealismPreset preset = parsePreset(request.preset());
        LaunchConfig config;
        try {
            config = new LaunchConfig(request.startingCapital(), request.durationMinutes(), preset);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        // An approved login token links the round to the player's purse; without
        // one the round runs anonymous exactly as before.
        String playerId = null;
        if (request.sessionToken() != null && !request.sessionToken().isBlank()) {
            playerId = game.players().playerForToken(request.sessionToken()).orElse(null);
        }
        GameService.RoundView round = game.createRound(config, playerId, true);
        log.info("createRound {} player={} capital={} minutes={} preset={}", round.roundId(), playerId,
                request.startingCapital(), request.durationMinutes(), request.preset());
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(round));
    }

    @PostMapping(path = "/symbols/search", version = "1")
    public List<SymbolResponse> searchSymbols(@RequestBody SymbolSearchRequest request) {
        int limit = request.limit() <= 0 ? 20 : request.limit();
        RealismPreset preset = request.preset() == null || request.preset().isBlank()
                ? RealismPreset.REALISTIC
                : parsePreset(request.preset());
        return game.search(request.query(), limit).stream()
                .map(instrument -> {
                    String assetClass = switch (instrument.type()) {
                        case INDEX -> "Index";
                        case EQUITY -> "Equity";
                        case FUTURE -> "Future";
                        case OPTION -> "Option";
                    };
                    double initialMargin = 0.0;
                    double maintenanceMargin = 0.0;
                    if (instrument.isSeries()) {
                        MarketRules rules = MarketRules.forSeries(instrument, preset);
                        initialMargin = rules.initialMarginFraction();
                        maintenanceMargin = rules.maintenanceMarginFraction();
                    }
                    return new SymbolResponse(
                            instrument.ticker(), instrument.name(), instrument.sector(), assetClass,
                            instrument.tickSize(), instrument.quantityGrid(), instrument.referencePrice(),
                            initialMargin, maintenanceMargin);
                })
                .toList();
    }

    @PostMapping(path = "/rounds/leaderboard", version = "1")
    public List<Standing> leaderboard(@RequestBody LeaderboardRequest request) {
        if (game.round(request.roundId()).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown round: " + request.roundId());
        }
        return game.leaderboard(request.roundId(), Map.of());
    }

    @PostMapping(path = "/rounds/control", version = "1")
    public ResponseEntity<Void> control(@RequestBody ControlRequest request) {
        try {
            game.control(request.roundId(), request.action());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    public record ControlRequest(String roundId, String action) {
    }

    /**
     * The warm-up history for a symbol in a running round, so the chart can load real bars
     * instead of a placeholder. Times are UTC seconds.
     */
    @PostMapping(path = "/rounds/history", version = "1")
    public List<BarResponse> history(@RequestBody HistoryRequest request) {
        var round = game.runningRound(request.roundId());
        if (round.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown round: " + request.roundId());
        }
        var bars = round.get().history().get(request.symbol());
        if (bars == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown symbol: " + request.symbol());
        }
        return bars.stream()
                .map(bar -> new BarResponse(bar.timeSec(), bar.open(), bar.high(), bar.low(), bar.close(), bar.volume()))
                .toList();
    }

    public record HistoryRequest(String roundId, String symbol) {
    }

    /**
     * Fill history for one participant (portfolio-tab 2.1): one row per filled
     * order with cumulative quantity, last price and fee, folded from the journal.
     */
    @PostMapping(path = "/rounds/fills", version = "1")
    public List<WireMessage.FillDto> fills(@RequestBody FillsRequest request) {
        try {
            return game.fills(request.roundId(), request.participantId()).stream()
                    .map(row -> new WireMessage.FillDto(row.orderId(), row.symbol(), row.side(),
                            row.orderType(), row.quantity(), row.filledQuantity(), row.price(), row.fee(),
                            row.tick()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public record FillsRequest(String roundId, String participantId) {
    }

    /**
     * Newsroom archive for one round (newsroom-narrator 1.3): stored articles
     * oldest-first for late joiners and the newsroom tab.
     */
    @PostMapping(path = "/rounds/articles", version = "1")
    public List<ArticleResponse> articles(@RequestBody ArticlesRequest request) {
        try {
            return game.articles(request.roundId()).stream()
                    .map(row -> new ArticleResponse(row.newsId(), row.roundId(), row.tick(),
                            row.kind().name(), row.tickers(), row.headline(), row.body(), row.model()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public record ArticlesRequest(String roundId) {
    }

    public record ArticleResponse(String newsId, String roundId, long tick, String kind,
            java.util.List<String> tickers, String headline, String body, String model) {
    }

    /**
     * Per-fill markouts for one participant (realistic-liquidity 4.1): each fill
     * joined to the first print at or past fill tick + horizon, so execution
     * quality is measurable over the wire. A missing or non-positive horizon
     * falls back to the server default (5 ticks).
     */
    @PostMapping(path = "/rounds/markouts", version = "1")
    public List<WireMessage.MarkoutDto> markouts(@RequestBody MarkoutsRequest request) {
        try {
            return game.markouts(request.roundId(), request.participantId(),
                            request.horizonTicks() == null ? 0L : request.horizonTicks()).stream()
                    .map(row -> new WireMessage.MarkoutDto(row.orderId(), row.symbol(), row.side(),
                            row.fillPrice(), row.markPrice(), row.markout(), row.fillTick(), row.markTick()))
                    .toList();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    public record MarkoutsRequest(String roundId, String participantId, Long horizonTicks) {
    }

    public record BarResponse(long time, double open, double high, double low, double close, long volume) {
    }

    public record SymbolSearchRequest(String query, int limit, String preset) {
    }

    public record SymbolResponse(String ticker, String name, String sector, String assetClass,
            double tickSize, long quantityGrid, double referencePrice,
            double initialMarginFraction, double maintenanceMarginFraction) {
    }

    public record LeaderboardRequest(String roundId) {
    }

    private static RoundResponse toResponse(GameService.RoundView round) {
        return new RoundResponse(
                round.roundId(),
                round.seed(),
                round.config().startingCapital(),
                round.config().durationMinutes(),
                round.config().preset().name(),
                round.lifecycle().state().name(),
                round.durationTicks(),
                round.sessionToken());
    }

    private static RealismPreset parsePreset(String preset) {
        try {
            return RealismPreset.valueOf(preset.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown preset: " + preset);
        }
    }
}
