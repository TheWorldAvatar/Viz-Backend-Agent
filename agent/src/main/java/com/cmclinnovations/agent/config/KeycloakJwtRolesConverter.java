package com.cmclinnovations.agent.config;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

public class KeycloakJwtRolesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {
    private static final String CLAIM_RESOURCE_ACCESS = "resource_access";
    private static final String CLAIM_CLIENT = "viz";
    private static final String CLAIM_ROLES = "roles";

    /**
     * Extracts the relevant client roles from a JWT token.
     */
    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Collection<GrantedAuthority> grantedAuthorities = new ArrayList<>();
        // This agent requires only the client roles for the viz client to manage
        // resources; All other realm or client roles are ignored
        Map<String, Map<String, Collection<String>>> resourceAccess = jwt.getClaim(CLAIM_RESOURCE_ACCESS);
        if (resourceAccess != null && !resourceAccess.isEmpty()) {
            resourceAccess.get(CLAIM_CLIENT).get(CLAIM_ROLES).forEach(
                    role -> grantedAuthorities
                            .add(new SimpleGrantedAuthority(role)));
        }

        return grantedAuthorities;
    }
}