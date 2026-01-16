package com.cmclinnovations.agent.model.type;

public enum TrackActionType {
  CREATION("Created"),
  MODIFICATION("Modified"),
  CONTRACT_RESET_STATUS("Verified with client; Pending approval"),
  IGNORED("");

  private final String message;

  TrackActionType(String message) {
    this.message = message;
  }

  public String getMessage() {
    return this.message;
  }
}