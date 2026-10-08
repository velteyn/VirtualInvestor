package com.virtualinvestor.edge.api;

import com.virtualinvestor.core.game.LaunchConfig;
import com.virtualinvestor.core.rules.RealismPreset;
import com.virtualinvestor.edge.game.GameService;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Waiting rooms over REST (game-lobby 2.1/2.2), versioned like the round
 * endpoints. The listing is public; every mutation needs a login token.
 * Refusals: unknown room/player as 404, full or started room as 409,
 * stranger start or unapproved player as 403, missing token as 401.
 */
@RestController
@RequestMapping("/api")
public class RoomController {

    private final GameService game;

    public RoomController(GameService game) {
        this.game = game;
    }

    public record CreateRoomRequest(double startingCapital, long durationMinutes, String preset,
            String sessionToken) {
    }

    public record RoomResponse(String roundId, String state, int seatsTaken, int seatsTotal,
            String masterPlayerId, String sessionToken) {
    }

    public record JoinRequest(String roundId, String sessionToken) {
    }

    public record JoinResponse(String roundId, String seat, String state) {
    }

    @PostMapping(path = "/rooms", version = "1")
    public ResponseEntity<RoomResponse> createRoom(@RequestBody CreateRoomRequest request) {
        String playerId = playerForToken(request.sessionToken());
        RealismPreset preset = parsePreset(request.preset());
        LaunchConfig config;
        try {
            config = new LaunchConfig(request.startingCapital(), request.durationMinutes(), preset);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
        try {
            GameService.RoundView room = game.createRoom(config, playerId, true,
                    com.virtualinvestor.runtime.RunningRound.DEFAULT_STEP_MILLIS,
                    System.currentTimeMillis());
            return ResponseEntity.status(HttpStatus.CREATED).body(new RoomResponse(room.roundId(),
                    room.lifecycle().state().name(), 1, 6, playerId, room.sessionToken()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping(path = "/rooms/join", version = "1")
    public JoinResponse joinRoom(@RequestBody JoinRequest request) {
        String playerId = playerForToken(request.sessionToken());
        try {
            String seat = game.joinRoom(request.roundId(), playerId, System.currentTimeMillis());
            String state = game.runningRound(request.roundId())
                    .map(round -> round.state().name()).orElse("UNKNOWN");
            return new JoinResponse(request.roundId(), seat, state);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
    }

    @PostMapping(path = "/rooms/start", version = "1")
    public ResponseEntity<Void> startRoom(@RequestBody JoinRequest request) {
        String playerId = playerForToken(request.sessionToken());
        try {
            game.startRoom(request.roundId(), playerId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        } catch (SecurityException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        return ResponseEntity.noContent().build();
    }

    @PostMapping(path = "/rounds/live", version = "1")
    public List<GameListingResponse> liveGames() {
        return game.liveGames(System.currentTimeMillis()).stream()
                .map(game -> new GameListingResponse(game.roundId(), game.kind(), game.state(),
                        game.preset(), game.timeLeftMillis(), game.seatsTaken(), game.seatsTotal(),
                        game.leader(), game.masterPlayerId(), game.humans(),
                        game.startingCapital(), game.durationMinutes()))
                .toList();
    }

    public record GameListingResponse(String roundId, String kind, String state, String preset,
            long timeLeftMillis, int seatsTaken, int seatsTotal, String leader,
            String masterPlayerId, List<String> humans, double startingCapital,
            long durationMinutes) {
    }

    private String playerForToken(String sessionToken) {
        if (sessionToken == null || sessionToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "login required");
        }
        return game.players().playerForToken(sessionToken)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "login required"));
    }

    private static RealismPreset parsePreset(String preset) {
        try {
            return RealismPreset.valueOf(preset.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (RuntimeException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unknown preset: " + preset);
        }
    }
}
