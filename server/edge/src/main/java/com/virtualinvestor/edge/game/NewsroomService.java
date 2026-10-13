package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.edge.wire.WireMessage;
import com.virtualinvestor.edge.ws.MarketBroadcaster;
import com.virtualinvestor.narrator.Narrator;
import com.virtualinvestor.persist.article.ArticleStore;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Newsroom fan-out (newsroom-narrator 2.3): the edge-side subscriber that turns
 * releases into articles out-of-band. Headlines always publish instantly with
 * empty bodies (today's path, untouched); selected releases are narrated on a
 * single background thread, stored, and re-broadcast under the same
 * {@code newsId} with the body filled. The marker for "article arrived" is a
 * non-empty body — the stage keeps its original value, so no core change.
 *
 * <p>Selection (measured in 1.1: ~32 releases per REALISTIC 30m round, all 20
 * earnings clustered before 40%): earnings, sector and macro releases are
 * eligible; random company noise is skipped. A per-round cap of 40 with
 * skip-on-overflow keeps prose from backpressuring the market.
 */
@Service
public class NewsroomService {

    static final int MAX_ARTICLES_PER_ROUND = 40;

    private static final Logger log = LoggerFactory.getLogger(NewsroomService.class);

    private final Narrator narrator;
    private final ArticleStore articles;
    private final InstrumentUniverse universe;
    private final MarketBroadcaster broadcaster;
    private final ExecutorService desk = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "newsroom-desk");
        thread.setDaemon(true);
        return thread;
    });
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    /** Latest tick observed per round, fed from the event stream. */
    private final Map<String, Long> roundTicks = new ConcurrentHashMap<>();

    public NewsroomService(Narrator narrator, javax.sql.DataSource dataSource,
            MarketBroadcaster broadcaster) {
        this.narrator = narrator;
        this.articles = new ArticleStore(dataSource);
        this.universe = InstrumentUniverse.defaultUniverse();
        this.broadcaster = broadcaster;
    }

    /** A release hit the wire: narrate it later if selected. Never blocks. */
    public void accept(String roundId, GameEvent.NewsReleased news) {
        ArticleStore.Kind kind = kindOf(news);
        if (kind == null) {
            return;
        }
        AtomicInteger count = counts.computeIfAbsent(roundId, id -> new AtomicInteger());
        if (count.get() >= MAX_ARTICLES_PER_ROUND) {
            return;
        }
        count.incrementAndGet();
        desk.submit(() -> write(roundId, news, kind));
    }

    /** Round preview at start (newsroom-narrator spec: preview kind). */
    public void preview(String roundId, String preset, double startingCapital) {
        desk.submit(() -> {
            try {
                String headline = "Round " + roundId + " opens: " + preset + " session";
                Narrator.Request request = new Narrator.Request("preview-" + roundId, "PREVIEW", headline,
                        "MACRO", List.of(), null, List.of(), 0.0, 0L);
                narrator.narrate(request).ifPresent(
                        draft -> publish(roundId, request, ArticleStore.Kind.PREVIEW, headline,
                                "A " + preset + " session with $" + (long) startingCapital + " starting capital. "
                                        + draft.body(),
                                draft.model()));
            } catch (Exception e) {
                // The desk must never die silently: a failed preview is a bare
                // headline, not a wedged newsroom (playtest: zero articles, no log).
                log.warn("newsroom preview failed for {}: {}", roundId, e.toString());
            }
        });
    }

    /** Round recap at settle (newsroom-narrator spec: recap kind). */
    public void recap(String roundId, long tick) {
        desk.submit(() -> {
            try {
                String headline = "Round " + roundId + " settles";
                Narrator.Request request = new Narrator.Request("recap-" + roundId, "RECAP", headline,
                        "MACRO", List.of(), null, List.of(), 0.0, tick);
                narrator.narrate(request).ifPresent(
                        draft -> publish(roundId, request, ArticleStore.Kind.RECAP, headline, draft.body(),
                                draft.model()));
            } catch (Exception e) {
                log.warn("newsroom recap failed for {}: {}", roundId, e.toString());
            }
        });
    }

    /** Track the round clock so stale releases can be shed before generating. */
    public void noteTick(String roundId, long tick) {
        roundTicks.merge(roundId, tick, Math::max);
    }

    /** A release whose validity window closed before its turn is pure waste. */
    static boolean isStale(long releaseTick, long durationTicks, Long nowTick) {
        return nowTick != null && nowTick - releaseTick > durationTicks;
    }

    /** Forget per-round counts with the round (room expiry, settle). */
    public void forget(String roundId) {
        counts.remove(roundId);
        roundTicks.remove(roundId);
    }

    private void write(String roundId, GameEvent.NewsReleased news, ArticleStore.Kind kind) {
        try {
            Long now = roundTicks.get(roundId);
            if (isStale(news.tick(), news.durationTicks(), now)) {
                log.info("newsroom stale-skipped {} (lag {} ticks)", news.newsId(), now - news.tick());
                return;
            }
            long startNanos = System.nanoTime();
            List<String> tickers = news.symbol() == null ? List.of() : List.of(news.symbol());
            List<String> names = new ArrayList<>();
            for (String ticker : tickers) {
                universe.find(ticker).map(instrument -> instrument.name()).ifPresent(names::add);
            }
            Narrator.Request request = new Narrator.Request(news.newsId(), kind.name(), news.headline(),
                    news.scope().name(), tickers, news.sector(), names, news.magnitude(), news.tick());
            var draft = narrator.narrate(request);
            long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000L;
            if (draft.isEmpty()) {
                log.info("newsroom declined {} in {}ms", news.newsId(), elapsedMillis);
                return;
            }
            log.info("newsroom article {} published in {}ms", news.newsId(), elapsedMillis);
            Narrator.Draft body = draft.orElseThrow();
            {
                ArticleStore.Article article = new ArticleStore.Article(request.newsId(), roundId,
                        request.tick(), kind, request.tickers(), news.headline(), body.body(),
                        body.model());
                try {
                    articles.save(article);
                } catch (Exception e) {
                    log.warn("newsroom store failed for {}: {}", request.newsId(), e.toString());
                    return;
                }
                // Same newsId, same addressing, original stage: only the body fills in,
                // so the feed upsert is a pure content update.
                broadcaster.broadcast(roundId, new WireMessage.NewsDto(news.newsId(), news.source(),
                        news.scope().name(), news.symbol(), news.sector(), news.headline(), body.body(),
                        news.magnitude(), news.durationTicks(), news.stage().name(), news.tick()));
            }
        } catch (Exception e) {
            log.warn("newsroom write failed for {}: {}", news.newsId(), e.toString());
        }
    }

    private void publish(String roundId, Narrator.Request request, ArticleStore.Kind kind, String headline,
            String body, String model) {
        ArticleStore.Article article = new ArticleStore.Article(request.newsId(), roundId, request.tick(),
                kind, request.tickers(), headline, body, model);
        try {
            articles.save(article);
        } catch (Exception e) {
            log.warn("newsroom store failed for {}: {}", request.newsId(), e.toString());
            return;
        }
        broadcaster.broadcast(roundId, new WireMessage.NewsDto(request.newsId(), "NEWSROOM", request.scope(), null,
                null, headline, body, request.magnitude(), 0L, "CONFIRMED", request.tick()));
    }

    /** Earnings, sector, macro and structural news are eligible; random company noise is skipped. */
    static ArticleStore.Kind kindOf(GameEvent.NewsReleased news) {
        return switch (news.scope()) {
            case SECTOR -> ArticleStore.Kind.SECTOR;
            case MACRO, STRUCTURE -> ArticleStore.Kind.MACRO;
            case COMPANY -> news.newsId() != null && news.newsId().startsWith("sched-")
                    ? ArticleStore.Kind.EARNINGS
                    : null;
        };
    }
}
