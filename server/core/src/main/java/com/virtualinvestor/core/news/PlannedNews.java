package com.virtualinvestor.core.news;

/**
 * A news story staged over time: a rumor is released at the item's tick with a fraction of its
 * impact, and the story is confirmed or denied at {@code resolutionTick}, applying the rest of
 * the impact or reversing the rumor move.
 */
public record PlannedNews(NewsItem rumorItem, long resolutionTick, boolean confirmed, double rumorFraction) {

    public PlannedNews {
        if (rumorItem == null) {
            throw new IllegalArgumentException("rumorItem is required");
        }
        if (resolutionTick < rumorItem.tick()) {
            throw new IllegalArgumentException("resolutionTick must not precede the rumor");
        }
        if (rumorFraction <= 0.0 || rumorFraction > 1.0) {
            throw new IllegalArgumentException("rumorFraction must be within (0, 1]");
        }
    }
}
