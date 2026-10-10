package com.virtualinvestor.narrator;

import java.util.Optional;

/**
 * Template narrator (newsroom-narrator 2.1): deterministic story bodies built
 * from the headline and its subjects, no model. The default when the sidecar
 * is off — the degradation ladder's middle rung.
 */
public final class TemplateNarrator implements Narrator {

    @Override
    public Optional<Draft> narrate(Request request) {
        if (request == null || request.headline() == null || request.headline().isBlank()) {
            return Optional.empty();
        }
        if ("PREVIEW".equalsIgnoreCase(request.kind())) {
            return Optional.of(new Draft(request.headline().trim() + ". "
                    + "The opening bell approaches: traders take their seats and the book starts empty. "
                    + "This is a template account; the full story follows when the newsroom desk is staffed.",
                    "template"));
        }
        if ("RECAP".equalsIgnoreCase(request.kind())) {
            return Optional.of(new Draft(request.headline().trim() + ". "
                    + "The closing auction is done and the standings are final. "
                    + "This is a template account; the full story follows when the newsroom desk is staffed.",
                    "template"));
        }
        String subjects = request.subjectNames().isEmpty() ? "the market"
                : String.join(", ", request.subjectNames());
        String direction = request.magnitude() >= 0 ? "higher" : "lower";
        String body = request.headline().trim() + ". " + subjects + " moved " + direction
                + " on the session's tape, with a signed shock of "
                + String.format("%.3f", Math.abs(request.magnitude())) + ".";
        if (request.sector() != null && !request.sector().isBlank()
                && !"MACRO".equalsIgnoreCase(request.scope())) {
            body += " The move centered on " + request.sector() + " names.";
        }
        body += " This is a template account; the full story follows when the newsroom desk is staffed.";
        return Optional.of(new Draft(body, "template"));
    }
}
