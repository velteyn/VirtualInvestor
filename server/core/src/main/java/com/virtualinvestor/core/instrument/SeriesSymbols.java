package com.virtualinvestor.core.instrument;

import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The symbol convention for derivative series (design D1). A series symbol names its
 * underlying outright so halt propagation, margining, and settlement can resolve the
 * underlying without a side catalog:
 *
 * <ul>
 *   <li>Futures: {@code NVDO-F}</li>
 *   <li>Calls: {@code NVDO-C640}, puts: {@code NVDO-P640}</li>
 * </ul>
 *
 * <p>Strikes are canonical decimal text on the series tick grid, so formatting then
 * parsing round-trips exactly.
 */
public final class SeriesSymbols {

    private static final Pattern SERIES =
            Pattern.compile("^([A-Z0-9]+)-(F|C(\\d+(?:\\.\\d+)?)|P(\\d+(?:\\.\\d+)?))$");

    /** What a series symbol denotes. Multiplier is fixed at 1.0 in v1. */
    public enum SeriesKind {
        FUTURE,
        CALL,
        PUT
    }

    /** Resolved terms of a series symbol. {@code strike} is NaN for futures. */
    public record SeriesTerms(SeriesKind kind, String underlying, double strike, double multiplier) {
        public SeriesTerms {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(underlying, "underlying");
            if (multiplier <= 0.0) {
                throw new IllegalArgumentException("multiplier must be positive, was " + multiplier);
            }
        }

        public boolean isOption() {
            return kind == SeriesKind.CALL || kind == SeriesKind.PUT;
        }
    }

    private SeriesSymbols() {
    }

    public static String futureSymbol(String underlying) {
        requireUnderlying(underlying);
        return underlying + "-F";
    }

    public static String callSymbol(String underlying, double strike) {
        return optionSymbol(underlying, strike, "C");
    }

    public static String putSymbol(String underlying, double strike) {
        return optionSymbol(underlying, strike, "P");
    }

    private static String optionSymbol(String underlying, double strike, String leg) {
        requireUnderlying(underlying);
        if (!Double.isFinite(strike) || strike <= 0.0) {
            throw new IllegalArgumentException("strike must be a positive finite price, was " + strike);
        }
        return underlying + "-" + leg + strikeText(strike);
    }

    /** Canonical strike text: plain decimal with trailing zeros stripped. */
    public static String strikeText(double strike) {
        return new BigDecimal(Double.toString(strike)).stripTrailingZeros().toPlainString();
    }

    /** Resolve a series symbol, or empty when the symbol is not a series. */
    public static Optional<SeriesTerms> parse(String symbol) {
        if (symbol == null) {
            return Optional.empty();
        }
        Matcher matcher = SERIES.matcher(symbol);
        if (!matcher.matches()) {
            return Optional.empty();
        }
        String underlying = matcher.group(1);
        String leg = matcher.group(2);
        if (leg.equals("F")) {
            return Optional.of(new SeriesTerms(SeriesKind.FUTURE, underlying, Double.NaN, 1.0));
        }
        String strikeText = leg.startsWith("C") ? matcher.group(3) : matcher.group(4);
        double strike = Double.parseDouble(strikeText);
        if (!(strike > 0.0)) {
            return Optional.empty();
        }
        SeriesKind kind = leg.startsWith("C") ? SeriesKind.CALL : SeriesKind.PUT;
        return Optional.of(new SeriesTerms(kind, underlying, strike, 1.0));
    }

    public static boolean isSeriesSymbol(String symbol) {
        return parse(symbol).isPresent();
    }

    private static void requireUnderlying(String underlying) {
        if (underlying == null || underlying.isBlank()) {
            throw new IllegalArgumentException("underlying must not be blank");
        }
        if (!underlying.matches("[A-Z0-9]+")) {
            throw new IllegalArgumentException("underlying must be an uppercase alphanumeric ticker, was " + underlying);
        }
    }
}
