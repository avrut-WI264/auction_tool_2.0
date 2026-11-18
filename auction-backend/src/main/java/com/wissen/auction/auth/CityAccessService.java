package com.wissen.auction.auth;

import com.wissen.auction.user.AccountRole;
import com.wissen.auction.user.AppUser;
import com.wissen.auction.user.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Central account/city authorization shared by auction and owner provisioning. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CityAccessService {
    private final AppUserRepository users;

    public AuthenticatedAccount requireAdministrator(String username, String city) {
        AuthenticatedAccount account = account(username, city);
        if (account.role() != AccountRole.ADMIN) {
            throw new SecurityException("Only the city's admin can manage this operation.");
        }
        return account;
    }

    public AuthenticatedAccount requireBidder(String username, String city) {
        AuthenticatedAccount account = account(username, city);
        if (account.role() != AccountRole.ADMIN && account.role() != AccountRole.OWNER) {
            throw new SecurityException("You cannot manage this auction.");
        }
        return account;
    }

    private AuthenticatedAccount account(String username, String city) {
        AppUser user = users.findByUsername(username)
                .orElseThrow(() -> new SecurityException("Account not found."));
        if (!user.isEnabled() || !user.getCity().equalsIgnoreCase(city)) {
            throw new SecurityException("You cannot manage this auction.");
        }
        return new AuthenticatedAccount(user.getUsername(), user.getCity(), AccountRole.from(user.getRole()),
                user.getTeam() == null ? null : user.getTeam().getId());
    }
}
