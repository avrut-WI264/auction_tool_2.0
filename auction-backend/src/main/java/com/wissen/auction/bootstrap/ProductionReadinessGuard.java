package com.wissen.auction.bootstrap;

import java.net.URI;

import com.wissen.auction.user.AppUserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Reject known development settings before accepting production traffic. */
@Component @Profile("prod")
public class ProductionReadinessGuard implements org.springframework.beans.factory.SmartInitializingSingleton {
    private final AppUserRepository users;
    private final PasswordEncoder passwords;
    private final boolean secure;
    private final String origins;
    private final String databaseUser;
    private final String schema;
    private final String metricsToken;

    public ProductionReadinessGuard(AppUserRepository users, PasswordEncoder passwords,
            @Value("${app.cookie.secure}") boolean secure, @Value("${app.allowed.origins}") String origins,
            @Value("${spring.datasource.username}") String databaseUser,
            @Value("${app.database.schema}") String schema,
            @Value("${app.management.token:}") String metricsToken) {
        this.users = users;
        this.passwords = passwords;
        this.secure = secure;
        this.origins = origins;
        this.databaseUser = databaseUser;
        this.schema = schema;
        this.metricsToken = metricsToken;
    }

    @Override @Transactional(readOnly = true)
    public void afterSingletonsInstantiated() {
        if (metricsToken.length() < 32 || metricsToken.startsWith("REPLACE")) {
            throw new IllegalStateException("Configure a unique metrics bearer token of at least 32 characters.");
        }
        if (!secure || "postgres".equalsIgnoreCase(databaseUser) || "public".equals(schema)) {
            throw new IllegalStateException("Production requires secure cookies, a restricted database user, and a dedicated schema.");
        }
        for (String origin : origins.split(",")) {
            URI uri = URI.create(origin.trim());
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || "localhost".equals(uri.getHost())) {
                throw new IllegalStateException("Production frontend origins must be explicit HTTPS origins.");
            }
        }
        var administrators = users.findAll().stream().filter(user -> user.isEnabled() && "admin".equals(user.getRole())).toList();
        if (administrators.isEmpty()) throw new IllegalStateException("Provision an administrator before starting production.");
        administrators.forEach(user -> {
            if (passwords.matches("admin123", user.getPassword())) {
                throw new IllegalStateException("A demo administrator password remains. Provision secure administrator credentials before production.");
            }
        });
    }
}
