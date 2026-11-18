package com.wissen.auction.user;

import java.security.Principal;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/{city}/owners")
@RequiredArgsConstructor
public class TeamOwnerController {
    private final TeamOwnerService owners;

    @PostMapping
    public OwnerAccountResponse create(@PathVariable String city, @Valid @RequestBody CreateOwnerRequest request,
                                       Principal principal) {
        return owners.create(city, principal.getName(), request);
    }
}
