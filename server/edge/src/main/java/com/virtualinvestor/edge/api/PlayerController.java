package com.virtualinvestor.edge.api;

import com.virtualinvestor.edge.game.PlayerService;
import com.virtualinvestor.persist.player.PlayerStore;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

/**
 * Player registration and login over REST (player-accounts 2.1), versioned like
 * the round endpoints. Login binds the socket's participant in task 2.2; here it
 * only authenticates and issues the session token that bind consumes.
 */
@RestController
@RequestMapping("/api")
public class PlayerController {

    private final PlayerService players;

    public PlayerController(PlayerService players) {
        this.players = players;
    }

    public record RegisterRequest(String username, String password, String bankId) {
    }

    public record PlayerResponse(String playerId, String username, String kind, String status, String bankId) {
    }

    public record LoginRequest(String username, String password) {
    }

    public record LoginResponse(String playerId, String username, String status, String bankId, String sessionToken,
            boolean mustChangePassword) {
    }

    public record ChangePasswordRequest(String username, String oldPassword, String newPassword) {
    }

    public record BankResponse(String bankId, String displayName, double feePerShare, double ticketMinimum,
            double custodyRate) {
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PlayerController.class);

    @PostMapping(path = "/players/register", version = "1")
    public ResponseEntity<PlayerResponse> register(@RequestBody RegisterRequest request) {
        try {
            PlayerStore.Player player = players.register(request.username(), request.password(), request.bankId());
            log.info("register username={} bank={} -> {}", request.username(), request.bankId(),
                    player.playerId());
            return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(player));
        } catch (IllegalArgumentException e) {
            log.info("register username={} refused: {}", request.username(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    @PostMapping(path = "/players/login", version = "1")
    public LoginResponse login(@RequestBody LoginRequest request) {
        try {
            PlayerService.Login login = players.login(request.username(), request.password());
            PlayerStore.Player player = login.player();
            log.info("login username={} player={} status={}", request.username(), player.playerId(),
                    player.status());
            return new LoginResponse(player.playerId(), player.username(), player.status().name(),
                    player.bankId(), login.sessionToken(), player.mustChangePassword());
        } catch (IllegalStateException e) {
            log.info("login username={} refused: {}", request.username(), e.getMessage());
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        } catch (IllegalArgumentException e) {
            log.info("login username={} failed credentials", request.username());
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping(path = "/players/change-password", version = "1")
    public void changePassword(@RequestBody ChangePasswordRequest request) {
        try {
            players.store().changePassword(request.username(), request.oldPassword(), request.newPassword());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, e.getMessage());
        }
    }

    @PostMapping(path = "/players/banks", version = "1")
    public List<BankResponse> banks() {
        return players.store().banks().stream()
                .map(bank -> new BankResponse(bank.bankId(), bank.displayName(), bank.feePerShare(),
                        bank.ticketMinimum(), bank.custodyRate()))
                .toList();
    }

    private static PlayerResponse toResponse(PlayerStore.Player player) {
        return new PlayerResponse(player.playerId(), player.username(), player.kind().name(),
                player.status().name(), player.bankId());
    }
}
