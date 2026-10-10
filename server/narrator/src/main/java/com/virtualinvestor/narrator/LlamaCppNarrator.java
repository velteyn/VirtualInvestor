package com.virtualinvestor.narrator;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * llama.cpp sidecar narrator (newsroom-narrator 2.2): POSTs the release facts
 * to the sidecar's {@code /completion} endpoint and returns the story body.
 * Any failure — unreachable, timeout, bad JSON, denylist hit — declines, so
 * the headline stands alone per the degradation ladder. No player input ever
 * enters the prompt: only simulation facts.
 */
public final class LlamaCppNarrator implements Narrator {

    /** Real-world names that must never appear in generated bodies. */
    static final List<String> DENY_NAMES = List.of(
            "apple", "nvidia", "tesla", "amazon", "netflix", "google", "microsoft", "meta platforms",
            "uber", "doordash", "paypal", "walmart", "verizon", "t-mobile", "at&t", "intel", "amd",
            "broadcom", "oracle", "salesforce", "adobe", "shopify", "ebay", "spotify", "airbnb",
            "coinbase", "robinhood", "bloomberg", "reuters", "wall street journal", "cnbc",
            "financial times", "spacex", "openai");

    /** Advice phrasing that must never reach players. */
    static final List<String> DENY_ADVICE = List.of(
            "you should buy", "you should sell", "buy now", "sell now",
            "strong buy", "strong sell", "recommend buying", "recommend selling");

    private final URI endpoint;
    private final Duration timeout;
    private final String model;
    private final HttpClient http;

    public LlamaCppNarrator(URI endpoint, Duration timeout, String model) {
        this.endpoint = Objects.requireNonNull(endpoint, "endpoint");
        this.timeout = Objects.requireNonNull(timeout, "timeout");
        this.model = Objects.requireNonNull(model, "model");
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
    }

    @Override
    public Optional<Draft> narrate(Request request) {
        if (request == null || request.headline() == null || request.headline().isBlank()) {
            return Optional.empty();
        }
        try {
            String payload = "{\"prompt\":" + jsonString(promptFor(request))
                    + ",\"n_predict\":160,\"temperature\":0.7,\"repeat_penalty\":1.15,\"repeat_last_n\":64,"
                    + "\"penalize_nl\":false,\"stop\":[\"\\n\\n\\n\"]}";
            HttpRequest httpRequest = HttpRequest.newBuilder(endpoint.resolve("/completion"))
                    .timeout(timeout)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();
            HttpResponse<String> response =
                    http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return Optional.empty();
            }
            String body = extractContent(response.body()).map(String::trim).filter(s -> !s.isEmpty())
                    .map(LlamaCppNarrator::dedupeSentences).filter(s -> !s.isEmpty())
                    .orElse(null);
            if (body == null || denied(body)) {
                return Optional.empty();
            }
            return Optional.of(new Draft(body, model));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /** Simulation facts plus the fictional-universe rules; no player input. */
    static String promptFor(Request request) {
        String subjects = request.subjectNames().isEmpty() ? "the broad market"
                : String.join(", ", request.subjectNames());
        return "Write a news story of at most 120 words, 3 to 5 sentences, for a fictional stock-market game. "
                + "Never repeat a sentence. Never invent dollar amounts, prices, percentages, or deal values. "
                + "Facts. Headline: " + request.headline() + ". Subjects: " + subjects + ". "
                + (request.sector() == null || request.sector().isBlank() ? ""
                        : "Sector: " + request.sector() + ". ")
                + "Rules. Use ONLY these fictional company names. Never mention real companies, "
                + "products, or news outlets. Never advise buying or selling. Plain prose, no headline. Story:";
    }

    /**
     * Drop exact-duplicate sentences (small models loop): first occurrence wins,
     * order preserved. A single sentence is returned untouched.
     */
    static String dedupeSentences(String body) {
        String[] parts = body.split("(?<=\\.\\s)|(?<=!\\s)|(?<=\\?\\s)");
        java.util.LinkedHashSet<String> seen = new java.util.LinkedHashSet<>();
        StringBuilder out = new StringBuilder();
        for (String part : parts) {
            String sentence = part.trim();
            if (sentence.isEmpty()) {
                continue;
            }
            String key = sentence.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
            if (seen.add(key)) {
                if (out.length() > 0) {
                    out.append(' ');
                }
                out.append(sentence);
            }
        }
        return out.toString();
    }

    /** True when the body names reality or advises the reader: drop it. */
    static boolean denied(String body) {
        String lower = body.toLowerCase(Locale.ROOT);
        for (String name : DENY_NAMES) {
            if (lower.contains(name)) {
                return true;
            }
        }
        for (String advice : DENY_ADVICE) {
            if (lower.contains(advice)) {
                return true;
            }
        }
        return false;
    }

    /** Pull {@code content} out of a /completion JSON object (minimal scanner, no deps). */
    static Optional<String> extractContent(String json) {
        if (json == null) {
            return Optional.empty();
        }
        int key = json.indexOf("\"content\"");
        if (key < 0) {
            return Optional.empty();
        }
        int colon = json.indexOf(':', key);
        int quote = json.indexOf('"', colon);
        if (quote < 0) {
            return Optional.empty();
        }
        StringBuilder out = new StringBuilder();
        for (int i = quote + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return Optional.of(out.toString());
            }
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n' -> out.append('\n');
                    case 't' -> out.append('\t');
                    case 'r' -> out.append('\r');
                    case 'u' -> {
                        if (i + 4 < json.length()) {
                            try {
                                out.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                                i += 4;
                            } catch (NumberFormatException e) {
                                out.append(next);
                            }
                        }
                    }
                    default -> out.append(next);
                }
            } else {
                out.append(c);
            }
        }
        return Optional.empty();
    }

    private static String jsonString(String value) {
        StringBuilder out = new StringBuilder("\"");
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
