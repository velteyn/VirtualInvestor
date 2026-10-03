package com.virtualinvestor.edge.api;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.participant.Standing;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.game.GameService;
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

    private final GameService game;

    public RoundController(GameService game) {
        this.game = game;
    }

    public record CreateRoundRequest(
            double startingCapital, long durationMinutes, String preset) {
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
        GameService.RoundView round = game.createRound(config);
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(round));
    }

    @PostMapping(path = "/symbols/search", version = "1")
    public List<SymbolResponse> searchSymbols(@RequestBody SymbolSearchRequest request) {
        int limit = request.limit() <= 0 ? 20 : request.limit();
        return game.search(request.query(), limit).stream()
                .map(instrument -> new SymbolResponse(
                        instrument.ticker(), instrument.name(), instrument.sector(),
                        instrument.isIndex() ? "Index" : "Equity"))
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

    public record BarResponse(long time, double open, double high, double low, double close, long volume) {
    }

    public record SymbolSearchRequest(String query, int limit) {
    }

    public record SymbolResponse(String ticker, String name, String sector, String assetClass) {
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
