package com.cmclinnovations.agent.service.application;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.core.ChangelogService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;

@Service
public class LifecycleTaskBatchService {
  private final AddService addService;
  private final ChangelogService changelogService;
  private final DateTimeService dateTimeService;
  private final DeleteService deleteService;
  private final LifecycleQueryService lifecycleQueryService;
  private final LifecycleTaskService lifecycleTaskService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final String ORDER_DISPATCH_MESSAGE = "Order has been assigned and is awaiting execution.";

  public LifecycleTaskBatchService(AddService addService, ChangelogService changelogService,
      DateTimeService dateTimeService, DeleteService deleteService, LifecycleQueryService lifecycleQueryService,
      LifecycleTaskService lifecycleTaskService, ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.changelogService = changelogService;
    this.dateTimeService = dateTimeService;
    this.deleteService = deleteService;
    this.lifecycleQueryService = lifecycleQueryService;
    this.lifecycleTaskService = lifecycleTaskService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Updates lifecycle event details for multiple tasks, then logs their
   * activities in a separate batch.
   */
  public ResponseEntity<StandardApiResponse<?>> updateTaskEventDetails(String type,
      List<Map<String, Object>> items) {
    BatchEventConfig config = this.getConfig(type);
    List<String> taskIds = this.validateAndGetTaskIds(items);

    Map<String, String> previousOccurrences = this.lifecycleTaskService.getPreviousOccurrences(
        taskIds, QueryResource.IRI_KEY, config.previousEventTypes());
    this.requirePreviousOccurrences(taskIds, previousOccurrences);

    // Reuse predecessor results when activity history targets the same event type.
    Map<String, String> activityTargets = Arrays.equals(
        config.activityTargetEventTypes(), config.previousEventTypes())
        ? previousOccurrences
        : this.lifecycleTaskService.getPreviousOccurrences(
            taskIds, QueryResource.IRI_KEY, config.activityTargetEventTypes());
    this.requirePreviousOccurrences(taskIds, activityTargets);

    this.prepareItems(items, previousOccurrences, config);

    ResponseEntity<StandardApiResponse<?>> response = this.deleteService.deleteLifecycleOccurrences(
        config.eventType().getId(), taskIds, config.eventType());
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    response = this.addService.instantiateBatch(Map.of(config.eventType().getId(), items));
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    // Log only after every dispatch and its SHACL processing has succeeded.
    String agentIri = this.instantiateAgent();
    List<String> activityTargetIris = taskIds.stream().map(activityTargets::get).toList();
    List<Map<String, Object>> activityParams = this.changelogService.prepareActivities(
        activityTargetIris, config.trackAction(), agentIri);
    response = this.addService.instantiateBatch(Map.of(QueryResource.HISTORY_ACTIVITY_RESOURCE, activityParams));
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    return this.responseEntityBuilder.success("task",
        LocalisationTranslator.getMessage(config.successMessageKey()));
  }

  private String instantiateAgent() {
    Map<String, Object> agentParams = this.changelogService.setAgent();
    // Authentication-disabled requests do not create or reference an agent.
    if (agentParams.isEmpty()) {
      return null;
    }
    return this.addService.instantiate(
        QueryResource.HISTORY_AGENT_RESOURCE, agentParams, TrackActionType.IGNORED).getBody().data().id();
  }

  private BatchEventConfig getConfig(String type) {
    if (type == null) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    }
    return switch (type.toLowerCase(Locale.ROOT)) {
      case "dispatch" -> new BatchEventConfig(
          LifecycleEventType.SERVICE_ORDER_DISPATCHED,
          new LifecycleEventType[] { LifecycleEventType.SERVICE_ORDER_RECEIVED },
          new LifecycleEventType[] { LifecycleEventType.SERVICE_ORDER_RECEIVED },
          TrackActionType.ASSIGNMENT,
          ORDER_DISPATCH_MESSAGE,
          null,
          LocalisationResource.SUCCESS_CONTRACT_TASK_BULK_ASSIGN_KEY);
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
  }

  private List<String> validateAndGetTaskIds(List<Map<String, Object>> items) {
    if (items == null) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY, "items"));
    }
    if (items.isEmpty()) {
      throw new IllegalArgumentException("At least one task is required!");
    }

    List<String> taskIds = new ArrayList<>();
    Set<String> uniqueTaskIds = new HashSet<>();
    for (Map<String, Object> item : items) {
      if (item == null) {
        throw new IllegalArgumentException("Task details cannot be null!");
      }
      String taskId = this.getRequiredValue(item, QueryResource.ID_KEY);
      this.getRequiredValue(item, LifecycleResource.CONTRACT_KEY);
      // Reject duplicate tasks before any lifecycle mutation occurs.
      if (!uniqueTaskIds.add(taskId)) {
        throw new IllegalArgumentException("Duplicate task identifier: " + taskId);
      }
      taskIds.add(taskId);
    }
    return taskIds;
  }

  private String getRequiredValue(Map<String, Object> item, String field) {
    Object value = item.get(field);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY, field));
    }
    return value.toString();
  }

  private void requirePreviousOccurrences(List<String> taskIds, Map<String, String> previousOccurrences) {
    List<String> missingTaskIds = taskIds.stream()
        .filter(taskId -> !previousOccurrences.containsKey(taskId))
        .toList();
    if (!missingTaskIds.isEmpty()) {
      throw new NullPointerException(
          "No valid previous occurrence found for task identifiers: " + String.join(", ", missingTaskIds));
    }
  }

  private void prepareItems(List<Map<String, Object>> items, Map<String, String> previousOccurrences,
      BatchEventConfig config) {
    // Reuse the stage IRI for tasks belonging to the same contract.
    Map<String, String> stagesByContract = new HashMap<>();
    for (Map<String, Object> item : items) {
      String contractId = item.get(LifecycleResource.CONTRACT_KEY).toString();
      String stage = stagesByContract.computeIfAbsent(contractId,
          id -> this.lifecycleQueryService.getInstance(FileService.CONTRACT_STAGE_QUERY_RESOURCE, id,
              config.eventType().getStage()).getFieldValue(QueryResource.IRI_KEY));

      item.put(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
      item.put(LifecycleResource.STAGE_KEY, stage);
      item.put(LifecycleResource.REMARKS_KEY, config.remarks());
      item.put(LifecycleResource.ORDER_KEY, previousOccurrences.get(item.get(QueryResource.ID_KEY).toString()));
      if (config.eventStatus() != null) {
        item.put(LifecycleResource.EVENT_STATUS_KEY, config.eventStatus());
      }
    }
  }

  private record BatchEventConfig(
      LifecycleEventType eventType,
      LifecycleEventType[] previousEventTypes,
      LifecycleEventType[] activityTargetEventTypes,
      TrackActionType trackAction,
      String remarks,
      String eventStatus,
      String successMessageKey) {
  }
}
