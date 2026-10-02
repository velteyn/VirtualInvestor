package com.virtualinvestor.core.rules;

/** Outcome of a pre-trade validation. */
public record ValidationResult(boolean valid, String reason) {

    private static final ValidationResult OK = new ValidationResult(true, null);

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult reject(String reason) {
        return new ValidationResult(false, reason);
    }
}
