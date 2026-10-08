package com.virtualinvestor.core.domain;

/**
 * Why an order report was emitted, modelled on FIX ExecType(150): the reason for the report,
 * distinct from the order's resulting status.
 */
public enum OrderEventKind {
    NEW,
    RESTED,
    TRIGGERED,
    PARTIAL_FILL,
    FILL,
    CANCELLED,
    REJECTED
}
