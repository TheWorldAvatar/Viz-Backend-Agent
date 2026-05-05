package com.cmclinnovations.agent.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.core.GrantedAuthorityDefaults;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.DelegatingJwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
// Enable this configuration IF 'keycloak.issuer.uri' property is present and
// has a value
@ConditionalOnExpression("!'${keycloak.issuer.uri:}'.trim().isEmpty() && '${keycloak.issuer.uri:}'!=null")
public class SecurityConfig {
    @Value("${KEYCLOAK_ISSUER_URI}")
    private String tokenIssuerUrl;

    @Bean
    SecurityFilterChain filterChain(HttpSecurity httpSecurity) throws Exception {
        // This converter combines multiple converters; extracts the roles from the JWT
        // token and have to be plugged into a JwtAuthenticationConverter
        DelegatingJwtGrantedAuthoritiesConverter authoritiesConverter = new DelegatingJwtGrantedAuthoritiesConverter(
                new JwtGrantedAuthoritiesConverter(), new KeycloakJwtRolesConverter());

        // This converter converts a Jwt object into an Authentication Token
        JwtAuthenticationConverter jwtAuthenticationConverter = new JwtAuthenticationConverter();
        jwtAuthenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        return httpSecurity.authorizeHttpRequests(authorize -> authorize.requestMatchers("/status").permitAll()
                .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter)))
                .csrf((csrf) -> csrf.ignoringRequestMatchers("/**"))
                .build();
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        // Lazily load the agent even if Keycloak is offline
        // Will retry on every request
        String jwkSetUri = tokenIssuerUrl + "/protocol/openid-connect/certs";
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder.withJwkSetUri(jwkSetUri).build();

        // Manually set the Issuer Validator
        OAuth2TokenValidator<Jwt> issuerValidator = JwtValidators.createDefaultWithIssuer(tokenIssuerUrl);
        jwtDecoder.setJwtValidator(issuerValidator);

        return jwtDecoder;
    }

    @Bean
    GrantedAuthorityDefaults grantedAuthorityDefaults() {
        return new GrantedAuthorityDefaults("");
    }
}