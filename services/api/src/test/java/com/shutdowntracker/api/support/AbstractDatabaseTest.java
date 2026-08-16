package com.shutdowntracker.api.support;

import javax.sql.DataSource;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Base class for tests that exercise SQL against the real database.
 *
 * <p>Extending this gives each test an empty schema with the production migrations
 * already applied.
 */
public abstract class AbstractDatabaseTest {

    protected DataSource dataSource() {
        return EmbeddedDatabase.dataSource();
    }

    protected JdbcTemplate jdbcTemplate() {
        return EmbeddedDatabase.jdbcTemplate();
    }

    @BeforeEach
    void resetDatabase() {
        EmbeddedDatabase.reset();
    }
}
