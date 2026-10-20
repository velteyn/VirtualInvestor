package com.virtualinvestor.core.news;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.virtualinvestor.core.domain.NewsScope;
import com.virtualinvestor.core.random.DeterministicRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class StoryTemplatesTest {

    private static final String NAME = "Nvido Semiconductors";
    private static final String SECTOR = "Semiconductors";

    private static List<StoryTemplates.Triple> all() {
        List<StoryTemplates.Triple> all = new ArrayList<>();
        all.addAll(StoryTemplates.TAKEOVER);
        all.addAll(StoryTemplates.REGULATORY);
        all.addAll(StoryTemplates.GURU);
        all.addAll(StoryTemplates.ANALYST);
        return all;
    }

    @Test
    void entriesFitBodiesNameFictionAndSlots() {
        assertFalse(all().isEmpty());
        for (StoryTemplates.Triple triple : all()) {
            for (String lede : List.of(triple.rumor(), triple.confirm(), triple.deny(), triple.body())) {
                assertTrue(lede.length() <= 200, "too long: " + lede);
                String filled = StoryTemplates.render(lede, NAME, SECTOR);
                assertFalse(filled.contains("{") || filled.contains("}"), "unfilled slot: " + filled);
                assertFalse(FictionalNames.containsRealName(filled), "real name slipped in: " + filled);
                assertTrue(filled.contains(NAME) || filled.contains(SECTOR) || !lede.contains("{"),
                        "slot vanished: " + filled);
            }
        }
    }

    @Test
    void tauntsAreShortCleanAndDayGated() {
        assertFalse(StoryTemplates.TAUNTS.isEmpty());
        for (StoryTemplates.Taunt taunt : StoryTemplates.TAUNTS) {
            assertTrue(taunt.text().length() <= 200);
            assertFalse(FictionalNames.containsRealName(taunt.text()));
        }
        DeterministicRandom random = new DeterministicRandom(7L);
        assertTrue(StoryTemplates.tauntAt(0.0, random).isEmpty(), "nothing unlocked on day zero");
        assertTrue(StoryTemplates.tauntAt(0.2, random).isPresent(), "early taunt by fraction 0.2");
        assertTrue(StoryTemplates.tauntAt(1.0, random).isPresent(), "late session always has banter");
        // Same seed, same banter.
        DeterministicRandom first = new DeterministicRandom(7L);
        DeterministicRandom second = new DeterministicRandom(7L);
        assertEquals(StoryTemplates.tauntAt(0.9, first), StoryTemplates.tauntAt(0.9, second));
    }

    @Test
    void everyScopeAndDirectionDraws() {
        DeterministicRandom random = new DeterministicRandom(11L);
        for (NewsScope scope : new NewsScope[]{NewsScope.COMPANY, NewsScope.SECTOR, NewsScope.MACRO}) {
            for (double direction : new double[]{1.0, -1.0}) {
                StoryTemplates.Triple triple = StoryTemplates.drawFor(scope, direction, random);
                assertEquals(direction, triple.direction(), 1e-9);
                String rumor = StoryTemplates.render(triple.rumor(), NAME, SECTOR);
                if (scope == NewsScope.COMPANY) {
                    assertTrue(rumor.contains(NAME), "company lede names the company: " + rumor);
                }
            }
        }
    }

    @Test
    void scenarioKeysMapToPacks() {
        DeterministicRandom random = new DeterministicRandom(13L);
        assertEquals(1.0, StoryTemplates.drawScenario("takeover", 1.0, random).direction(), 1e-9);
        assertEquals(-1.0, StoryTemplates.drawScenario("scandal", -1.0, random).direction(), 1e-9);
        assertEquals(-1.0, StoryTemplates.drawScenario("probe", -1.0, random).direction(), 1e-9);
        assertEquals(1.0, StoryTemplates.drawScenario("squeeze", 1.0, random).direction(), 1e-9);
        assertEquals(1.0, StoryTemplates.drawScenario("meme", 1.0, random).direction(), 1e-9);
    }

    @Test
    void renderIsNullSafe() {
        assertEquals("beats", StoryTemplates.render("{name} beats", null, null).replaceAll("^\\s+", ""));
        Optional<String> none = StoryTemplates.tauntAt(-0.1, new DeterministicRandom(1L));
        assertTrue(none.isEmpty());
    }
}
