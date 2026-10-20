package com.virtualinvestor.narrator;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.news.StoryTemplates;
import com.virtualinvestor.core.random.DeterministicRandom;
import java.util.Optional;

/**
 * Template narrator (newsroom-narrator 2.1): deterministic story bodies built
 * from the headline and its subjects, no model. The default when the sidecar
 * is off — the degradation ladder's middle rung. Bodies come from the same
 * scope packs as the wire ledes (takeover, regulatory, guru, analyst-color),
 * so template prose agrees with the wire on subjects; the entry is picked
 * deterministically from the news id, keeping retries identical.
 */
public final class TemplateNarrator implements Narrator {

    private static final String TRAILER = "This is a template account; the full story follows when the newsroom desk is staffed.";

    @Override
    public Optional<Draft> narrate(Request request) {
        if (request == null || request.headline() == null || request.headline().isBlank()) {
            return Optional.empty();
        }
        if ("PREVIEW".equalsIgnoreCase(request.kind())) {
            return Optional.of(new Draft(request.headline().trim() + ". "
                    + "The opening bell approaches: traders take their seats and the book starts empty. "
                    + TRAILER,
                    "template"));
        }
        if ("RECAP".equalsIgnoreCase(request.kind())) {
            return Optional.of(new Draft(request.headline().trim() + ". "
                    + "The closing auction is done and the standings are final. "
                    + TRAILER,
                    "template"));
        }
        NewsScope scope;
        try {
            scope = NewsScope.valueOf(String.valueOf(request.scope()).toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            scope = NewsScope.MACRO;
        }
        double direction = request.magnitude() < 0.0 ? -1.0 : 1.0;
        String name = request.subjectNames().isEmpty() ? null : request.subjectNames().get(0);
        // Company ledes name the company; without a subject there is no
        // honest lede to fill, so decline instead of printing blanks.
        if (scope == NewsScope.COMPANY && name == null) {
            return Optional.empty();
        }
        long seed = request.newsId() == null ? 0L : request.newsId().hashCode();
        StoryTemplates.Triple entry = StoryTemplates.drawFor(scope, direction, new DeterministicRandom(seed));
        StringBuilder body = new StringBuilder(request.headline().trim());
        body.append(". ").append(StoryTemplates.render(entry.body(), name, request.sector()));
        if (request.sector() != null && !request.sector().isBlank() && scope != NewsScope.MACRO) {
            body.append(" The move centered on ").append(request.sector()).append(" names.");
        }
        body.append(' ').append(TRAILER);
        return Optional.of(new Draft(body.toString(), "template"));
    }
}
