package com.virtualinvestor.core.book;

/** A completed execution between a resting (maker) order and an incoming (taker) order. */
public record Fill(
        String makerOrderId,
        String takerOrderId,
        double price,
        long quantity,
        long makerQuantity,
        long makerRemainingAfter) {
}
