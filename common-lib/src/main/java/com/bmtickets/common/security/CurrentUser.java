package com.bmtickets.common.security;

import org.springframework.security.oauth2.jwt.Jwt;

/** Convenience view of the authenticated end user extracted from the access token. */
public record CurrentUser(String id, String email, boolean admin) {

    public static CurrentUser from(Jwt jwt, boolean admin) {
        String email = jwt.getClaimAsString("email");
        return new CurrentUser(jwt.getSubject(), email, admin);
    }
}
