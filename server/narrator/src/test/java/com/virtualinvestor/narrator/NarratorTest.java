package com.virtualinvestor.narrator;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class NarratorTest {

    private static Narrator.Request earnings() {
        return new Narrator.Request("sched-PEAR", "EARNINGS", "Pear Computers Inc beats quarterly expectations",
                "COMPANY", List.of("PEAR"), "Consumer Tech", List.of("Pear Computers Inc"), 0.025, 5400L, "", "");
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
                List.of(), 0.01, 1L, "", "");

        assertTrue(new TemplateNarrator().narrate(blank).isEmpty());
    }

    @Test
    void templateBodyAgreesWithWireSubjects() {
        Narrator.Request sector = new Narrator.Request("rand-7", "SECTOR", "Chip names weaken on flows",
                "SECTOR", List.of(), "Semiconductors", List.of(), -0.02, 100L, "", "");
        Narrator.Draft draft = new TemplateNarrator().narrate(sector).orElseThrow();

        assertEquals("template", draft.model());
        assertTrue(draft.body().contains("Chip names weaken on flows"));
        assertTrue(draft.body().contains("Semiconductors"), draft.body());
    }

    @Test
    void templateIsDeterministicPerNewsId() {
        Narrator.Request first = new Narrator.Request("rand-9", "MACRO", "Data misses, stocks slide",
                "MACRO", List.of(), null, List.of(), -0.02, 100L, "", "");
        String one = new TemplateNarrator().narrate(first).orElseThrow().body();
        String two = new TemplateNarrator().narrate(first).orElseThrow().body();
        assertEquals(one, two);
    }

    @Test
    void disabledDeclinesEverything() {
        assertTrue(new DisabledNarrator().narrate(earnings()).isEmpty());
    }
}
