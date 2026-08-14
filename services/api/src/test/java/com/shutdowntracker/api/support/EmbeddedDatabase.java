package com.shutdowntracker.api.support;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.sql.DataSource;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * A real PostgreSQL instance for repository tests, started once per JVM and shared by
 * every test class.
 *
 * <p>The repositories in this service are hand-written JDBC against SQL that only the
 * real engine can validate: enum casts, {@code jsonb} columns, generated columns, and
 * partial indexes all behave differently on any substitute. Tests therefore run the
 * production Flyway migrations in {@code infra/migrations} against an actual server
 * rather than mocking the database away.
 *
 * <p>Docker is deliberately not required. The embedded distribution unpacks a genuine
 * PostgreSQL binary, so the same tests run on a developer machine and in CI without a
 * service container.
 */
public final class EmbeddedDatabase {

    private static final Object STARTUP_LOCK = new Object();

    private static EmbeddedPostgres postgres;
    private static DataSource dataSource;
    private static JdbcTemplate jdbcTemplate;
    private static List<String> tableNames;

    private EmbeddedDatabase() {
    }

    /**
     * Starts the server and applies every migration on first use.
     *
     * <p>Migration failures surface here rather than inside an unrelated assertion,
     * because a schema that does not apply invalidates every test that follows.
     */
    public static DataSource dataSource() {
        synchronized (STARTUP_LOCK) {
            if (dataSource == null) {
                start();
            }
            return dataSource;
        }
    }

    public static JdbcTemplate jdbcTemplate() {
        dataSource();
        return jdbcTemplate;
    }

    private static void start() {
        try {
            postgres = EmbeddedPostgres.builder().start();
        } catch (IOException exception) {
            throw new UncheckedIOException("Unable to start the embedded PostgreSQL server.", exception);
        }
        DataSource started = postgres.getPostgresDatabase();
        Flyway.configure()
                .dataSource(started)
                .locations("filesystem:" + migrationsDirectory())
                .baselineOnMigrate(false)
                .load()
                .migrate();
        dataSource = started;
        jdbcTemplate = new JdbcTemplate(started);
        tableNames = jdbcTemplate.queryForList(
                """
                SELECT tablename
                FROM pg_tables
                WHERE schemaname = 'public'
                  AND tablename <> 'flyway_schema_history'
                """,
                String.class);
    }

    /**
     * Locates {@code infra/migrations} by walking up from the working directory, so the
     * suite behaves the same whether Maven runs from the repository root or the module.
     */
    private static String migrationsDirectory() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null) {
            Path migrations = candidate.resolve("infra").resolve("migrations");
            if (Files.isDirectory(migrations)) {
                return migrations.toString();
            }
            candidate = candidate.getParent();
        }
        throw new IllegalStateException("Could not locate infra/migrations from " + Path.of("").toAbsolutePath());
    }

    /**
     * Empties every table so each test starts from a known state. {@code TRUNCATE ...
     * CASCADE} is used because the schema is heavily foreign-keyed and per-table
     * deletion would require maintaining a dependency order by hand.
     */
    public static void reset() {
        JdbcTemplate template = jdbcTemplate();
        if (tableNames.isEmpty()) {
            return;
        }
        template.execute("TRUNCATE TABLE " + String.join(", ", tableNames) + " RESTART IDENTITY CASCADE");
    }

    /** Table names created by the migrations, excluding Flyway's own bookkeeping table. */
    public static List<String> tableNames() {
        dataSource();
        return List.copyOf(tableNames);
    }
}
