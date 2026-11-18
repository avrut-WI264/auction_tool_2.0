package com.wissen.auction.common;

import com.wissen.auction.auth.AppUser;
import com.wissen.auction.auth.AppUserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ProductionReadinessGuardTest {
    private final AppUserRepository users = mock(AppUserRepository.class);
    private final BCryptPasswordEncoder passwords = new BCryptPasswordEncoder();
    private final String metrics = "independent-test-metrics-token-0123456789";

    @Test
    void productionRejectsLocalDatabaseCredentialsAndInsecureCookies() {
        var guard = new ProductionReadinessGuard(users, passwords, false, "https://auction.example", "postgres", "auction_app", metrics);
        assertThatThrownBy(guard::afterSingletonsInstantiated).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void demoPasswordsAreRejectedEvenWhenTheyWerePreviouslyHashed() {
        when(users.findAll()).thenReturn(List.of(AppUser.builder().username("admin").role("admin")
                .password(passwords.encode("admin123")).build()));
        var guard = new ProductionReadinessGuard(users, passwords, true, "https://auction.example", "auction_runtime", "auction_app", metrics);
        assertThatThrownBy(guard::afterSingletonsInstantiated).hasMessageContaining("demo administrator password");
    }

    @Test
    void secureExplicitConfigurationWithProvisionedAdministratorPasses() {
        when(users.findAll()).thenReturn(List.of(AppUser.builder().username("admin").role("admin")
                .password(passwords.encode("long-unique-admin-password")).build()));
        var guard = new ProductionReadinessGuard(users, passwords, true, "https://auction.example", "auction_runtime", "auction_app", metrics);
        assertThatCode(guard::afterSingletonsInstantiated).doesNotThrowAnyException();
    }
}
