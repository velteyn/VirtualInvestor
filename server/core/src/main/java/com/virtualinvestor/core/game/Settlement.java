package com.virtualinvestor.core.game;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.SeriesSymbols;
import com.virtualinvestor.core.participant.Account;
import com.virtualinvestor.core.participant.Participant;
import com.virtualinvestor.core.participant.Position;
import com.virtualinvestor.core.participant.Ranking;
import com.virtualinvestor.core.participant.Standing;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ends a round: expires futures and options in cash against their underlyings' closing
 * prices, then marks all equity positions to the closing price, force-closes every
 * position (pure intraday, so nobody carries a position past the close), computes
 * final equity, and produces the final ranking. The order is deterministic.
 */
public final class Settlement {

    /** The final standings and the events produced by closing every position. */
    public record Result(List<Standing> standings, List<GameEvent> events) {
        public Result {
            standings = List.copyOf(standings);
            events = List.copyOf(events);
        }
    }

    private Settlement() {
    }

    public static Result settle(
            Collection<Participant> participants,
            Map<String, Double> closingMarks,
            Map<String, Double> startingCapitals,
            long tick,
            String roundId) {
        List<GameEvent> events = new ArrayList<>();
        for (Participant participant : participants) {
            events.addAll(settleFutures(participant, closingMarks, tick));
        }
        for (Participant participant : participants) {
            events.addAll(settleOptions(participant, closingMarks, tick));
        }
        for (Participant participant : participants) {
            events.addAll(MarginManager.forceClose(participant, closingMarks, tick));
        }
        List<Standing> standings = Ranking.standings(participants, closingMarks, startingCapitals);
        events.add(new GameEvent.RoundSettled(roundId, tick));
        return new Result(standings, events);
    }

    /** Cash-settle every open future against its underlying's closing price. */
    static List<GameEvent> settleFutures(Participant participant, Map<String, Double> closingMarks, long tick) {
        Account account = participant.account();
        List<GameEvent> events = new ArrayList<>();
        for (Position position : new ArrayList<>(account.positions().values())) {
            Optional<SeriesSymbols.SeriesTerms> series = SeriesSymbols.parse(position.ticker());
            if (series.isEmpty() || series.get().kind() != SeriesSymbols.SeriesKind.FUTURE) {
                continue;
            }
            double settle = underlyingMark(closingMarks, series.get().underlying(), position);
            OrderSide side = position.netQty() > 0L ? OrderSide.SELL : OrderSide.BUY;
            long quantity = Math.abs(position.netQty());
            account.applyFuturesFill(position.ticker(), side, quantity, settle, 0.0);
            events.add(new GameEvent.FuturesSettled(
                    participant.id(), position.ticker(), quantity, settle, tick));
        }
        return List.copyOf(events);
    }

    /**
     * Cash-settle every open option against its underlying's closing price: in-the-money
     * contracts pay intrinsic value, out-of-the-money contracts lapse at zero.
     */
    static List<GameEvent> settleOptions(Participant participant, Map<String, Double> closingMarks, long tick) {
        Account account = participant.account();
        List<GameEvent> events = new ArrayList<>();
        for (Position position : new ArrayList<>(account.positions().values())) {
            Optional<SeriesSymbols.SeriesTerms> series = SeriesSymbols.parse(position.ticker());
            if (series.isEmpty() || !series.get().isOption()) {
                continue;
            }
            double close = underlyingMark(closingMarks, series.get().underlying(), position);
            double intrinsic = series.get().kind() == SeriesSymbols.SeriesKind.CALL
                    ? Math.max(close - series.get().strike(), 0.0)
                    : Math.max(series.get().strike() - close, 0.0);
            OrderSide side = position.netQty() > 0L ? OrderSide.SELL : OrderSide.BUY;
            long quantity = Math.abs(position.netQty());
            account.applyOptionFill(position.ticker(), side, quantity, intrinsic);
            events.add(new GameEvent.OptionSettled(
                    participant.id(), position.ticker(), quantity, intrinsic, tick));
        }
        return List.copyOf(events);
    }

    private static double underlyingMark(Map<String, Double> closingMarks, String underlying, Position position) {
        Double close = closingMarks.get(underlying);
        if (close != null) {
            return close;
        }
        return closingMarks.getOrDefault(position.ticker(), position.avgPrice());
    }
}
