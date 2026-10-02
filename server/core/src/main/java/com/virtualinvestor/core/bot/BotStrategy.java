package com.virtualinvestor.core.bot;

import java.util.List;

/**
 * A virtual trader's decision rule. Each strategy is stateless per tick; any state that
 * matters (inventory, cash) arrives in the context, so decisions stay deterministic.
 */
public interface BotStrategy {

    List<OrderIntent> decide(BotContext context);
}
