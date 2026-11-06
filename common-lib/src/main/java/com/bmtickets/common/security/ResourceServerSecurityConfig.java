package com.bmtickets.common.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Zero-trust: every service validates the JWT itself (signature via JWKS, issuer, expiry) even though the
 * API gateway already did. A compromised/misrouted internal call therefore cannot bypass authorization.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(AppSecurityProperties.class)
public class ResourceServerSecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AppSecurityProperties props) throws Exception {
        http
                .csrf(csrf -> csrf.disable())          // stateless bearer-token API, no cookies
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> {
                    auth.requestMatchers("/actuator/health/**", "/actuator/info", "/actuator/prometheus",
                            "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html", "/error").permitAll();
                    props.publicPaths().forEach(p -> auth.requestMatchers(p).permitAll());
                    props.publicGetPaths().forEach(p -> auth.requestMatchers(HttpMethod.GET, p).permitAll());
                    auth.requestMatchers("/internal/**").hasAuthority(props.internalAuthority());
                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(o -> o.jwt(jwt -> jwt.jwtAuthenticationConverter(new JwtRoleConverter())));
        return http.build();
    }
}
