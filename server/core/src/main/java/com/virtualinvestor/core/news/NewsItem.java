package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import java.util.Objects;

/**
 * A news event: who published it, what it concerns, how hard it hits, and how long its effect
 * lasts. {@code direction} is -1, 0 or 1 and {@code magnitude} is non-negative, so the signed
 * impact is {@link #signedMagnitude()}.
 */
public record NewsItem(
        String id,
        NewsSource source,
        NewsScope scope,
        String symbol,
        String sector,
        String headline,
        String body,
        double direction,
        double magnitude,
        long durationTicks,
        NewsStage stage,
        long tick,
        double rumorFraction,
        long resolutionDelayTicks,
        boolean confirmed) {

    public NewsItem {
        requireText(id, "id");
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(stage, "stage");
        requireText(headline, "headline");
        if (direction != -1.0 && direction != 0.0 && direction != 1.0) {
            throw new IllegalArgumentException("direction must be -1, 0 or 1, was " + direction);
        }
        if (magnitude < 0.0) {
            throw new IllegalArgumentException("magnitude must not be negative");
        }
        if (durationTicks < 0L) {
            throw new IllegalArgumentException("durationTicks must not be negative");
        }
        if (scope == NewsScope.COMPANY && isBlank(symbol)) {
            throw new IllegalArgumentException("company news requires a symbol");
        }
        if (scope == NewsScope.SECTOR && isBlank(sector)) {
            throw new IllegalArgumentException("sector news requires a sector");
        }
        if (FictionalNames.containsRealName(headline) || FictionalNames.containsRealName(body)) {
            throw new IllegalArgumentException("news text references a real company or outlet");
        }
        if (rumorFraction <= 0.0 || rumorFraction > 1.0) {
            throw new IllegalArgumentException("rumorFraction must be within (0, 1]");
        }
        if (resolutionDelayTicks < 0L) {
            throw new IllegalArgumentException("resolutionDelayTicks must not be negative");
        }
    }

    public double signedMagnitude() {
        return direction * magnitude;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
    }
}
