package com.cmclinnovations.agent.service.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.LifecycleTaskOperationType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.ChangelogService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

@Service
public class LifecycleTaskBatchService {
  private final AddService addService;
  private final ChangelogService changelogService;
  private final DateTimeService dateTimeService;
  private final DeleteService deleteService;
  private final GetService getService;
  private final LifecycleQueryService lifecycleQueryService;
  private final LifecycleTaskService lifecycleTaskService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private final LifecycleQueryFactory lifecycleQueryFactory;

  private static final int NUM_DAY_ORDER_GEN = 30;
  private static final Logger LOGGER = LogManager.getLogger(LifecycleTaskBatchService.class);

  public LifecycleTaskBatchService(AddService addService, ChangelogService changelogService,
      DateTimeService dateTimeService, DeleteService deleteService, GetService getService,
      LifecycleQueryService lifecycleQueryService, LifecycleTaskService lifecycleTaskService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.changelogService = changelogService;
    this.dateTimeService = dateTimeService;
    this.deleteService = deleteService;
    this.getService = getService;
    this.lifecycleQueryService = lifecycleQueryService;
    this.lifecycleTaskService = lifecycleTaskService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();
  }

  /**
   * Signal the commencement of the services for the specified contracts.
   */
  public ResponseEntity<StandardApiResponse<?>> commenceContracts(List<String> contractIds,
      Map<String, Object> params) {
    Map<String, String> nextTaskStartDates = new LinkedHashMap<>();
    contractIds.forEach(contractId -> nextTaskStartDates.put(contractId, null));
    Map<String, Queue<String>> occurrencesByContract = this.getOrderReceivedOccurrenceDatesByContract(
        nextTaskStartDates);

    return this.commenceContractBatch(occurrencesByContract, params);
  }

  /**
   * Generates orders and approval occurrences for the specified contracts.
   *
   * @param occurrencesByContract Target occurrence dates indexed by contract.
   * @param params                Approval occurrence parameters.
   * @return Response describing the contract commencement outcome.
   */
  private ResponseEntity<StandardApiResponse<?>> commenceContractBatch(
      Map<String, Queue<String>> occurrencesByContract, Map<String, Object> params) {
    boolean hasOccurrenceError = this.genOrderReceivedOccurrences(occurrencesByContract);
    if (hasOccurrenceError) {
      String partialErrorMsg = LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY);
      LOGGER.warn(partialErrorMsg);
      return this.responseEntityBuilder.error(
          partialErrorMsg,
          HttpStatus.INTERNAL_SERVER_ERROR);
    }

    LOGGER.info("All orders has been successfully received!");
    boolean hasApprovalError = this.genApprovalOccurrences(occurrencesByContract.keySet(), params);
    if (hasApprovalError) {
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_APPROVE_PARTIAL_KEY),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return this.responseEntityBuilder
        .success("contract", LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_APPROVED_KEY));
  }

  /**
   * Generates approval occurrences and activity records for the specified contracts.
   *
   * @param contractIds Target contract identifiers.
   * @param params      Approval occurrence parameters.
   * @return Boolean indicating whether an error occurred.
   */
  private boolean genApprovalOccurrences(Set<String> contractIds,
      Map<String, Object> params) {
    if (contractIds.isEmpty()) {
      return false;
    }

    List<Map<String, Object>> approvalParams = new ArrayList<>();
    for (String contractId : contractIds) {
      Map<String, Object> approval = new HashMap<>(params);
      approval.put(LifecycleResource.CONTRACT_KEY, contractId);
      approval.remove(QueryResource.ID_KEY);
      approval.remove(LifecycleResource.INSTANCE_KEY);
      this.lifecycleQueryService.addOccurrenceParams(approval, LifecycleEventType.APPROVED);
      approvalParams.add(approval);
    }

    try {
      ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiateBatch(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, approvalParams);
      if (response.getStatusCode() != HttpStatus.OK) {
        return true;
      }

      List<String> contractIris = contractIds.stream()
          .map(contractId -> this.lifecycleQueryService.getInstance(FileService.CONTRACT_QUERY_RESOURCE, contractId)
              .getFieldValue(QueryResource.IRI_KEY))
          .toList();
      response = this.logActionsBatch(contractIris, TrackActionType.APPROVED);
      return response.getStatusCode() != HttpStatus.OK;
    } catch (IllegalStateException _) {
      LOGGER.warn("Something went wrong with instantiating the approve events!");
      return true;
    }
  }

  /**
   * Updates lifecycle event details for multiple tasks, then logs their
   * activities in a separate batch.
   *
   * @param type  Lifecycle operation type.
   * @param items Task details to update.
   * @return Response describing the batch operation outcome.
   */
  public ResponseEntity<StandardApiResponse<?>> updateTaskEventDetails(String type,
      List<Map<String, Object>> items) {
    LifecycleTaskOperationType config = this.getConfig(type);
    Set<String> taskIds = this.validateAndGetTaskIds(items);
    LifecycleEventType[] previousEventTypes = config.getPreviousEventTypes();

    Map<String, String> previousOccurrences = this.lifecycleTaskService.getPreviousOccurrences(
        taskIds, QueryResource.IRI_KEY, previousEventTypes);
    this.requirePreviousOccurrences(taskIds, previousOccurrences);

    // Reuse predecessor results when activity history targets the same event type.
    LifecycleEventType[] activityTargetEventTypes = config.getActivityTargetEventTypes();
    Map<String, String> activityTargets = Arrays.equals(activityTargetEventTypes, previousEventTypes)
        ? previousOccurrences
        : this.lifecycleTaskService.getPreviousOccurrences(
            taskIds, QueryResource.IRI_KEY, activityTargetEventTypes);
    this.requirePreviousOccurrences(taskIds, activityTargets);

    this.prepareItems(items, previousOccurrences, config);

    ResponseEntity<StandardApiResponse<?>> response = this.deleteService.deleteLifecycleOccurrences(
        config.getEventType().getId(), taskIds, config.getEventType());
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    response = this.addService.instantiateBatch(config.getEventType().getId(), items);
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    // Log only after every dispatch and its SHACL processing has succeeded.
    List<String> activityTargetIris = taskIds.stream().map(activityTargets::get).toList();
    response = this.logActionsBatch(activityTargetIris, config.getTrackAction());
    if (response.getStatusCode() != HttpStatus.OK) {
      return response;
    }

    return this.responseEntityBuilder.success("task",
        LocalisationTranslator.getMessage(config.getBulkSuccessMessageKey()));
  }

  /**
   * Generates and instantiates activity records for the specified targets.
   *
   * @param targetIris  Target instance IRIs.
   * @param trackAction Action to record for each target.
   * @return Response describing the activity batch outcome.
   */
  private ResponseEntity<StandardApiResponse<?>> logActionsBatch(List<String> targetIris,
      TrackActionType trackAction) {
    String agentIri = this.instantiateAgent();
    List<Map<String, Object>> activityParams = this.changelogService.logActions(
        targetIris, trackAction, agentIri);
    return this.addService.instantiateBatch(QueryResource.HISTORY_ACTIVITY_RESOURCE, activityParams);
  }

  /**
   * Creates the authenticated agent when authentication is enabled.
   *
   * @return Instantiated agent IRI, or null when authentication is disabled.
   */
  private String instantiateAgent() {
    Map<String, Object> agentParams = this.changelogService.setAgent();
    // Authentication-disabled requests do not create or reference an agent.
    if (agentParams.isEmpty()) {
      return null;
    }
    return this.addService.instantiate(
        QueryResource.HISTORY_AGENT_RESOURCE, agentParams, TrackActionType.IGNORED).getBody().data().id();
  }

  /**
   * Retrieves the processing configuration for a supported lifecycle operation.
   *
   * @param type Lifecycle operation type.
   * @return Configuration for processing the requested operation.
   */
  private LifecycleTaskOperationType getConfig(String type) {
    LifecycleTaskOperationType config = LifecycleTaskOperationType.fromId(type);
    if (config == null || !config.supportsBulk()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    }
    return config;
  }

  /**
   * Validates task inputs and returns their unique identifiers.
   *
   * @param items Task details to validate.
   * @return Validated task identifiers.
   */
  private Set<String> validateAndGetTaskIds(List<Map<String, Object>> items) {
    if (items == null) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY, "items"));
    }
    if (items.isEmpty()) {
      throw new IllegalArgumentException("At least one task is required!");
    }

    Set<String> taskIds = new HashSet<>();
    for (Map<String, Object> item : items) {
      if (item == null) {
        throw new IllegalArgumentException("Task details cannot be null!");
      }
      String taskId = this.getRequiredValue(item, QueryResource.ID_KEY);
      this.getRequiredValue(item, LifecycleResource.CONTRACT_KEY);
      // Reject duplicate tasks before any lifecycle mutation occurs.
      if (!taskIds.add(taskId)) {
        throw new IllegalArgumentException("Duplicate task identifier: " + taskId);
      }
    }
    return taskIds;
  }

  /**
   * Retrieves a required non-blank value from a task input.
   *
   * @param item  Task details containing the value.
   * @param field Required field name.
   * @return Required value as a string.
   */
  private String getRequiredValue(Map<String, Object> item, String field) {
    Object value = item.get(field);
    if (value == null || value.toString().isBlank()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY, field));
    }
    return value.toString();
  }

  /**
   * Verifies that every requested task has a matching previous occurrence.
   *
   * @param taskIds            Requested task identifiers.
   * @param previousOccurrences Previous occurrences indexed by task identifier.
   */
  private void requirePreviousOccurrences(Set<String> taskIds, Map<String, String> previousOccurrences) {
    List<String> missingTaskIds = taskIds.stream()
        .filter(taskId -> !previousOccurrences.containsKey(taskId))
        .toList();
    if (!missingTaskIds.isEmpty()) {
      throw new NullPointerException(
          "No valid previous occurrence found for task identifiers: " + String.join(", ", missingTaskIds));
    }
  }

  /**
   * Adds generated lifecycle values to every task before batch instantiation.
   *
   * @param items               Task details to prepare.
   * @param previousOccurrences Previous occurrences indexed by task identifier.
   * @param config              Lifecycle operation configuration.
   */
  private void prepareItems(List<Map<String, Object>> items, Map<String, String> previousOccurrences,
      LifecycleTaskOperationType config) {
    // Reuse the stage IRI for tasks belonging to the same contract.
    Map<String, String> stagesByContract = new HashMap<>();
    for (Map<String, Object> item : items) {
      String contractId = item.get(LifecycleResource.CONTRACT_KEY).toString();
      String stage = stagesByContract.computeIfAbsent(contractId,
          id -> this.lifecycleQueryService.getInstance(FileService.CONTRACT_STAGE_QUERY_RESOURCE, id,
              config.getEventType().getStage()).getFieldValue(QueryResource.IRI_KEY));

      item.put(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
      item.put(LifecycleResource.STAGE_KEY, stage);
      item.put(LifecycleResource.REMARKS_KEY, config.getRemarks());
      item.put(LifecycleResource.ORDER_KEY, previousOccurrences.get(item.get(QueryResource.ID_KEY).toString()));
      if (config.getEventStatus() != null) {
        item.put(LifecycleResource.EVENT_STATUS_KEY, config.getEventStatus());
      }
    }
  }

  /**
   * Check for active contract and generate orders up to the limit date.
   */
  public void genOrderActiveContracts() {
    String todayString = this.dateTimeService.getCurrentDate();
    String taskGenerationCutoffDate = this.dateTimeService.getFutureDate(todayString, NUM_DAY_ORDER_GEN);
    LOGGER.info("Retrieving all active contracts that need orders to be generated...");
    String query = this.lifecycleQueryFactory.getLatestOrderQuery(taskGenerationCutoffDate);
    Queue<SparqlBinding> results = this.getService.getInstances(query);
    Map<String, String> nextTaskStartDates = new LinkedHashMap<>();
    while (!results.isEmpty()) {
      SparqlBinding resultRow = results.poll();
      String currentContract = resultRow.getFieldValue(QueryResource.ID_KEY);
      // Latest task date for the contract
      String latestTaskDate = resultRow.getFieldValue(QueryResource.LATEST_DATE_VAR.getVarName());
      String nextTaskStartDate = this.dateTimeService.getFutureDate(latestTaskDate, 1);
      LOGGER.info("Generating orders for contract {}, starting from {}", currentContract, nextTaskStartDate);
      nextTaskStartDates.put(currentContract, nextTaskStartDate);
    }
    Map<String, Queue<String>> occurrencesByContract = this.getOrderReceivedOccurrenceDatesByContract(
        nextTaskStartDates);
    this.genOrderReceivedOccurrences(occurrencesByContract);
  }

  /**
   * Generate occurrences for the order received event of the specified contracts.
   * 
   * @param occurrencesByContract Target occurrence dates indexed by contract.
   * @return Boolean indicating whether an error occurred.
   */
  public boolean genOrderReceivedOccurrences(Map<String, Queue<String>> occurrencesByContract) {
    List<Map<String, Object>> occurrenceParams = new ArrayList<>();
    List<String> occurrenceIris = new ArrayList<>();
    for (Map.Entry<String, Queue<String>> entry : occurrencesByContract.entrySet()) {
      String contract = entry.getKey();
      Queue<String> occurrences = entry.getValue();
      LOGGER.info("Generating all orders for the active contract {}...", contract);
      try {
        // Add parameter template
        Map<String, Object> occurrenceTemplate = new HashMap<>();
        occurrenceTemplate.put(LifecycleResource.CONTRACT_KEY, contract);
        occurrenceTemplate.put(LifecycleResource.REMARKS_KEY, LifecycleResource.ORDER_INITIALISE_MESSAGE);
        this.lifecycleQueryService.addOccurrenceParams(occurrenceTemplate,
            LifecycleEventType.SERVICE_ORDER_RECEIVED);
        String orderPrefix = StringResource.getPrefix(
            occurrenceTemplate.get(LifecycleResource.STAGE_KEY).toString());
        // Prepare each occurrence
        while (!occurrences.isEmpty()) {
          // Retrieve and update the date of occurrence
          String occurrenceDate = occurrences.poll();
          Map<String, Object> occurrence = new HashMap<>(occurrenceTemplate);
          // set new id each time
          occurrence.remove(QueryResource.ID_KEY);
          LifecycleResource.genIdAndInstanceParameters(orderPrefix, LifecycleEventType.SERVICE_ORDER_RECEIVED,
              occurrence);
          occurrence.put(LifecycleResource.DATE_TIME_KEY, occurrenceDate);
          occurrenceParams.add(occurrence);
          occurrenceIris.add(occurrence.get(LifecycleResource.INSTANCE_KEY).toString());
        }
      } catch (RuntimeException e) {
        LOGGER.error("Failed to prepare order occurrences for contract {}.", contract, e);
        throw e;
      }
    }

    if (occurrenceParams.isEmpty()) {
      LOGGER.warn("No order occurrences were prepared.");
      return false;
    }

    try {
      ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiateBatch(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, occurrenceParams);
      if (response.getStatusCode() != HttpStatus.OK) {
        LOGGER.warn("Order occurrence batch failed with status {}.", response.getStatusCode());
        return true;
      }

      response = this.logActionsBatch(occurrenceIris, TrackActionType.CREATION);
      if (response.getStatusCode() != HttpStatus.OK) {
        LOGGER.warn("Order creation activity batch failed with status {}.", response.getStatusCode());
        return true;
      }
      return false;
    } catch (IllegalStateException e) {
      LOGGER.error("Failed to instantiate order occurrences or activities.", e);
      return true;
    }
  }

  /**
   * Retrieve occurrence dates for each specified contract.
   *
   * @param nextTaskStartDates Optional next task start dates indexed by contract.
   * @return Target occurrence dates indexed by contract.
   */
  private Map<String, Queue<String>> getOrderReceivedOccurrenceDatesByContract(
      Map<String, String> nextTaskStartDates) {
    Map<String, Queue<String>> occurrencesByContract = new LinkedHashMap<>();
    nextTaskStartDates.forEach((contract, nextTaskStartDate) -> occurrencesByContract.put(contract,
        this.getOrderReceivedOccurrenceDates(contract, nextTaskStartDate)));
    return occurrencesByContract;
  }

  /**
   * Retrieve occurrence dates for the order received event of a specified
   * contract.
   *
   * @param contract          Target contract.
   * @param nextTaskStartDate Optional parameter that indicates the next task
   *                          start date. If provided, this will overwrite the
   *                          contract start date.
   * @return Target occurrence dates.
   */
  private Queue<String> getOrderReceivedOccurrenceDates(String contract, String nextTaskStartDate) {
    // Retrieve schedule information for the specific contract
    SparqlBinding bindings = this.lifecycleQueryService.querySchedule(contract);
    // Extract specific schedule info
    String startDate = nextTaskStartDate != null ? nextTaskStartDate
        : bindings.getFieldValue(QueryResource.SCHEDULE_START_DATE_VAR.getVarName());
    // For non-perpetual schedules, get earliest date cutoff or contract end date
    String endDate = null;
    if (bindings.containsField(QueryResource.SCHEDULE_END_DATE_VAR.getVarName())) {
      String endDateVal = bindings.getFieldValue(QueryResource.SCHEDULE_END_DATE_VAR.getVarName());
      endDate = this.dateTimeService.getEarliestDateOrContractEnd(endDateVal, NUM_DAY_ORDER_GEN);
    }
    String recurrence = bindings.getFieldValue(LifecycleResource.SCHEDULE_RECURRENCE_PLACEHOLDER_KEY);
    Queue<String> occurrences = new ArrayDeque<>();
    // Handle as fixed date schedule first
    if (bindings.containsField(QueryResource.FIXED_DATE_DATE_KEY)) {
      List<Map<String, SparqlResponseField>> entryDates = bindings.getList(QueryResource.FIXED_DATE_DATE_KEY);
      List<String> entryDateStrings = entryDates.stream()
          .map(entryDate -> entryDate.get(QueryResource.FIXED_DATE_DATE_KEY).value())
          .collect(Collectors.toList());
      occurrences = this.dateTimeService.getOccurrenceDates(entryDateStrings, endDate);
    } else {
      // Extract date of occurrences based on the schedule information
      // For perpetual and single time schedules, simply add the start date
      if (recurrence == null || recurrence.equals(LifecycleResource.RECURRENCE_DAILY_TASK)) {
        occurrences.offer(this.dateTimeService.getDateTimeFromDate(startDate));
      } else if (recurrence.equals(LifecycleResource.RECURRENCE_ALT_DAY_TASK)) {
        // Alternate day recurrence should have dual interval
        occurrences = this.dateTimeService.getOccurrenceDates(startDate, endDate, 2);
      } else {
        // Note that this may run for other intervals like P3D but
        // an error will be thrown in the following method unless the recurrence is in
        // intervals of 7
        int weeklyInterval = this.dateTimeService.getWeeklyInterval(recurrence);
        occurrences = this.dateTimeService.getOccurrenceDates(startDate, endDate, bindings, weeklyInterval);
      }
    }
    return occurrences;
  }
}
