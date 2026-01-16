package com.cmclinnovations.agent.model.type;

public enum TrackActionType {
  CREATION("Created"),
  MODIFICATION("Modified"),
  IGNORED("");

  private final String message;

  TrackActionType(String message) {
    this.message = message;
  }

  public String getMessage() {
    return this.message;
  }
}