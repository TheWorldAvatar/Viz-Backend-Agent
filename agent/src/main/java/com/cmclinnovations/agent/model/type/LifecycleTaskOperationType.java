package com.cmclinnovations.agent.model.type;

import com.cmclinnovations.agent.utils.LocalisationResource;

/**
 * Defines the configuration for supported service task lifecycle operations.
 */
public enum LifecycleTaskOperationType {
  DISPATCH(
      "dispatch",
      LifecycleEventType.SERVICE_ORDER_DISPATCHED,
      TrackActionType.ASSIGNMENT,
      "Order has been assigned and is awaiting execution.",
      null,
      LocalisationResource.SUCCESS_CONTRACT_TASK_ASSIGN_KEY,
      LocalisationResource.SUCCESS_CONTRACT_TASK_BULK_ASSIGN_KEY,
      eventTypes(LifecycleEventType.SERVICE_ORDER_RECEIVED),
      LifecycleEventType.SERVICE_ORDER_RECEIVED);

  private final String id;
  private final LifecycleEventType eventType;
  private final TrackActionType trackAction;
  private final String remarks;
  private final String eventStatus;
  private final String successMessageKey;
  private final String bulkSuccessMessageKey;
  private final LifecycleEventType[] activityTargetEventTypes;
  private final LifecycleEventType[] previousEventTypes;

  LifecycleTaskOperationType(String id, LifecycleEventType eventType, TrackActionType trackAction, String remarks,
      String eventStatus, String successMessageKey, String bulkSuccessMessageKey,
      LifecycleEventType[] activityTargetEventTypes, LifecycleEventType... previousEventTypes) {
    this.id = id;
    this.eventType = eventType;
    this.trackAction = trackAction;
    this.remarks = remarks;
    this.eventStatus = eventStatus;
    this.successMessageKey = successMessageKey;
    this.bulkSuccessMessageKey = bulkSuccessMessageKey;
    this.activityTargetEventTypes = activityTargetEventTypes;
    this.previousEventTypes = previousEventTypes;
  }

  public String getId() {
    return this.id;
  }

  public LifecycleEventType getEventType() {
    return this.eventType;
  }

  public TrackActionType getTrackAction() {
    return this.trackAction;
  }

  public String getRemarks() {
    return this.remarks;
  }

  public String getEventStatus() {
    return this.eventStatus;
  }

  public String getSuccessMessageKey() {
    return this.successMessageKey;
  }

  public String getBulkSuccessMessageKey() {
    return this.bulkSuccessMessageKey;
  }

  public LifecycleEventType[] getActivityTargetEventTypes() {
    return this.activityTargetEventTypes.clone();
  }

  public LifecycleEventType[] getPreviousEventTypes() {
    return this.previousEventTypes.clone();
  }

  public boolean supportsBulk() {
    return this.bulkSuccessMessageKey != null;
  }

  /**
   * Retrieves a task operation based on its request identifier.
   *
   * @param id Request operation identifier.
   * @return Matching task operation, or null when unsupported.
   */
  public static LifecycleTaskOperationType fromId(String id) {
    if (id == null) {
      return null;
    }
    for (LifecycleTaskOperationType operation : LifecycleTaskOperationType.values()) {
      if (operation.getId().equalsIgnoreCase(id)) {
        return operation;
      }
    }
    return null;
  }

  private static LifecycleEventType[] eventTypes(LifecycleEventType... eventTypes) {
    return eventTypes;
  }
}
