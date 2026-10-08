package com.virtualinvestor.edge.api;

import com.virtualinvestor.edge.game.AdminService;
import com.virtualinvestor.edge.game.GameService;
import com.virtualinvestor.edge.game.PlayerService;
import com.virtualinvestor.persist.player.PlayerStore;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Game-master backyard (player-accounts 3.1–3.3): login plus player approvals,
 * password and purse resets, and round control. All guarded by the admin gate
 * except the login itself.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final AdminService admins;
    private final PlayerService players;
    private final GameService game;

    public AdminController(AdminService admins, PlayerService players, GameService game) {
        this.admins = admins;
        this.players = players;
        this.game = game;
    }

    public record AdminLoginRequest(String password) {
    }

    public record AdminLoginResponse(String adminToken) {
    }

    public record PlayerIdRequest(String playerId) {
    }

    public record ResetPasswordRequest(String playerId, String newPassword) {
    }

    public record ResetPurseRequest(String playerId, double startingCapital) {
    }

    public record PlayerRow(String playerId, String username, String kind, String status, String bankId) {
    }

    @PostMapping(path = "/login", version = "1")
    public AdminLoginResponse login(@RequestBody AdminLoginRequest request) {
        try {
            AdminLoginResponse response = new AdminLoginResponse(admins.login(request.password()));
            log.info("admin login ok");
            return response;
        } catch (IllegalArgumentException e) {
            log.info("admin login refused");
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    public record CreatePlayerRequest(String username, String password, String bankId) {
    }

    public record UpdatePlayerRequest(String playerId, String username, String bankId) {
    }

    @PostMapping(path = "/players/create", version = "1")
    public ResponseEntity<PlayerRow> create(@RequestBody CreatePlayerRequest request) {
        try {
            PlayerStore.Player player =
                    players.store().createApproved(request.username(), request.password(), request.bankId());
            return ResponseEntity.status(HttpStatus.CREATED).body(row(player));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(path = "/players/update", version = "1")
    public PlayerRow update(@RequestBody UpdatePlayerRequest request) {
        try {
            return row(players.store().updatePlayer(request.playerId(), request.username(), request.bankId()));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(path = "/players/delete", version = "1")
    public ResponseEntity<Void> delete(@RequestBody PlayerIdRequest request) {
        try {
            players.store().deletePlayer(request.playerId());
            players.invalidatePlayerSessions(request.playerId());
            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping(path = "/players", version = "1")
    public List<PlayerRow> players() {
        return players.store().all().stream().map(player -> new PlayerRow(player.playerId(),
                player.username(), player.kind().name(), player.status().name(), player.bankId())).toList();
    }

    @PostMapping(path = "/players/approve", version = "1")
    public PlayerRow approve(@RequestBody PlayerIdRequest request) {
        PlayerStore store = players.store();
        try {
            store.approve(request.playerId());
            log.info("approve player={}", request.playerId());
            return row(store, request.playerId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping(path = "/players/reject", version = "1")
    public PlayerRow reject(@RequestBody PlayerIdRequest request) {
        PlayerStore store = players.store();
        try {
            store.reject(request.playerId());
            players.invalidatePlayerSessions(request.playerId());
            return row(store, request.playerId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    @PostMapping(path = "/players/reset-password", version = "1")
    public void resetPassword(@RequestBody ResetPasswordRequest request) {
        try {
            players.store().resetPassword(request.playerId(), request.newPassword());
            players.invalidatePlayerSessions(request.playerId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(path = "/players/reset-purse", version = "1")
    public void resetPurse(@RequestBody ResetPurseRequest request) {
        try {
            players.store().resetPurse(request.playerId(), request.startingCapital());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    public record RoundRow(String roundId, String state, long tick) {
    }

    public record RoundIdRequest(String roundId) {
    }

    @PostMapping(path = "/rounds", version = "1")
    public List<RoundRow> rounds() {
        return game.rounds().stream()
                .map(view -> game.runningRound(view.roundId())
                        .map(live -> new RoundRow(view.roundId(), live.state().name(), live.tick()))
                        .orElseGet(() -> new RoundRow(view.roundId(), "UNKNOWN", 0L)))
                .toList();
    }

    @PostMapping(path = "/rounds/pause", version = "1")
    public void pause(@RequestBody RoundIdRequest request) {
        control(request.roundId(), "pause");
    }

    @PostMapping(path = "/rounds/resume", version = "1")
    public void resume(@RequestBody RoundIdRequest request) {
        control(request.roundId(), "resume");
    }

    @PostMapping(path = "/rounds/settle", version = "1")
    public void settle(@RequestBody RoundIdRequest request) {
        try {
            game.settleEarly(request.roundId());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private void control(String roundId, String action) {
        try {
            game.control(roundId, action);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }
    }

    private static PlayerRow row(PlayerStore store, String playerId) {
        PlayerStore.Player player = store.findById(playerId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "unknown player"));
        return row(player);
    }

    private static PlayerRow row(PlayerStore.Player player) {
        return new PlayerRow(player.playerId(), player.username(), player.kind().name(),
                player.status().name(), player.bankId());
    }
}
