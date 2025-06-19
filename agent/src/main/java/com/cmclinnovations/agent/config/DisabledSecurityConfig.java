package com.cmclinnovations.agent.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
// Enable this configuration IF 'keycloak.issuer.uri' property is MISSING or
// empty
@ConditionalOnExpression("'${keycloak.issuer.uri:}'.trim().isEmpty() || '${keycloak.issuer.uri:}'==null")
public class DisabledSecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity httpSecurity) throws Exception {
        return httpSecurity.authorizeHttpRequests(authorize -> authorize
                .anyRequest().permitAll()).build();
    }
}