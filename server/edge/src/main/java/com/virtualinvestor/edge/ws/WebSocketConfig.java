package com.virtualinvestor.edge.ws;

import com.virtualinvestor.edge.game.GameService;
import com.virtualinvestor.edge.game.PlayerService;
import com.virtualinvestor.edge.game.RoundRuntimeService;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** Registers the game WebSocket at {@code /ws}. */
@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final MarketBroadcaster broadcaster;
    private final RoundRuntimeService runtime;
    private final PlayerService players;
    private final GameService game;

    public WebSocketConfig(MarketBroadcaster broadcaster, RoundRuntimeService runtime,
            PlayerService players, GameService game) {
        this.broadcaster = broadcaster;
        this.runtime = runtime;
        this.players = players;
        this.game = game;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new GameWebSocketHandler(broadcaster, runtime, players, game::seatFor), "/ws")
                .setAllowedOriginPatterns("*");
    }
}
