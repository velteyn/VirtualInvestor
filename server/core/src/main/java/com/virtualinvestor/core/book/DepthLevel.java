package com.virtualinvestor.core.book;

/** One aggregated price level of the book. */
public record DepthLevel(double price, long quantity, int orderCount) {
}
