package com.virtualinvestor.narrator;

import java.util.List;
import java.util.Optional;

/**
 * Newsroom narrator (newsroom-narrator 2.1): a pure function from release to
 * story body. Implementations never touch the engine — the edge calls this
 * out-of-band and attaches the body to the already-published headline.
 * Returning empty declines (dropped by filter, sidecar down, over cap).
 */
public interface Narrator {

    /** Everything the narrator may know: simulation facts only, never player input. */
    record Request(String newsId, String kind, String headline, String scope, List<String> tickers,
            String sector, List<String> subjectNames, double magnitude, long tick) {
    }

    record Draft(String body, String model) {
    }

    Optional<Draft> narrate(Request request);
}
