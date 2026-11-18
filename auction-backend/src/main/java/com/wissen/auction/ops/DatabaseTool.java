package com.wissen.auction.ops;

import java.nio.charset.StandardCharsets;
import java.sql.*;

import com.wissen.auction.auction.domain.AuctionCity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/** Explicit operator tool. Secrets are read from process environment, never CLI arguments. */
public final class DatabaseTool {
    private DatabaseTool() {}

    public static void main(String[] args) throws Exception {
        String operation = args.length == 1 ? args[0] : "";
        if (!"provision-admin".equals(operation)) {
            throw new IllegalArgumentException("Expected provision-admin.");
        }
        String url = required("SPRING_DATASOURCE_URL");
        String user = required("SPRING_DATASOURCE_USERNAME");
        String password = required("SPRING_DATASOURCE_PASSWORD");
        String schema = required("APP_DATABASE_SCHEMA");
        if (!schema.matches("[a-z][a-z0-9_]{0,62}") || "public".equals(schema)) {
            throw new IllegalArgumentException("Use a dedicated application schema.");
        }
        String city = new AuctionCity(required("BOOTSTRAP_CITY")).value();
        String username = required("BOOTSTRAP_USERNAME");
        String adminPassword = required("BOOTSTRAP_PASSWORD");
        if (!username.matches("[A-Za-z0-9_.-]{3,60}") || adminPassword.length() < 12 || adminPassword.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Use a valid username and a password of at least 12 characters and at most 72 UTF-8 bytes.");
        }
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.setSchema(schema);
            connection.setAutoCommit(false);
            try (PreparedStatement lock = connection.prepareStatement("SELECT pg_advisory_xact_lock(hashtext(?))")) {
                lock.setString(1, "provision-admin:" + username); lock.execute();
            }
            try (PreparedStatement existing = connection.prepareStatement("SELECT id, role FROM app_users WHERE username = ? FOR UPDATE")) {
                existing.setString(1, username);
                try (ResultSet row = existing.executeQuery()) {
                    if (row.next()) {
                        if (!"admin".equals(row.getString("role")) || !"true".equals(System.getenv("BOOTSTRAP_ALLOW_RESET"))) {
                            throw new IllegalStateException("Account exists. Reset requires an existing admin account and explicit AllowReset.");
                        }
                        try (PreparedStatement revoke = connection.prepareStatement("UPDATE auth_sessions SET revoked = true WHERE user_id = ?")) {
                            revoke.setLong(1, row.getLong("id")); revoke.executeUpdate();
                        }
                    }
                }
            }
            try (PreparedStatement save = connection.prepareStatement("""
                    INSERT INTO app_users(username, password, role, city, enabled) VALUES (?, ?, 'admin', ?, true)
                    ON CONFLICT(username) DO UPDATE SET password = EXCLUDED.password, city = EXCLUDED.city, enabled = true
                    """)) {
                save.setString(1, username); save.setString(2, new BCryptPasswordEncoder().encode(adminPassword)); save.setString(3, city);
                save.executeUpdate();
            }
            connection.commit();
            System.out.println("Administrator provisioned; previous sessions were revoked.");
        }
    }

    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing environment variable: " + name);
        return value;
    }
}
