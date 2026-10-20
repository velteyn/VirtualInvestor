package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.news.ReliabilityTier;

/**
 * A news event as a bot sees it: a signed magnitude where positive is bullish,
 * plus its validity window. Headline reactions stop at releaseTick +
 * validityTicks (news-validity 1.1); the absorbed fundamental move persists.
 */
public record NewsSignal(double signedMagnitude, String symbol, String sector, NewsScope scope,
        ReliabilityTier tier, long releaseTick, long validityTicks) {
}
