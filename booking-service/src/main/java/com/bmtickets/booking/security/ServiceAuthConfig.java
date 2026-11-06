package com.bmtickets.booking.security;

import com.bmtickets.grpc.security.BearerTokenCallCredentials;
import feign.RequestInterceptor;
import net.devh.boot.grpc.client.inject.StubTransformer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.AuthorizedClientServiceOAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientManager;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientProviderBuilder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Configuration
public class ServiceAuthConfig {

    /** Non-request-bound manager: works from Kafka listeners and scheduled jobs, not only HTTP threads. */
    @Bean
    OAuth2AuthorizedClientManager authorizedClientManager(ClientRegistrationRepository registrations,
                                                          OAuth2AuthorizedClientService clientService) {
        var manager = new AuthorizedClientServiceOAuth2AuthorizedClientManager(registrations, clientService);
        manager.setAuthorizedClientProvider(OAuth2AuthorizedClientProviderBuilder.builder().clientCredentials().build());
        return manager;
    }

    /** Feign (REST) -> payment-service. */
    @Bean
    RequestInterceptor serviceTokenRequestInterceptor(ServiceTokenProvider tokens) {
        return template -> template.header("Authorization", "Bearer " + tokens.token());
    }

    /** gRPC -> catalog / seat-inventory. */
    @Bean
    StubTransformer serviceTokenStubTransformer(ServiceTokenProvider tokens) {
        return (name, stub) -> stub.withCallCredentials(new BearerTokenCallCredentials(tokens::token));
    }
}
