package com.cmclinnovations.agent.service.core;

import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;

@Service
public class ChangelogService {
  private final DateTimeService dateTimeService;

  /**
   * Constructs a new service with the following dependencies.
   */
  public ChangelogService(DateTimeService dateTimeService) {
    this.dateTimeService = dateTimeService;
  }

  /**
   * Generates a map of replacements for logging the action on an entity.
   * 
   * @param iri    The entity IRI to be appended with the corresponding action
   *               logs.
   * @param action The action to be logged.
   */
  public Map<String, Object> logAction(String iri, TrackActionType action) {
    if (action == TrackActionType.IGNORED) {
      throw new IllegalArgumentException("TrackActionType.IGNORED is not a valid action for logging.");
    }
    String currentDateTime = this.dateTimeService.getCurrentDateTime();
    Map<String, Object> replacements = new HashMap<>();
    replacements.put(QueryResource.IRI_KEY, iri);
    replacements.put(LifecycleResource.REMARKS_KEY, action.getMessage());
    replacements.put(LifecycleResource.TIMESTAMP_KEY, currentDateTime);
    return replacements;
  }
}
