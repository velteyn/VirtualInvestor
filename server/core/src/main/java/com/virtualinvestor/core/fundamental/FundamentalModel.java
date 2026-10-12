package com.virtualinvestor.core.fundamental;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.DeterministicRandom;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The latent fundamental value process (design D5). A market factor and per-sector factors
 * drive each instrument through its beta exposures, plus idiosyncratic diffusion. Values
 * evolve multiplicatively so they stay positive, and every draw comes from a per-subsystem
 * seeded stream in canonical order, keeping the process deterministic and replayable.
 *
 * <p>Annualized volatilities are scaled to the step by {@code sqrt(stepMillis / yearMillis)};
 * correlations between instruments are independent of that scaling.
 */
public final class FundamentalModel {

    private final InstrumentUniverse universe;
    private final DeterministicRandom marketRandom;
    private final Map<String, DeterministicRandom> sectorRandom;
    private final Map<String, DeterministicRandom> idioRandom;
    private final Map<String, Double> sectorFactor;
    private final Map<String, Double> sectorIncrement;
    private final Map<String, Double> fundamental;
    private final Map<String, Double> lastReturn;

    private final double marketStepVol;
    private final double sectorStepVol;
    private final double stepFraction;

    private VolatilityState volatilityState;
    private OrderFlowImpact orderFlowImpact;
    private double reversionStrength;
    private final Map<String, Double> reference = new LinkedHashMap<>();
    private double marketFactor;

    public FundamentalModel(
            InstrumentUniverse universe,
            RandomStreams streams,
            double marketAnnualVol,
            double sectorAnnualVol,
            long stepMillis,
            long yearMillis) {
        this.universe = Objects.requireNonNull(universe, "universe");
        Objects.requireNonNull(streams, "streams");
        if (marketAnnualVol < 0.0 || sectorAnnualVol < 0.0) {
            throw new IllegalArgumentException("volatilities must not be negative");
        }
        if (stepMillis <= 0L || yearMillis <= 0L) {
            throw new IllegalArgumentException("stepMillis and yearMillis must be positive");
        }
        this.stepFraction = Math.sqrt((double) stepMillis / (double) yearMillis);
        this.marketStepVol = marketAnnualVol * stepFraction;
        this.sectorStepVol = sectorAnnualVol * stepFraction;
        this.marketRandom = streams.stream("fundamental.market");
        this.sectorRandom = new LinkedHashMap<>();
        this.sectorFactor = new LinkedHashMap<>();
        this.sectorIncrement = new LinkedHashMap<>();
        this.fundamental = new LinkedHashMap<>();
        this.idioRandom = new LinkedHashMap<>();
        this.lastReturn = new LinkedHashMap<>();

        for (String sector : new LinkedHashSet<>(universe.spots().stream().map(Instrument::sector).toList())) {
            sectorRandom.put(sector, streams.stream("fundamental.sector." + sector));
            sectorFactor.put(sector, 0.0);
            sectorIncrement.put(sector, 0.0);
        }
        for (Instrument instrument : universe.spots()) {
            idioRandom.put(instrument.ticker(), streams.stream("fundamental.idio." + instrument.ticker()));
            fundamental.put(instrument.ticker(), instrument.referencePrice());
            reference.put(instrument.ticker(), instrument.referencePrice());
            lastReturn.put(instrument.ticker(), 0.0);
        }
    }

    /**
     * Attach a volatility state so a news-driven elevation scales each instrument's
     * idiosyncratic diffusion. Null keeps base volatility.
     */
    public void setVolatilityState(VolatilityState volatilityState) {
        this.volatilityState = volatilityState;
    }

    /**
     * Enable order-flow impact (design D1). When set, net aggressive flow moves the fundamental
     * in addition to the factor, diffusion and jump terms.
     */
    public void setOrderFlowImpact(OrderFlowImpact orderFlowImpact) {
        this.orderFlowImpact = orderFlowImpact;
    }

    /**
     * The strength of the pull that tethers the fundamental back toward its reference each step
     * (0 keeps no pull). Must be within [0, 1).
     */
    public void setReversionStrength(double reversionStrength) {
        if (reversionStrength < 0.0 || reversionStrength >= 1.0) {
            throw new IllegalArgumentException("reversionStrength must be within [0, 1)");
        }
        this.reversionStrength = reversionStrength;
    }

    /**
     * Apply one step's net aggressive flow to an instrument and return the moved fundamental.
     * A no-op when no impact is configured.
     */
    public double applyFlow(String ticker, double netFlow) {
        if (orderFlowImpact == null || netFlow == 0.0) {
            return fundamental(ticker);
        }
        // The permanent part moves the reference too, so reversion preserves it; the transient
        // part moves only the fundamental.
        double permanent = orderFlowImpact.permanentFraction(ticker, netFlow);
        reference.merge(ticker, permanent, (current, fraction) -> Math.max(0.01, current * (1.0 + fraction)));
        double moved = orderFlowImpact.apply(fundamental(ticker), ticker, netFlow);
        fundamental.put(ticker, moved);
        return moved;
    }

    /** Advance every factor and fundamental value by one step. */
    public void step() {
        double marketIncrement = marketStepVol * marketRandom.nextGaussian();
        marketFactor += marketIncrement;

        for (Map.Entry<String, DeterministicRandom> entry : sectorRandom.entrySet()) {
            double increment = sectorStepVol * entry.getValue().nextGaussian();
            sectorIncrement.put(entry.getKey(), increment);
            sectorFactor.merge(entry.getKey(), increment, Double::sum);
        }

        for (Instrument instrument : universe.spots()) {
            String ticker = instrument.ticker();
            double multiplier = volatilityState == null ? 1.0 : volatilityState.multiplier(ticker);
            double idio = instrument.baseVolatility() * multiplier * stepFraction * idioRandom.get(ticker).nextGaussian();
            double sectorIncrementFor = sectorIncrement.get(instrument.sector());
            double instrumentReturn = instrument.marketBeta() * marketIncrement
                    + instrument.sectorBeta() * sectorIncrementFor
                    + idio;
            lastReturn.put(ticker, instrumentReturn);
            fundamental.merge(ticker, instrumentReturn, (current, value) -> current * Math.exp(value));

            // The reference drifts with the same factors; the fundamental is pulled toward it
            // so a flow-driven deviation reverts rather than persisting forever.
            reference.merge(ticker, instrumentReturn, (current, value) -> current * Math.exp(value));
            if (reversionStrength > 0.0) {
                double ref = reference.get(ticker);
                double current = fundamental.get(ticker);
                fundamental.put(ticker, current + reversionStrength * (ref - current));
            }
        }
    }

    /** The configured order-flow impact, or null when disabled. */
    public OrderFlowImpact orderFlowImpact() {
        return orderFlowImpact;
    }

    public double reversionStrength() {
        return reversionStrength;
    }

    /** The reference value the fundamental reverts toward (the factor/diffusion drift path). */
    public double reference(String ticker) {
        Double value = reference.get(ticker);
        if (value == null) {
            throw new IllegalArgumentException("unknown instrument: " + ticker);
        }
        return value;
    }

    /** Apply a discrete jump to one instrument's fundamental value (news, design D5). */
    public void jump(String ticker, double fraction) {
        requireKnown(ticker);
        fundamental.merge(ticker, fraction, (current, value) -> Math.max(0.01, current * (1.0 + value)));
    }

    /**
     * Apply a news event's impact to the instruments its scope covers, returning the affected
     * tickers. COMPANY moves one name; SECTOR moves every name in the sector; MACRO moves the
     * whole market scaled by each instrument's market beta; STRUCTURE moves the named symbol.
     */
    public List<String> applyNews(NewsScope scope, String symbol, String sector, double magnitude) {
        List<String> affected = new ArrayList<>();
        switch (scope) {
            case COMPANY -> {
                jump(symbol, magnitude);
                affected.add(symbol);
            }
            case STRUCTURE -> {
                jump(symbol, magnitude);
                affected.add(symbol);
            }
            case SECTOR -> {
                for (Instrument instrument : universe.spots()) {
                    if (instrument.sector().equals(sector)) {
                        jump(instrument.ticker(), magnitude);
                        affected.add(instrument.ticker());
                    }
                }
            }
            case MACRO -> {
                for (Instrument instrument : universe.spots()) {
                    jump(instrument.ticker(), magnitude * instrument.marketBeta());
                    affected.add(instrument.ticker());
                }
            }
        }
        return List.copyOf(affected);
    }

    public double marketFactor() {
        return marketFactor;
    }

    public double sectorFactor(String sector) {
        Double value = sectorFactor.get(sector);
        if (value == null) {
            throw new IllegalArgumentException("unknown sector: " + sector);
        }
        return value;
    }

    public double fundamental(String ticker) {
        return requireKnown(ticker);
    }

    /** The return applied to an instrument on the most recent step. */
    public double lastReturn(String ticker) {
        requireKnown(ticker);
        return lastReturn.get(ticker);
    }

    private double requireKnown(String ticker) {
        Double value = fundamental.get(ticker);
        if (value == null) {
            throw new IllegalArgumentException("unknown instrument: " + ticker);
        }
        return value;
    }
}
