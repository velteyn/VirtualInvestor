package com.virtualinvestor.core.news;

/**
 * The fictional wire services that publish news in the simulation. None of these is a real
 * outlet; {@link #fromToken(String)} refuses anything else so a real source can never enter
 * the market.
 */
public enum NewsSource {
    THE_WIRE,
    MERIDIAN_NEWS,
    TAPE;

    public static NewsSource fromToken(String token) {
        if (token == null) {
            throw new IllegalArgumentException("news source is required");
        }
        String normalized = token.trim().toUpperCase(java.util.Locale.ROOT).replace(' ', '_');
        for (NewsSource source : values()) {
            if (source.name().equals(normalized)) {
                return source;
            }
        }
        throw new IllegalArgumentException("unknown or non-fictional news source: " + token);
    }
}
