package com.virtualinvestor.edge.ws;

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

    public WebSocketConfig(MarketBroadcaster broadcaster, RoundRuntimeService runtime) {
        this.broadcaster = broadcaster;
        this.runtime = runtime;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new GameWebSocketHandler(broadcaster, runtime), "/ws")
                .setAllowedOriginPatterns("*");
    }
}
