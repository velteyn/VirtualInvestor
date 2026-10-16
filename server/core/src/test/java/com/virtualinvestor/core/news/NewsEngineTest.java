package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.domain.NewsStage;
import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.fundamental.FundamentalModel;
import com.virtualinvestor.core.instrument.Instrument;
import com.virtualinvestor.core.instrument.InstrumentType;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.random.RandomStreams;
import java.util.List;
import org.junit.jupiter.api.Test;

class NewsEngineTest {

    private static final long STEP_MILLIS = 100L;
    private static final long YEAR_MILLIS = 100_000L;

    private static FundamentalModel model() {
        Instrument instrument = new Instrument("HIGH1", "Nvido-like", "Semis", InstrumentType.EQUITY,
                1_000_000L, 1.0, 0.01, 1L, 100.0, 0.05, 1.8, 0.0);
        return new FundamentalModel(
                InstrumentUniverse.of(List.of(instrument)), new RandomStreams(1L), 0.30, 0.20, STEP_MILLIS, YEAR_MILLIS);
    }

    private static NewsItem rumor(double direction, double magnitude) {
        return new NewsItem("n1", NewsSource.THE_WIRE, NewsScope.COMPANY, "HIGH1", "Semis",
                "Rumour of a deal", "Wire report", direction, magnitude, 100L, NewsStage.RUMOR, 10L, 0.5, 100L, true);
    }

    @Test
    void confirmedStoryMovesFundamentalAtRumorThenFurther() {
        FundamentalModel model = model();
        NewsEngine engine = new NewsEngine(model, List.of(new PlannedNews(rumor(1.0, 0.10), 40L, true, 0.4)));
        double before = model.fundamental("HIGH1");

        List<GameEvent> rumorEvents = engine.advance(10L);
        double afterRumor = model.fundamental("HIGH1");
        assertTrue(afterRumor > before, "fundamental should rise at the rumor");
        assertTrue(rumorEvents.stream().anyMatch(GameEvent.NewsReleased.class::isInstance));

        List<GameEvent> resolutionEvents = engine.advance(40L);
        double afterConfirm = model.fundamental("HIGH1");
        assertTrue(afterConfirm > afterRumor, "confirmation should move the fundamental further");
        assertTrue(resolutionEvents.stream().anyMatch(GameEvent.NewsConfirmed.class::isInstance));
    }

    @Test
    void deniedStoryReversesTheRumorMove() {
        FundamentalModel model = model();
        NewsEngine engine = new NewsEngine(model, List.of(new PlannedNews(rumor(1.0, 0.10), 40L, false, 0.4)));
        double before = model.fundamental("HIGH1");

        engine.advance(10L);
        double afterRumor = model.fundamental("HIGH1");
        assertTrue(afterRumor > before);

        List<GameEvent> resolutionEvents = engine.advance(40L);
        double afterDenial = model.fundamental("HIGH1");
        assertTrue(afterDenial < afterRumor, "denial should reverse the rumor move");
        assertTrue(resolutionEvents.stream().anyMatch(GameEvent.NewsDenied.class::isInstance));
    }
}
