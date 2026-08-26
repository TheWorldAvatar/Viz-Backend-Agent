package com.cmclinnovations.agent.service.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.response.UserProfile;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;

@Service
public class ChangelogService {
  private final AuthenticationService authenticationService;
  private final DateTimeService dateTimeService;
  private static final String UPDATED_SINCE_QUERY_TEMPLATE = "?changelog <https://theworldavatar.io/kg/ontochangelog/affected> ?iri;\r\n"
      + "<https://theworldavatar.io/kg/ontochangelog/timestamp> ?timestamp.\r\n" +
      "FILTER(?timestamp > \"" + FileService.REPLACEMENT_TARGET + "\"^^xsd:dateTime)";

  /**
   * Constructs a new service with the following dependencies.
   */
  public ChangelogService(AuthenticationService authenticationService, DateTimeService dateTimeService) {
    this.authenticationService = authenticationService;
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
    return this.prepareActivity(iri, action, this.dateTimeService.getCurrentDateTime());
  }

  /**
   * Generates activity replacements for multiple affected entities without
   * persisting them.
   *
   * @param iris     Entity IRIs affected by the action.
   * @param action   Action to log for every entity.
   * @param agentIri Optional agent IRI shared by the activities.
   */
  public List<Map<String, Object>> prepareActivities(Collection<String> iris, TrackActionType action,
      String agentIri) {
    if (iris == null || iris.isEmpty() || iris.stream().anyMatch(iri -> iri == null || iri.isBlank())) {
      throw new IllegalArgumentException("At least one affected entity IRI is required!");
    }
    // Use one timestamp for every activity in the same batch action.
    String timestamp = this.dateTimeService.getCurrentDateTime();
    List<Map<String, Object>> activities = new ArrayList<>();
    for (String iri : iris) {
      Map<String, Object> activity = this.prepareActivity(iri, action, timestamp);
      // Assign IDs before the activity maps are rendered as one payload.
      activity.put(QueryResource.ID_KEY, UUID.randomUUID().toString());
      if (agentIri != null && !agentIri.isBlank()) {
        activity.put(QueryResource.HISTORY_AGENT_RESOURCE, agentIri);
      }
      activities.add(activity);
    }
    return activities;
  }

  private Map<String, Object> prepareActivity(String iri, TrackActionType action, String timestamp) {
    if (action == TrackActionType.IGNORED) {
      throw new IllegalArgumentException("TrackActionType.IGNORED is not a valid action for logging.");
    }
    Map<String, Object> replacements = new HashMap<>();
    replacements.put(QueryResource.IRI_KEY, iri);
    replacements.put(QueryResource.HISTORY_ACTIVITY_RESOURCE, action.getClazz());
    replacements.put(LifecycleResource.TIMESTAMP_KEY, timestamp);
    return replacements;
  }

  /**
   * Generates a map of replacements to set the agent profile.
   */
  public Map<String, Object> setAgent() {
    if (this.authenticationService.isAuthenticationEnabled()) {
      UserProfile profile = this.authenticationService.getUserProfile();
      Map<String, Object> replacements = new HashMap<>();
      replacements.put(QueryResource.ID_KEY, profile.id());
      replacements.put(ShaclResource.NAME_PROPERTY, profile.name());
      return replacements;
    }
    return new HashMap<>();
  }

  /**
   * Builds a filter query for changes since a specific point in time.
   * 
   * @param timestamp The timestamp input in UNIX seconds.
   */
  public String buildDeltaFilterQuery(String timestamp) {
    if (timestamp == null || timestamp.isBlank()) {
      return "";
    }
    String currentDateTime = this.dateTimeService.getDateTimeFromTimestamp(timestamp);
    return UPDATED_SINCE_QUERY_TEMPLATE.replace(FileService.REPLACEMENT_TARGET, currentDateTime);
  }
}
