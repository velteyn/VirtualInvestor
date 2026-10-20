package com.virtualinvestor.core.news;

import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.fundamental.VolatilityState;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Applies staged news to the market. At the rumor tick it moves the fundamental by the rumor
 * fraction and emits a RUMOR release; at the resolution tick it confirms (applying the
 * remaining impact) or denies (reversing the rumor impact) and emits the matching event.
 */
public final class NewsEngine {

    private final FundamentalModel fundamentals;
    private final List<PlannedNews> timeline;

    private VolatilityState volatilityState;

    public NewsEngine(FundamentalModel fundamentals, List<PlannedNews> timeline) {
        this.fundamentals = Objects.requireNonNull(fundamentals, "fundamentals");
        this.timeline = List.copyOf(timeline);
    }

    /** Attach a volatility state so significant news also elevates it for the affected names. */
    public void setVolatilityState(VolatilityState volatilityState) {
        this.volatilityState = volatilityState;
    }

    public List<GameEvent> advance(long tick) {
        List<GameEvent> events = new ArrayList<>();
        for (PlannedNews planned : timeline) {
            NewsItem rumor = planned.rumorItem();
            if (rumor.tick() == tick) {
                double piece = rumor.signedMagnitude() * planned.rumorFraction();
                apply(rumor, piece);
                events.add(new GameEvent.NewsReleased(
                        rumor.id(), rumor.source().name(), rumor.scope(), rumor.symbol(), rumor.sector(),
                        rumor.headline(), rumor.body(), piece, rumor.durationTicks(), NewsStage.RUMOR, tick,
                        rumor.tier()));
            }
            if (planned.resolutionTick() == tick) {
                if (planned.confirmed()) {
                    double remaining = rumor.signedMagnitude() * (1.0 - planned.rumorFraction());
                    apply(rumor, remaining);
                    // The wire carries the stage's lede, never the rumor text twice.
                    String lede = rumor.confirmHeadline() != null ? rumor.confirmHeadline() : rumor.headline();
                    events.add(new GameEvent.NewsConfirmed(rumor.id(), lede, tick));
                } else {
                    double reverse = -rumor.signedMagnitude() * planned.rumorFraction();
                    apply(rumor, reverse);
                    String lede = rumor.denyHeadline() != null ? rumor.denyHeadline() : rumor.headline();
                    events.add(new GameEvent.NewsDenied(rumor.id(), lede, tick));
                }
            }
        }
        return List.copyOf(events);
    }

    private void apply(NewsItem item, double signedMagnitude) {
        if (signedMagnitude == 0.0) {
            return;
        }
        List<String> affected = fundamentals.applyNews(item.scope(), item.symbol(), item.sector(), signedMagnitude);
        if (volatilityState != null) {
            volatilityState.bumpAll(affected, Math.abs(signedMagnitude));
        }
    }
}
