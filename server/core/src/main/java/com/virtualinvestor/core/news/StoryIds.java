package com.virtualinvestor.core.news;

/**
 * Story identity for staged beats (narrator-arcs): every scenario item id ends
 * in {@code -b<k>} ({@code scen-takeover-0-b2}); stripping the suffix gives the
 * arc both the planner and the newsroom desk group by. Single-beat items are
 * beats too ({@code -b0}) with no priors.
 */
public final class StoryIds {

    private StoryIds() {
    }

    public static boolean isBeat(String newsId) {
        return newsId != null && newsId.matches(".*-b\\d+$");
    }

    /** The arc a beat belongs to; the id itself when it is not a beat. */
    public static String storyOf(String newsId) {
        if (!isBeat(newsId)) {
            return newsId;
        }
        return newsId.substring(0, newsId.lastIndexOf("-b"));
    }

    public static String beatId(String storyId, int beat) {
        return storyId + "-b" + beat;
    }
}
