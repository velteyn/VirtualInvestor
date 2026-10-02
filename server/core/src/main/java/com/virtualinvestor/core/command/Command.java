package com.virtualinvestor.core.command;

import com.virtualinvestor.core.domain.OrderSide;
import com.virtualinvestor.core.domain.OrderType;

/**
 * An intent submitted to the world (design D13). Commands explain why the world changed;
 * they are validated and then translated into events, which are what the world records.
 * All implementations are immutable records nested here so the set is closed and exhaustive.
 */
public sealed interface Command {

    /** Stable identity of this command for idempotency. */
    String commandId();

    long tick();

    record PlaceOrder(
            String commandId,
            String participantId,
            String symbol,
            OrderSide side,
            OrderType type,
            long quantity,
            double price,
            double triggerPrice,
            long tick) implements Command {
    }

    record ModifyOrder(
            String commandId,
            String orderId,
            double price,
            double triggerPrice,
            long tick) implements Command {
    }

    record CancelOrder(
            String commandId,
            String orderId,
            long tick) implements Command {
    }

    record SetArmed(
            String commandId,
            String participantId,
            boolean armed,
            long tick) implements Command {
    }

    record CreateRound(
            String commandId,
            String roundId,
            long seed,
            long startingCapital,
            long durationTicks,
            String preset) implements Command {

        @Override
        public long tick() {
            return 0L;
        }
    }

    record PauseRound(
            String commandId,
            String roundId,
            long tick) implements Command {
    }

    record ResumeRound(
            String commandId,
            String roundId,
            long tick) implements Command {
    }

    record JoinRound(
            String commandId,
            String roundId,
            String participantId,
            boolean virtual) implements Command {

        @Override
        public long tick() {
            return 0L;
        }
    }

    record Reconnect(
            String commandId,
            String roundId,
            String participantId,
            String sessionToken) implements Command {

        @Override
        public long tick() {
            return 0L;
        }
    }
}
