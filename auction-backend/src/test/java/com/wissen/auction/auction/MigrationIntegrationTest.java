package com.wissen.auction.auction;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.sql.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "TEST_POSTGRES_URL", matches = ".+")
class MigrationIntegrationTest {
    @Test
    void freshAndExplicitlyBaselinedLegacySchemasMigrateWithoutLosingData() throws Exception {
        for (boolean legacy : new boolean[]{false, true}) {
            String schema = "auction_migration_" + UUID.randomUUID().toString().replace("-", "");
            String base = System.getenv("TEST_POSTGRES_URL");
            String url = base + (base.contains("?") ? "&" : "?") + "currentSchema=" + schema;
            try (Connection connection = DriverManager.getConnection(base, System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"));
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE SCHEMA " + schema);
            }
            try {
                Flyway flyway = Flyway.configure().dataSource(url, System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"))
                        .schemas(schema).defaultSchema(schema).baselineVersion("0").load();
                if (legacy) {
                    try (Connection connection = DriverManager.getConnection(url, System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"));
                         Statement statement = connection.createStatement()) {
                        statement.execute(Files.readString(Path.of("src/main/resources/db/migration/V1__initial_schema.sql")));
                        statement.execute("INSERT INTO teams(id, location, owner_name, purse_remaining, team_name) VALUES (44, 'Pune', 'Legacy Owner', 100000, 'Legacy Team')");
                    }
                    assertThatThrownBy(flyway::migrate).hasMessageContaining("non-empty schema");
                    flyway.baseline();
                }
                flyway.migrate();
                assertThat(flyway.info().current().getVersion().toString()).isEqualTo("2");
                try (Connection connection = DriverManager.getConnection(url, System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"));
                     Statement statement = connection.createStatement()) {
                    try (ResultSet rows = statement.executeQuery("SELECT count(*) FROM auth_sessions")) { rows.next(); assertThat(rows.getInt(1)).isZero(); }
                    if (legacy) {
                        try (ResultSet rows = statement.executeQuery("SELECT team_name FROM teams WHERE id = 44")) {
                            assertThat(rows.next()).isTrue(); assertThat(rows.getString(1)).isEqualTo("Legacy Team");
                        }
                    }
                }
            } finally {
                if (!schema.matches("auction_migration_[a-f0-9]{32}")) throw new IllegalStateException("Unsafe schema.");
                try (Connection connection = DriverManager.getConnection(base, System.getenv("TEST_POSTGRES_USER"), System.getenv("TEST_POSTGRES_PASSWORD"));
                     Statement statement = connection.createStatement()) { statement.execute("DROP SCHEMA " + schema + " CASCADE"); }
            }
        }
    }
}
