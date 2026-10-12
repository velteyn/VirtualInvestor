package com.virtualinvestor.narrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

class LlamaCppNarratorTest {

    private static Narrator.Request earnings() {
        return new Narrator.Request("sched-PEAR", "EARNINGS", "Pear Computers Inc beats quarterly expectations",
                "COMPANY", List.of("PEAR"), "Consumer Tech", List.of("Pear Computers Inc"), 0.025, 5400L);
    }

    private static HttpServer stub(String contentJson) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        byte[] body = contentJson.getBytes(StandardCharsets.UTF_8);
        server.createContext("/completion", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        return server;
    }

    @Test
    void extractsBodyFromCompletion() throws Exception {
        HttpServer server = stub("{\"content\":\"Pear beat the street. Services carried the quarter.\",\"stop\":true}");
        try {
            Narrator narrator = new LlamaCppNarrator(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(5), "llama-test");

            Narrator.Draft draft = narrator.narrate(earnings()).orElseThrow();

            assertEquals("Pear beat the street. Services carried the quarter.", draft.body());
            assertEquals("llama-test", draft.model());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void dropsBodyNamingReality() throws Exception {
        HttpServer server = stub("{\"content\":\"Even Apple could not match Pear. You should buy now.\",\"stop\":true}");
        try {
            Narrator narrator = new LlamaCppNarrator(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(5), "llama-test");

            assertTrue(narrator.narrate(earnings()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void unreachableSidecarDeclines() {
        Narrator narrator =
                new LlamaCppNarrator(URI.create("http://127.0.0.1:1"), Duration.ofSeconds(2), "llama-test");

        assertTrue(narrator.narrate(earnings()).isEmpty());
    }

    @Test
    void promptPinsFictionalUniverse() {
        String prompt = LlamaCppNarrator.promptFor(earnings());

        assertTrue(prompt.contains("Pear Computers Inc beats quarterly expectations"));
        assertTrue(prompt.contains("ONLY these fictional company names"));
        assertTrue(prompt.contains("Never advise buying or selling"));
        assertTrue(prompt.contains("economic journalist"));
        assertTrue(prompt.contains("using ONLY what is given"));
        assertTrue(prompt.contains("Never invent prices"));
    }

    @Test
    void dedupeSentencesKeepsFirstOccurrence() {
        String looped = "The gaming industry is fast-growing. The gaming industry is fast-growing. "
                + "New customers arrive daily.";

        assertEquals("The gaming industry is fast-growing. New customers arrive daily.",
                LlamaCppNarrator.dedupeSentences(looped));
    }

    @Test
    void dedupeCollapsesNearDuplicates() {
        String looped = "The market is a surreal place. The market is a surreal place! "
                + "New customers arrive daily.";

        assertEquals("The market is a surreal place. New customers arrive daily.",
                LlamaCppNarrator.dedupeSentences(looped));
    }

    @Test
    void degenerateLoopDeclines() throws Exception {
        HttpServer server = stub("{\"content\":\"The market is a surreal place. "
                + "The market is a surreal place. The market is a surreal place.\", \"stop\":true}");
        try {
            Narrator narrator = new LlamaCppNarrator(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(5), "llama-test");

            assertTrue(narrator.narrate(earnings()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void healthyBodyPassesDegeneracyCheck() {
        assertTrue(!LlamaCppNarrator.isDegenerate("Pear beat the street. Services carried the quarter."));
        assertTrue(LlamaCppNarrator.isDegenerate("The market is a surreal place. The market is a surreal place."));
    }

    @Test
    void inventedFiguresDecline() throws Exception {
        HttpServer server = stub("{\"content\":\"Buffer rose 10 percent to $59 on heavy trade.\",\"stop\":true}");
        try {
            Narrator narrator = new LlamaCppNarrator(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(5), "llama-test");

            assertTrue(narrator.narrate(earnings()).isEmpty());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void preachyFillerDeclines() {
        assertTrue(LlamaCppNarrator.denied("Past performance does not guarantee future results."));
        assertTrue(LlamaCppNarrator.denied("Seek professional advice if unsure about your investments."));
        assertTrue(!LlamaCppNarrator.denied("Pear beat the street. Services carried the quarter."));
    }

    @Test
    void malformedJsonDeclines() throws Exception {
        HttpServer server = stub("not json at all");
        try {
            Narrator narrator = new LlamaCppNarrator(
                    URI.create("http://127.0.0.1:" + server.getAddress().getPort()),
                    Duration.ofSeconds(5), "llama-test");

            assertTrue(narrator.narrate(earnings()).isEmpty());
        } finally {
            server.stop(0);
        }
    }
}
