package com.virtualinvestor.edge.game;

import com.virtualinvestor.core.event.GameEvent;
import com.virtualinvestor.core.instrument.InstrumentUniverse;
import com.virtualinvestor.core.news.ScenarioDeck;
import com.virtualinvestor.core.news.StoryIds;
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
    /**
     * One desk, priority-ordered (narrator-arcs 2.3): arc beats jump the
     * FIFO queue so slow important work is never starved by routine
     * releases. Single thread still — generations are CPU-bound, so a second
     * worker would only slow both. Sequence breaks ties within a lane.
     */
    private final java.util.concurrent.atomic.AtomicLong deskSeq = new java.util.concurrent.atomic.AtomicLong();
    private final ExecutorService desk = new java.util.concurrent.ThreadPoolExecutor(
            1, 1, 0L, java.util.concurrent.TimeUnit.SECONDS,
            new java.util.concurrent.PriorityBlockingQueue<>(),
            r -> {
                Thread thread = new Thread(r, "newsroom-desk");
                thread.setDaemon(true);
                return thread;
            });

    /** A unit of desk work ordered by lane (arcs first), then arrival. */
    static final class DeskTask implements Runnable, Comparable<DeskTask> {
        private final long seq;
        private final int lane;
        private final Runnable job;

        DeskTask(long seq, int lane, Runnable job) {
            this.seq = seq;
            this.lane = lane;
            this.job = job;
        }

        @Override
        public void run() {
            job.run();
        }

        @Override
        public int compareTo(DeskTask other) {
            int laneOrder = Integer.compare(lane, other.lane);
            return laneOrder != 0 ? laneOrder : Long.compare(seq, other.seq);
        }
    }

    /** Lane 0 jumps the queue: scenario beats. Everything else is lane 1. */
    static int laneOf(String newsId) {
        return newsId != null && newsId.startsWith("scen-") ? 0 : 1;
    }

    private void submit(int lane, Runnable job) {
        desk.execute(new DeskTask(deskSeq.getAndIncrement(), lane, job));
    }
    private final Map<String, AtomicInteger> counts = new ConcurrentHashMap<>();
    /** Latest tick observed per round, fed from the event stream. */
    private final Map<String, Long> roundTicks = new ConcurrentHashMap<>();
    /** Last print per symbol per round: the desk's own market mirror. */
    private final Map<String, Map<String, Double>> marksByRound = new ConcurrentHashMap<>();
    /** Symbols currently halted per round (cleared by their next print). */
    private final Map<String, java.util.Set<String>> haltedByRound = new ConcurrentHashMap<>();
    /** Last narrated mark per story, for move-since-last-beat consequences. */
    private final Map<String, Double> storyMarks = new ConcurrentHashMap<>();

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
        submit(laneOf(news.newsId()), () -> write(roundId, news, kind));
    }

    /** Round preview at start (newsroom-narrator spec: preview kind). */
    public void preview(String roundId, String preset, double startingCapital) {
        submit(1, () -> {
            try {
                String headline = "Round " + roundId + " opens: " + preset + " session";
                Narrator.Request request = new Narrator.Request("preview-" + roundId, "PREVIEW", headline,
                        "MACRO", List.of(), null, List.of(), 0.0, 0L, "", "");
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
        submit(1, () -> {
            try {
                String headline = "Round " + roundId + " settles";
                Narrator.Request request = new Narrator.Request("recap-" + roundId, "RECAP", headline,
                        "MACRO", List.of(), null, List.of(), 0.0, tick, "", "");
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

    /** A print refreshes the desk mirror and reopens a halted book. */
    public void noteTrade(String roundId, String symbol, double price) {
        if (symbol != null && Double.isFinite(price) && price > 0.0) {
            marksByRound.computeIfAbsent(roundId, id -> new ConcurrentHashMap<>()).put(symbol, price);
            java.util.Set<String> halted = haltedByRound.get(roundId);
            if (halted != null) {
                halted.remove(symbol);
            }
        }
    }

    /** A halt rejection freezes the desk mirror for that symbol. */
    public void noteHalt(String roundId, String symbol) {
        if (symbol != null) {
            haltedByRound.computeIfAbsent(roundId, id -> ConcurrentHashMap.newKeySet()).add(symbol);
        }
    }

    /** A release whose validity window closed before its turn is pure waste. */
    static boolean isStale(long releaseTick, long durationTicks, Long nowTick) {
        return nowTick != null && nowTick - releaseTick > durationTicks;
    }

    /** Whether the desk sheds this release: never arc beats, only expired routine news. */
    static boolean shedFor(String newsId, long releaseTick, long durationTicks, Long nowTick) {
        return laneOf(newsId) != 0 && isStale(releaseTick, durationTicks, nowTick);
    }

    /**
     * A price move in words, never digits: memory feeds the narrator, whose
     * denylist declines any figure. Rounds to whole percent.
     */
    static String wordsFor(double movePercent) {
        double abs = Math.abs(movePercent);
        if (abs < 0.5) {
            return "little changed";
        }
        String direction = movePercent > 0.0 ? "up" : "down";
        String approx = abs - Math.floor(abs) >= 0.5 ? "just over" : "about";
        return direction + " " + approx + " " + numberWord(Math.round(abs)) + " percent";
    }

    private static String numberWord(long n) {
        String[] ones = {"zero", "one", "two", "three", "four", "five", "six", "seven",
                "eight", "nine", "ten", "eleven", "twelve", "thirteen", "fourteen", "fifteen",
                "sixteen", "seventeen", "eighteen", "nineteen"};
        String[] tens = {"", "", "twenty", "thirty", "forty", "fifty",
                "sixty", "seventy", "eighty", "ninety"};
        if (n < 0L) {
            return "about zero";
        }
        if (n < 20L) {
            return ones[(int) n];
        }
        if (n < 100L) {
            long remainder = n % 10L;
            return tens[(int) (n / 10L)] + (remainder == 0L ? "" : "-" + ones[(int) remainder]);
        }
        return "more than one hundred";
    }

    /** Forget per-round counts with the round (room expiry, settle). */
    public void forget(String roundId) {
        counts.remove(roundId);
        roundTicks.remove(roundId);
        marksByRound.remove(roundId);
        haltedByRound.remove(roundId);
        storyMarks.keySet().removeIf(key -> key.startsWith(roundId + "/"));
    }

    /** Story-so-far for a beat: prior HEADLINES plus market consequences.
      * Headlines only — full bodies parrot back and bury the lead (pilot:
      * beat two restated beat one and ignored the bidding war). */
    String assembleMemory(String roundId, GameEvent.NewsReleased news) {
        String story = StoryIds.storyOf(news.newsId());
        StringBuilder memory = new StringBuilder();
        try {
            List<String> heads = articles.listByRound(roundId).stream()
                    .filter(a -> !a.newsId().equals(news.newsId())
                            && StoryIds.storyOf(a.newsId()).equals(story)
                            && a.tick() < news.tick())
                    .sorted(java.util.Comparator.comparingLong(ArticleStore.Article::tick))
                    .map(ArticleStore.Article::headline)
                    .toList();
            int words = 0;
            for (String headline : heads) {
                for (String word : headline.split("\\s+")) {
                    if (word.isBlank()) {
                        continue;
                    }
                    if (words >= 40) {
                        break;
                    }
                    memory.append(words == 0 ? "" : " ").append(word);
                    words++;
                }
                if (words >= 40) {
                    break;
                }
                memory.append(" |");
                words++;
            }
        } catch (Exception e) {
            log.warn("newsroom memory read failed for {}: {}", news.newsId(), e.toString());
        }
        if (news.symbol() != null) {
            Double mark = marksByRound.getOrDefault(roundId, Map.of()).get(news.symbol());
            Double last = storyMarks.get(roundId + "/" + story);
            if (mark != null && last != null && last > 0.0) {
                double move = 100.0 * (mark - last) / last;
                memory.append(memory.length() == 0 ? "" : " ")
                        .append(news.symbol()).append(" ").append(wordsFor(move))
                        .append(" since last beat");
            }
            if (mark != null) {
                storyMarks.put(roundId + "/" + story, mark);
            }
            java.util.Set<String> halted = haltedByRound.get(roundId);
            if (halted != null && halted.contains(news.symbol())) {
                memory.append(memory.length() == 0 ? "" : " ").append("Trading halted.");
            }
        }
        return memory.toString().trim();
    }

    private void write(String roundId, GameEvent.NewsReleased news, ArticleStore.Kind kind) {
        try {
            Long now = roundTicks.get(roundId);
            // Arc beats are never shed: a developing story is worth telling
            // late, and killing one strands its arc (narrator-arcs 2.3).
            if (shedFor(news.newsId(), news.tick(), news.durationTicks(), now)) {
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
                    news.scope().name(), tickers, news.sector(), names, news.magnitude(), news.tick(),
                    assembleMemory(roundId, news), ScenarioDeck.bibleForStory(StoryIds.storyOf(news.newsId())));
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
        // Banter is wire flavor, never article material: narrating it would
        // burn sidecar budget on zero-magnitude noise.
        if (news.newsId() != null && news.newsId().startsWith("taunt-")) {
            return null;
        }
        if (news.newsId() != null && news.newsId().startsWith("scen-")) {
            return ArticleStore.Kind.SCENARIO;
        }
        return switch (news.scope()) {
            case SECTOR -> ArticleStore.Kind.SECTOR;
            case MACRO, STRUCTURE -> ArticleStore.Kind.MACRO;
            case COMPANY -> news.newsId() != null && news.newsId().startsWith("sched-")
                    ? ArticleStore.Kind.EARNINGS
                    : null;
        };
    }
}
