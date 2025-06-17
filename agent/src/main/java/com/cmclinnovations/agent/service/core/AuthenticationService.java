package com.cmclinnovations.agent.service.core;

import java.util.HashSet;
import java.util.Set;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthenticationService {
  /**
   * Constructs a new service.
   */
  public AuthenticationService() {
    // No initialisation step is required
  }

  /**
   * Get roles for the current user and request submitted.
   */
  public Set<String> getRoles() {
    Set<String> roles = new HashSet<>();
    SecurityContextHolder.getContext().getAuthentication().getAuthorities().forEach(authority -> {
      // Filter out SCOPE authorities
      if (authority != null && !authority.getAuthority().startsWith("SCOPE_")) {
        roles.add(authority.getAuthority());
      }
    });
    return roles;
  }
}