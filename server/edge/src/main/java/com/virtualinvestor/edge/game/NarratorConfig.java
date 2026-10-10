package com.virtualinvestor.edge.game;

import com.virtualinvestor.narrator.DisabledNarrator;
import com.virtualinvestor.narrator.LlamaCppNarrator;
import com.virtualinvestor.narrator.Narrator;
import com.virtualinvestor.narrator.TemplateNarrator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Newsroom wiring (newsroom-narrator 2.1). The narrator mode comes from the
 * environment so compose, dev machines and tests each choose: {@code disabled}
 * declines everything (bare headlines, pre-newsroom behavior), {@code template}
 * writes deterministic bodies with no model (the default), {@code llama} uses
 * the sidecar client (2.2; falls back to templates on any failure, per the
 * degradation ladder).
 */
@Configuration
public class NarratorConfig {

    private static final Logger log = LoggerFactory.getLogger(NarratorConfig.class);

    @Bean
    public Narrator narrator() {
        String mode = System.getenv().getOrDefault("VI_NARRATOR", "template").toLowerCase();
        return switch (mode) {
            case "disabled" -> new DisabledNarrator();
            case "llama" -> new LlamaCppNarrator(
                    java.net.URI.create(System.getenv().getOrDefault("VI_NARRATOR_URL", "http://127.0.0.1:18080")),
                    java.time.Duration.ofMillis(Long.parseLong(
                            System.getenv().getOrDefault("VI_NARRATOR_TIMEOUT_MS", "20000"))),
                    System.getenv().getOrDefault("VI_NARRATOR_MODEL", "qwen2.5-0.5b-q8"));
            default -> {
                if (!mode.equals("template")) {
                    log.warn("unknown narrator mode '{}'; using templates", mode);
                }
                yield new TemplateNarrator();
            }
        };
    }
}
