package com.virtualinvestor.narrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarratorTest {

    private static Narrator.Request earnings() {
        return new Narrator.Request("sched-PEAR", "EARNINGS", "Pear Computers Inc beats quarterly expectations",
                "COMPANY", List.of("PEAR"), "Consumer Tech", List.of("Pear Computers Inc"), 0.025, 5400L);
    }

    @Test
    void templateBuildsGroundedBody() {
        Narrator.Draft draft = new TemplateNarrator().narrate(earnings()).orElseThrow();

        assertEquals("template", draft.model());
        assertTrue(draft.body().contains("Pear Computers Inc beats quarterly expectations"));
        assertTrue(draft.body().contains("Consumer Tech"));
    }

    @Test
    void templateDeclinesBlankHeadline() {
        Narrator.Request blank = new Narrator.Request("x", "MACRO", "  ", "MACRO", List.of(), null,
                List.of(), 0.01, 1L);

        assertTrue(new TemplateNarrator().narrate(blank).isEmpty());
    }

    @Test
    void disabledDeclinesEverything() {
        assertTrue(new DisabledNarrator().narrate(earnings()).isEmpty());
    }
}
