package com.wissen.auction.auth;

import com.wissen.auction.team.Team;
import com.wissen.auction.team.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TeamOwnerService {
    private final CityAccessService access;
    private final AppUserRepository users;
    private final TeamRepository teams;
    private final PasswordEncoder encoder;

    @Transactional
    public OwnerAccountResponse create(String city, String administrator, CreateOwnerRequest request) {
        if (request.password().getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must not exceed 72 UTF-8 bytes.");
        }
        AuthenticatedAccount admin = access.requireAdministrator(administrator, city);
        Team team = teams.findById(request.teamId())
                .orElseThrow(() -> new IllegalArgumentException("Team not found."));
        if (!team.getLocation().equalsIgnoreCase(city)) {
            throw new SecurityException("Team belongs to another city.");
        }
        if (users.existsByUsername(request.username())) {
            throw new IllegalStateException("Username already exists.");
        }
        AppUser owner = AppUser.builder().username(request.username())
                .password(encoder.encode(request.password())).role(AccountRole.OWNER.value())
                .city(admin.city()).team(team).build();
        users.save(owner);
        return new OwnerAccountResponse(owner.getUsername(), team.getId(), team.getTeamName());
    }
}
