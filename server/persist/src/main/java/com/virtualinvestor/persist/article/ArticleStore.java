package com.virtualinvestor.persist.article;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import javax.sql.DataSource;

/**
 * Newsroom articles (newsroom-narrator 1.2): generated story bodies keyed by
 * news id, so the live wire (same-{@code newsId} re-broadcast) and the history
 * endpoint serve the same rows. Articles are content, never simulation input.
 */
public final class ArticleStore {

    public enum Kind {
        EARNINGS,
        SECTOR,
        MACRO,
        PREVIEW,
        RECAP,
        TEMPLATE
    }

    public record Article(String newsId, String roundId, long tick, Kind kind, List<String> tickers,
            String headline, String body, String model) {
    }

    private final DataSource dataSource;

    public ArticleStore(DataSource dataSource) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
    }

    /** Store an article; re-storing the same round and news id replaces the body (regeneration). */
    public void save(Article article) {
        Objects.requireNonNull(article, "article");
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "INSERT INTO articles (news_id, round_id, tick, kind, tickers, headline, body, model)"
                                + " VALUES (?, ?, ?, ?, ?, ?, ?, ?)"
                                + " ON CONFLICT (round_id, news_id) DO UPDATE SET body = EXCLUDED.body,"
                                + " model = EXCLUDED.model")) {
            statement.setString(1, article.newsId());
            statement.setString(2, article.roundId());
            statement.setLong(3, article.tick());
            statement.setString(4, article.kind().name());
            statement.setString(5, String.join(",", article.tickers()));
            statement.setString(6, article.headline());
            statement.setString(7, article.body());
            statement.setString(8, article.model());
            statement.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("article save failed", e);
        }
    }

    /** The round's archive oldest-first, for late joiners and the newsroom tab. */
    public List<Article> listByRound(String roundId) {
        try (Connection connection = dataSource.getConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT news_id, round_id, tick, kind, tickers, headline, body, model"
                                + " FROM articles WHERE round_id = ? ORDER BY tick, news_id")) {
            statement.setString(1, roundId);
            try (ResultSet rows = statement.executeQuery()) {
                List<Article> articles = new ArrayList<>();
                while (rows.next()) {
                    List<String> tickers = rows.getString(5).isEmpty() ? List.of()
                            : List.of(rows.getString(5).split(","));
                    articles.add(new Article(rows.getString(1), rows.getString(2), rows.getLong(3),
                            Kind.valueOf(rows.getString(4)), tickers, rows.getString(6), rows.getString(7),
                            rows.getString(8)));
                }
                return articles;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("article listing failed", e);
        }
    }
}
