package com.mf.migrations;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import liquibase.Liquibase;
import liquibase.database.Database;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * One real PostgreSQL (Testcontainers) shared by every test class in the JVM. Each test calls {@link #reset()}
 * first, so tests never see each other's data. The container is stopped by Testcontainers' cleanup container
 * when the JVM exits. If Docker is unavailable, class initialisation fails and every test fails loudly.
 */
final class TestDatabase {

    static final String MASTER = "db/changelog/db.changelog-master.yaml";

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18-alpine");

    static {
        POSTGRES.start();
    }

    private TestDatabase() {}

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    /** Empties the database: drops every object, including Liquibase's own bookkeeping tables. */
    static void reset() throws SQLException {
        try (var connection = connect(); var statement = connection.createStatement()) {
            statement.execute("DROP SCHEMA public CASCADE");
            statement.execute("CREATE SCHEMA public");
        }
    }

    /** Applies the whole changelog, as `mvn liquibase:update` would. */
    static void migrate() throws Exception {
        try (var connection = connect(); var liquibase = liquibase(connection)) {
            liquibase.update();
        }
    }

    /** Rolls back the most recently applied {@code count} change sets. */
    static void rollback(int count) throws Exception {
        try (var connection = connect(); var liquibase = liquibase(connection)) {
            liquibase.rollback(count, null);
        }
    }

    /** The first column of the first row, or null when there are no rows. */
    @SuppressWarnings("unchecked")
    static <T> T scalar(String sql, Object... args) throws SQLException {
        try (var connection = connect(); var statement = prepare(connection, sql, args); var rows = statement.executeQuery()) {
            return rows.next() ? (T) rows.getObject(1) : null;
        }
    }

    /** The first column of every row, as strings. */
    static List<String> column(String sql, Object... args) throws SQLException {
        try (var connection = connect(); var statement = prepare(connection, sql, args); var rows = statement.executeQuery()) {
            var values = new ArrayList<String>();
            while (rows.next()) {
                values.add(rows.getString(1));
            }
            return values;
        }
    }

    /** Runs an INSERT/UPDATE/DELETE and returns the affected row count. */
    static int update(String sql, Object... args) throws SQLException {
        try (var connection = connect(); var statement = prepare(connection, sql, args)) {
            return statement.executeUpdate();
        }
    }

    private static PreparedStatement prepare(Connection connection, String sql, Object[] args) throws SQLException {
        var statement = connection.prepareStatement(sql);
        for (int i = 0; i < args.length; i++) {
            statement.setObject(i + 1, args[i]);
        }
        return statement;
    }

    private static Liquibase liquibase(Connection connection) throws Exception {
        Database database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(new JdbcConnection(connection));
        return new Liquibase(MASTER, new ClassLoaderResourceAccessor(), database);
    }
}
