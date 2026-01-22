package com.cmclinnovations.agent.model.type;

public enum TrackActionType {
  CREATION("Created"),
  MODIFICATION("Modified"),
  CONTRACT_RESET_STATUS("Verified with client; Pending approval"),
  APPROVED("Approved"),
  ASSIGNMENT("Assigned"),
  CANCELLATION("Cancelled"),
  COMPLETION("Completed"),
  SAVED_COMPLETION("Saved completion details"),
  ISSUE_REPORT("Reported issue"),
  RESCINDMENT("Rescinded"),
  RESCHEDULED("Rescheduled"),
  IGNORED("");

  private final String message;

  TrackActionType(String message) {
    this.message = message;
  }

  public String getMessage() {
    return this.message;
  }
}