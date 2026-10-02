package com.virtualinvestor.core.bot;

import com.virtualinvestor.core.domain.NewsScope;

/** A news event as a bot sees it: a signed magnitude where positive is bullish. */
public record NewsSignal(double signedMagnitude, String symbol, String sector, NewsScope scope) {
}
