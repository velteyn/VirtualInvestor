package com.virtualinvestor.edge.game;

import com.virtualinvestor.persist.player.PlayerStore;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Database wiring for the edge (player-accounts 2.1). The offline build has no
 * connection-pool starter in the local repository, so the DataSource is a plain
 * Postgres source and migrations run programmatically at startup — the same shape
 * the persist tests already use. Connection details come from the environment so
 * compose, dev machines and tests can each point elsewhere.
 */
@Configuration
public class PersistenceConfig {

    @Bean
    public DataSource dataSource() {
        PGSimpleDataSource dataSource = new PGSimpleDataSource();
        dataSource.setUrl(System.getenv().getOrDefault(
                "VI_DB_URL", "jdbc:postgresql://localhost:5432/virtualinvestor"));
        dataSource.setUser(System.getenv().getOrDefault("VI_DB_USER", "vi"));
        dataSource.setPassword(System.getenv().getOrDefault("VI_DB_PASSWORD", "vi"));
        Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        return dataSource;
    }

    @Bean
    public PlayerStore playerStore(DataSource dataSource) {
        return new PlayerStore(dataSource);
    }

    @Bean
    public AdminService adminService(
            PlayerStore store,
            @org.springframework.beans.factory.annotation.Value("${VI_ADMIN_PASSWORD:}") String masterPassword) {
        return new AdminService(store, masterPassword);
    }
}
