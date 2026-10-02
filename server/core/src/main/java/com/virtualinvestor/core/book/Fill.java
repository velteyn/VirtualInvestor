package com.virtualinvestor.core.book;

import com.virtualinvestor.core.domain.OrderSide;

/** A completed execution between a resting (maker) order and an incoming (taker) order. */
public record Fill(
        String makerOrderId,
        String makerParticipantId,
        OrderSide makerSide,
        String takerOrderId,
        String takerParticipantId,
        OrderSide takerSide,
        double price,
        long quantity,
        long makerQuantity,
        long makerRemainingAfter) {
}
