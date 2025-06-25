package com.cmclinnovations.agent.service.core;

import java.util.HashSet;
import java.util.Set;

import org.springframework.core.env.Environment;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.utils.StringResource;

@Service
public class AuthenticationService {
  private final Environment environment;

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