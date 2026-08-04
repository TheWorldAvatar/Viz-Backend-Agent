package com.cmclinnovations.agent.service.core;

import java.time.Instant;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.response.UserProfile;
import com.cmclinnovations.agent.utils.StringResource;

@Service
public class AuthenticationService {
  private final Environment environment;
  private static final String SYSTEM_ID = "system-cron-job";
  private static final String SYSTEM_NAME = "System";

  /**
   * Constructs a new service.
   */
  public AuthenticationService(Environment environment) {
    this.environment = environment;
  }

  /**
   * Checks if authentication has been enabled.
   */
  public boolean isAuthenticationEnabled() {
    String value = environment.getProperty("keycloak.issuer.uri");
    return value != null && !value.trim().isEmpty();
  }

  /**
   * Creates an internal authentication token to bypass keycloak for internal
   * tasks.
   */
  public void setInternalAuthentication() {
    if (this.isAuthenticationEnabled()) {
      Jwt systemJwt = new Jwt(
          "mock-system-token-value",
          Instant.now(),
          Instant.now().plusSeconds(3600),
          Map.of("alg", "none"),
          Map.of("sub", SYSTEM_ID, "name", SYSTEM_NAME));

      UsernamePasswordAuthenticationToken systemAuth = new UsernamePasswordAuthenticationToken(systemJwt, null,
          Collections.emptyList());

      SecurityContextHolder.getContext().setAuthentication(systemAuth);
    }
  }

  /**
   * Retrieves the user roles associated with the credentials.
   */
  public Set<String> getUserRoles() {
    Set<String> userRoles = new HashSet<>();
    SecurityContextHolder.getContext().getAuthentication().getAuthorities().forEach(authority -> {
      // Filter out SCOPE authorities
      if (authority != null && !authority.getAuthority().startsWith("SCOPE_")) {
        userRoles.add(authority.getAuthority());
      }
    });

    return userRoles;
  }

  /**
   * Retrieves the user profile associated with the credentials.
   */
  public UserProfile getUserProfile() {
    Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

    if (authentication != null && authentication.getPrincipal() instanceof Jwt jwt) {
      return new UserProfile(jwt.getSubject(), jwt.getClaimAsString("name"));
    }
    return null;
  }

  /**
   * Verifies if the authenticated user does not possesses all of the specified
   * roles required for authorisation. This method is typically used to control
   * access to resources or functionalities based on user permissions.
   *
   * @param userRoles     A list of user roles
   * @param requiredRoles A semi-colon-separated string of role names e.g.
   *                      "admin;editor;viewer"
   */
  public boolean isUnauthorised(Set<String> userRoles, String requiredRoles) {
    Set<String> dataRoles = StringResource.mapRoles(requiredRoles);
    dataRoles.retainAll(userRoles);
    return dataRoles.isEmpty();
  }
}