package com.bmtickets.booking.security;

import org.springframework.security.oauth2.client.OAuth2AuthorizeRequest;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.stereotype.Component;

/**
 * Service identity for east-west calls: OAuth2 client-credentials token (Cognito app client / Keycloak client).
 * Tokens are cached by the authorized-client service and renewed shortly before expiry.
 * Used for calls without an end user in context too (e.g. the expiry job cancelling payments).
 */
@Component
public class ServiceTokenProvider {

    static final String REGISTRATION_ID = "service-client";

    private final OAuth2AuthorizedClientManager manager;

    public ServiceTokenProvider(OAuth2AuthorizedClientManager manager) {
        this.manager = manager;
    }

    public String token() {
        OAuth2AuthorizeRequest request = OAuth2AuthorizeRequest.withClientRegistrationId(REGISTRATION_ID)
                .principal("booking-service")
                .build();
        OAuth2AuthorizedClient client = manager.authorize(request);
        if (client == null || client.getAccessToken() == null) {
            throw new IllegalStateException("Unable to obtain service access token");
        }
        return client.getAccessToken().getTokenValue();
    }
}
