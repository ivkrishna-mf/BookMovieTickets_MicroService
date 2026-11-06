package com.bmtickets.common.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Maps identity-provider specific claims to Spring authorities so the same code works with
 * Amazon Cognito (prod: {@code cognito:groups}) and Keycloak (local: {@code realm_access.roles}).
 * <ul>
 *   <li>{@code scope}/{@code scp} -> {@code SCOPE_xxx} (used for service-to-service authorization)</li>
 *   <li>groups / realm roles -> {@code ROLE_XXX} (used for user authorization, e.g. ROLE_ADMIN)</li>
 * </ul>
 */
public class JwtRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private final JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new HashSet<>(scopes.convert(jwt));
        roles(jwt).forEach(r -> authorities.add(new SimpleGrantedAuthority("ROLE_" + r.toUpperCase())));
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private static Collection<String> roles(Jwt jwt) {
        Set<String> roles = new HashSet<>();
        List<String> cognitoGroups = jwt.getClaimAsStringList("cognito:groups");
        if (cognitoGroups != null) {
            roles.addAll(cognitoGroups);
        }
        Map<String, Object> realmAccess = jwt.getClaimAsMap("realm_access");
        if (realmAccess != null && realmAccess.get("roles") instanceof Collection<?> rs) {
            rs.forEach(r -> roles.add(String.valueOf(r)));
        }
        return roles;
    }
}
