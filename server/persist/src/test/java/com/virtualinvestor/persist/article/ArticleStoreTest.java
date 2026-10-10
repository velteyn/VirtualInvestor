package com.virtualinvestor.persist.article;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;

class ArticleStoreTest {

    private static PGSimpleDataSource dataSource;
    private static ArticleStore store;

    @BeforeAll
    static void migrate() {
        String url = System.getProperty("vi.db.url", "jdbc:postgresql://localhost:5432/virtualinvestor");
        String user = System.getProperty("vi.db.user", "vi");
        String password = System.getProperty("vi.db.password", "vi");
        Flyway.configure().dataSource(url, user, password).locations("classpath:db/migration").load().migrate();
        dataSource = new PGSimpleDataSource();
        dataSource.setUrl(url);
        dataSource.setUser(user);
        dataSource.setPassword(password);
        store = new ArticleStore(dataSource);
    }

    @BeforeEach
    void clean() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement round = connection.prepareStatement(
                        "INSERT INTO rounds (round_id, seed, starting_capital, duration_minutes, preset, state)"
                                + " VALUES ('round-test-newsroom', 7, 100000, 30, 'REALISTIC', 'RUNNING')"
                                + " ON CONFLICT (round_id) DO NOTHING");
                PreparedStatement articles = connection.prepareStatement(
                        "DELETE FROM articles WHERE round_id = 'round-test-newsroom'")) {
            round.executeUpdate();
            articles.executeUpdate();
        }
    }

    @Test
    void roundTripTemplateArticleOldestFirst() {
        store.save(new ArticleStore.Article("sched-PEAR", "round-test-newsroom", 5400L,
                ArticleStore.Kind.EARNINGS, List.of("PEAR"), "Pear Computers Inc beats quarterly expectations",
                "Template body.", "template"));
        store.save(new ArticleStore.Article("rand-3", "round-test-newsroom", 1200L,
                ArticleStore.Kind.MACRO, List.of(), "Data beats expectations, stocks climb",
                "Earlier body.", "template"));

        List<ArticleStore.Article> articles = store.listByRound("round-test-newsroom");
        assertEquals(2, articles.size());
        assertEquals("rand-3", articles.get(0).newsId());
        assertEquals("sched-PEAR", articles.get(1).newsId());
        assertEquals(List.of("PEAR"), articles.get(1).tickers());
        assertTrue(articles.get(1).body().contains("Template body."));
    }

    @Test
    void resaveReplacesBody() {
        store.save(new ArticleStore.Article("sched-NVDO", "round-test-newsroom", 5500L,
                ArticleStore.Kind.EARNINGS, List.of("NVDO"), "headline", "first", "template"));
        store.save(new ArticleStore.Article("sched-NVDO", "round-test-newsroom", 5500L,
                ArticleStore.Kind.EARNINGS, List.of("NVDO"), "headline", "second", "llama-1b-q4"));

        List<ArticleStore.Article> articles = store.listByRound("round-test-newsroom");
        assertEquals(1, articles.size());
        assertEquals("second", articles.get(0).body());
        assertEquals("llama-1b-q4", articles.get(0).model());
    }

    @Test
    void sameNewsIdCoexistsAcrossRounds() throws Exception {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement round = connection.prepareStatement(
                        "INSERT INTO rounds (round_id, seed, starting_capital, duration_minutes, preset, state)"
                                + " VALUES ('round-test-newsroom-2', 8, 100000, 30, 'REALISTIC', 'RUNNING')"
                                + " ON CONFLICT (round_id) DO NOTHING")) {
            round.executeUpdate();
        }
        store.save(new ArticleStore.Article("sched-PEAR", "round-test-newsroom", 5400L,
                ArticleStore.Kind.EARNINGS, List.of("PEAR"), "headline", "round one", "template"));
        store.save(new ArticleStore.Article("sched-PEAR", "round-test-newsroom-2", 5400L,
                ArticleStore.Kind.EARNINGS, List.of("PEAR"), "headline", "round two", "template"));

        assertEquals("round one", store.listByRound("round-test-newsroom").stream()
                .filter(a -> a.newsId().equals("sched-PEAR")).findFirst().orElseThrow().body());
        assertEquals("round two", store.listByRound("round-test-newsroom-2").stream()
                .filter(a -> a.newsId().equals("sched-PEAR")).findFirst().orElseThrow().body());
    }
}
