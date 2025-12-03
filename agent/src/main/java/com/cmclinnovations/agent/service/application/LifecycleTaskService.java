package com.cmclinnovations.agent.service.application;

import java.util.AbstractMap;
import java.util.ArrayDeque;
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
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class LifecycleTaskService {
  private final AddService addService;
  final DateTimeService dateTimeService;
  private final GetService getService;
  private final UpdateService updateService;
  public final LifecycleQueryService lifecycleQueryService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private final LifecycleQueryFactory lifecycleQueryFactory;
  private final Map<Variable, List<Integer>> taskVarSequence = new HashMap<>();

  private static final String ORDER_INITIALISE_MESSAGE = "Order received and is being processed.";
  private static final String ORDER_DISPATCH_MESSAGE = "Order has been assigned and is awaiting execution.";
  private static final String ORDER_COMPLETE_MESSAGE = "Order has been completed successfully.";
  private static final int NUM_DAY_ORDER_GEN = 30;
  static final Logger LOGGER = LogManager.getLogger(LifecycleTaskService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   */
  public LifecycleTaskService(AddService addService, DateTimeService dateTimeService, GetService getService,
      UpdateService updateService, LifecycleQueryService lifecycleQueryService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.dateTimeService = dateTimeService;
    this.getService = getService;
    this.updateService = updateService;
    this.lifecycleQueryService = lifecycleQueryService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();

    Integer MIN_INDEX = -5;

    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.DATE_KEY), List.of(MIN_INDEX, 1));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY), List.of(MIN_INDEX, 2));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_KEY), List.of(MIN_INDEX, 3));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_ID_KEY), List.of(1000, 999));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY), List.of(1000, 1000));
    this.taskVarSequence.put(QueryResource.SCHEDULE_RECURRENCE_VAR, List.of(1000, 1001));
  }

  /**
   * Retrieve the number of task in the specific stage and status.
   * 
   * @param resourceID     The target resource identifier for the instance class.
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param isClosed       Indicates whether to retrieve closed tasks.
   * @param filters        Mappings between filter fields and their values.
   */
  public ResponseEntity<StandardApiResponse<?>> getOccurrenceCount(String resourceID, String startTimestamp,
      String endTimestamp, boolean isClosed, Map<String, String> filters) {
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, false);
    String[] queryStatement = this.genLifecycleStatements(startTimestamp, endTimestamp, new HashSet<>(), parsedFilters,
        "", isClosed, false);
    return this.responseEntityBuilder.success(null,
        String.valueOf(
            this.getService.getCount(resourceID, queryStatement[0], LifecycleResource.TASK_ID_SORT_BY_PARAMS, filters,
                false)));
  }

  /**
   * Retrieve filter options at the task level.
   * 
   * @param resourceID     The target resource identifier for the instance class.
   * @param field          The field of filtering.
   * @param search         String subset to narrow filter scope.
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param isClosed       Indicates whether to retrieve closed tasks.
   * @param filters        Optional additional filters.
   */
  public List<String> getFilterOptions(String resourceID, String field, String search, String startTimestamp,
      String endTimestamp, boolean isClosed, Map<String, String> filters) {
    String originalField = LifecycleResource.revertLifecycleSpecialFields(field, false);
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, false);
    parsedFilters.remove(originalField);
    String[] queryStatement = this.genLifecycleStatements(startTimestamp, endTimestamp, new HashSet<>(), parsedFilters,
        originalField, isClosed, false);
    List<String> options = this.getService.getAllFilterOptions(resourceID, originalField, queryStatement[0], search,
        parsedFilters);
    if (originalField.equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
      return options.stream().map(option -> LocalisationTranslator.getScheduleTypeFromRecurrence(option)).toList();
    } else if (originalField.equals(LifecycleResource.EVENT_KEY)) {
      return options.stream().map(option -> LocalisationTranslator.getEvent(option)).toList();
    }
    return options;
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date.
   * 
   * @param contract   The contract identifier.
   * @param entityType Target resource ID.
   * @param pagination Pagination state to filter results.
   */
  public ResponseEntity<StandardApiResponse<?>> getOccurrences(String contract, String entityType,
      PaginationState pagination) {
    // Map<String, String> lifecycleStatements =
    // this.lifecycleQueryFactory.getServiceTasksQuery(contract, null, null,
    // null);
    // List<Map<String, Object>> occurrences =
    // this.executeOccurrenceQuery(entityType, lifecycleStatements, null,
    // pagination);
    LOGGER.info("Successfuly retrieved all associated services!");
    return this.responseEntityBuilder.success(null, "Under construction");
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date(s).
   * 
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param entityType     Target resource ID.
   * @param isClosed       Indicates whether to retrieve closed tasks.
   * @param pagination     Pagination state to filter results.
   */
  public ResponseEntity<StandardApiResponse<?>> getOccurrences(String startTimestamp, String endTimestamp,
      String entityType, boolean isClosed, PaginationState pagination) {
    String[] lifecycleStatements = this.genLifecycleStatements(startTimestamp, endTimestamp,
        pagination.getSortedFields(), pagination.getFilters(), "", isClosed, true);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, lifecycleStatements,
        isClosed, pagination);
    LOGGER.info("Successfuly retrieved tasks!");
    return this.responseEntityBuilder.success(null, occurrences);
  }

  /**
   * Generates additional query statements for the active service stage during the
   * lifecycle.
   * 
   * @param startTimestamp   Start timestamp in UNIX format.
   * @param endTimestamp     End timestamp in UNIX format.
   * @param sortedFields     Set of fields for sorting that should be included.
   * @param filters          Filters set by the user.
   * @param field            Optional target to include the corresponding filter
   *                         statements.
   * @param isClosed         Indicates whether to retrieve closed tasks.
   * @param reqOriStatements Requires that the original statements are present.
   */
  private String[] genLifecycleStatements(String startTimestamp, String endTimestamp, Set<String> sortedFields,
      Map<String, Set<String>> filters, String field, boolean isClosed, boolean reqOriStatements) {
    String[] targetStartEndDates = this.dateTimeService.getStartEndDate(startTimestamp, endTimestamp, isClosed);
    Map<String, String> statementMappings = this.lifecycleQueryFactory.getServiceTasksQuery(null,
        targetStartEndDates[0], targetStartEndDates[1], isClosed);
    Map<String, String> filterExpressions = new HashMap<>();
    Map<String, Set<String>> serviceEventFilters = new HashMap<>(filters);
    if (!field.isEmpty()) {
      // Override the field value for filter options, as it should ignore them
      serviceEventFilters.put(field, new HashSet<>());
    }
    // Get statements for dispatch events that matches any sort/filter criteria
    String addFilterQueries = this.genServiceEventsQueryStatements(LifecycleEventType.SERVICE_ORDER_DISPATCHED,
        sortedFields, serviceEventFilters, filterExpressions);
    // Non-closed tasks should not have the closed related statements
    if (isClosed) {
      addFilterQueries += this.genServiceEventsQueryStatements(LifecycleEventType.SERVICE_EXECUTION,
          sortedFields, serviceEventFilters, filterExpressions);
      addFilterQueries += this.genServiceEventsQueryStatements(LifecycleEventType.SERVICE_CANCELLATION,
          sortedFields, serviceEventFilters, filterExpressions);
      addFilterQueries += this.genServiceEventsQueryStatements(LifecycleEventType.SERVICE_INCIDENT_REPORT,
          sortedFields, serviceEventFilters, filterExpressions);
    }
    statementMappings.put(LifecycleResource.LIFECYCLE_RESOURCE,
        statementMappings.get(LifecycleResource.LIFECYCLE_RESOURCE) + addFilterQueries);

    Map<String, String> extendedMappings = this.lifecycleQueryFactory
        .insertExtendedLastModifiedFilters(statementMappings);
    String extendedDateStatement = "BIND(STR(?date) as ?" + LifecycleResource.NEW_DATE_KEY + ")";
    extendedMappings.put(LifecycleResource.DATE_KEY, extendedDateStatement);
    String lifecycleStatements = this.lifecycleQueryService.genLifecycleStatements(extendedMappings, sortedFields,
        filters, field) + filterExpressions.values().stream().collect(Collectors.joining("\n"));
    if (reqOriStatements) {
      return new String[] { lifecycleStatements,
          statementMappings.values().stream().collect(Collectors.joining("\n")) };
    } else {
      return new String[] { lifecycleStatements };
    }
  }

  /**
   * Executes the occurrence query and group them by the specified group variable.
   * 
   * @param entityType    Target resource ID.
   * @param queryMappings Additional query statements to be added if any.
   * @param isClosed      Indicates whether to retrieve closed tasks.
   * @param pagination    Pagination state to filter results.
   */
  private List<Map<String, Object>> executeOccurrenceQuery(String entityType, String[] lifecycleStatements,
      Boolean isClosed, PaginationState pagination) {
    Queue<List<String>> ids = this.getService.getAllIds(entityType, lifecycleStatements[0], pagination);
    Map<Variable, List<Integer>> varSequences = new HashMap<>(this.taskVarSequence);
    String addQuery = lifecycleStatements[1];
    addQuery += this.parseEventOccurrenceQuery(-4, LifecycleEventType.SERVICE_ORDER_DISPATCHED, varSequences);
    if (isClosed) {
      addQuery += this.parseEventOccurrenceQuery(-3, LifecycleEventType.SERVICE_EXECUTION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(-2, LifecycleEventType.SERVICE_CANCELLATION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(-1, LifecycleEventType.SERVICE_INCIDENT_REPORT, varSequences);
    }
    Queue<SparqlBinding> results = this.getService.getInstances(entityType, true, ids, addQuery, varSequences);
    return results.stream()
        .map(binding -> this.lifecycleQueryService.parseLifecycleBinding(binding.get()))
        .toList();
  }

  /**
   * Generates the query statements for service events such as dispatch, complete,
   * cancel, and report if required.
   * 
   * @param lifecycleEvent    Target event type.
   * @param sortedFields      Set of fields for sorting that should be included.
   * @param filters           Filters with name and values.
   * @param filterExpressions Mappings to store the current filter expressions
   *                          when added.
   */
  private String genServiceEventsQueryStatements(LifecycleEventType lifecycleEvent, Set<String> sortedFields,
      Map<String, Set<String>> filters, Map<String, String> filterExpressions) {
    Map<String, String> filteredStatementMappings = this.getService.getStatementMappingsForTargetFields(
        lifecycleEvent.getShaclReplacement(), sortedFields, filters);
    if (filteredStatementMappings.isEmpty()) {
      return "";
    }
    StringBuilder queryBuilder = new StringBuilder();
    filteredStatementMappings.forEach((key, value) -> {
      if (key.equals(StringResource.SORT_KEY)) {
        queryBuilder.append(value);
      } else {
        queryBuilder.append(value);
        filterExpressions.computeIfAbsent(key, expression -> {
          Set<String> filterValues = filters.get(key);
          return QueryResource.filterOrExpressions(key, filterValues);
        });
      }
    });
    String eventVar = QueryResource.genVariable(lifecycleEvent.getId() + "_event").getQueryString();
    return QueryResource.optional(
        LifecycleResource.genOccurrenceTargetQueryStatement(eventVar, lifecycleEvent)
            + queryBuilder.toString().replace(QueryResource.IRI_VAR.getQueryString(), eventVar));
  }

  /**
   * Parses the event occurrence query to extract the variables and WHERE
   * contents.
   * 
   * @param groupIndex     The group index for the variables.
   * @param lifecycleEvent Target event type.
   * @param varSequences   List of variable sequences to be added.
   */
  private String parseEventOccurrenceQuery(int groupIndex, LifecycleEventType lifecycleEvent,
      Map<Variable, List<Integer>> varSequences) {
    String replacementQueryLine = lifecycleEvent.getShaclReplacement();
    String occurrenceQuery = this.getService.getQuery(replacementQueryLine, true);
    Map<Variable, List<Integer>> dispatchVars = LifecycleResource.extractOccurrenceVariables(occurrenceQuery,
        groupIndex);
    varSequences.putAll(dispatchVars);
    return LifecycleResource.extractOccurrenceQuery(occurrenceQuery, lifecycleEvent);
  }

  /**
   * Get the task details for the specified task ID.
   * 
   * @param taskId The identifier of the task.
   */
  public ResponseEntity<StandardApiResponse<?>> getTask(String taskId) {
    SparqlBinding task = this.lifecycleQueryService.getInstance(FileService.TASK_QUERY_RESOURCE, taskId);
    return this.responseEntityBuilder.success(null, this.lifecycleQueryService.parseLifecycleBinding(task.get()));
  }

  /**
   * Generate a default occurrence instance.
   * 
   * @param params            Existing configurable parameters that will be
   *                          amended to instantiate the occurrence.
   * @param eventType         Target event type.
   * @param successLogMessage Optional log message on success.
   * @param messageResource   Optional resource id of the message to be displayed
   *                          when successful.
   */
  public ResponseEntity<StandardApiResponse<?>> genOccurrence(Map<String, Object> params,
      LifecycleEventType eventType, String successLogMessage, String messageResource) {
    return this.genOccurrence(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params, eventType, successLogMessage,
        messageResource);
  }

  /**
   * Generate an occurrence for the specified resource.
   * 
   * @param resourceId        The target lifecycle resource for instantiation.
   * @param params            Existing configurable parameters that will be
   *                          amended to instantiate the occurrence.
   * @param eventType         Target event type.
   * @param successLogMessage Optional log message on success.
   * @param messageResource   Optional resource id of the message to be displayed
   *                          when successful.
   */
  public ResponseEntity<StandardApiResponse<?>> genOccurrence(String resourceId, Map<String, Object> params,
      LifecycleEventType eventType, String successLogMessage, String messageResource) {
    this.lifecycleQueryService.addOccurrenceParams(params, eventType);
    return this.addService.instantiate(resourceId, params, successLogMessage, messageResource);
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
    while (!results.isEmpty()) {
      SparqlBinding resultRow = results.poll();
      String currentContract = resultRow.getFieldValue(QueryResource.ID_KEY);
      // Latest task date for the contract
      String latestTaskDate = resultRow.getFieldValue(QueryResource.LATEST_DATE_VAR.getVarName());
      String nextTaskStartDate = this.dateTimeService.getFutureDate(latestTaskDate, 1);
      LOGGER.info("Generating orders for contract {}, starting from {}", currentContract, nextTaskStartDate);
      this.genOrderReceivedOccurrences(currentContract, nextTaskStartDate);
    }
  }

  /**
   * Generate occurrences for the order received event of a specified contract.
   * 
   * @param contract          Target contract.
   * @param nextTaskStartDate Optional parameter that indicates the next task
   *                          start date. If provided, this will overwrite the
   *                          contract start date.
   * @return boolean indicating if the occurrences have been generated
   *         successfully.
   */
  public boolean genOrderReceivedOccurrences(String contract, String nextTaskStartDate) {
    LOGGER.info("Generating all orders for the active contract {}...", contract);
    // Retrieve schedule information for the specific contract
    SparqlBinding bindings = this.lifecycleQueryService.getInstance(FileService.CONTRACT_SCHEDULE_QUERY_RESOURCE,
        contract, contract);
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
    // Add parameter template
    Map<String, Object> params = new HashMap<>();
    params.put(LifecycleResource.CONTRACT_KEY, contract);
    params.put(LifecycleResource.REMARKS_KEY, ORDER_INITIALISE_MESSAGE);
    this.lifecycleQueryService.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
    String orderPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString());
    // Instantiate each occurrence
    boolean hasError = false;
    while (!occurrences.isEmpty()) {
      // Retrieve and update the date of occurrence
      String occurrenceDate = occurrences.poll();
      // set new id each time
      params.remove(QueryResource.ID_KEY);
      LifecycleResource.genIdAndInstanceParameters(orderPrefix, LifecycleEventType.SERVICE_ORDER_RECEIVED, params);
      params.put(LifecycleResource.DATE_TIME_KEY, occurrenceDate);
      try {
        // Error logs for any specified occurrence
        this.addService.instantiate(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      } catch (IllegalStateException e) {
        LOGGER.error("Error encountered while creating order for {} on {}! Read error logs for more details",
            contract, occurrenceDate);
        hasError = true;
      }
    }
    return hasError;
  }

  /**
   * Continues the task on next working day by generating a new occurrence for the
   * order received and dispatch event of a specified contract.
   * 
   * @param taskId     The latest task ID.
   * @param contractId Target contract.
   */
  public ResponseEntity<StandardApiResponse<?>> continueTaskOnNextWorkingDay(String taskId, String contractId) {
    LOGGER.info("Generating the task for the next working day...");
    String nextWorkingDateTime = this.dateTimeService.getNextWorkingDate();
    Queue<SparqlBinding> nextEvents = this.lifecycleQueryService.getContractEventQuery(contractId,
        this.dateTimeService.getDateFromDateTime(nextWorkingDateTime),
        LifecycleEventType.SERVICE_ORDER_RECEIVED);
    if (!nextEvents.isEmpty()) {
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_DUPLICATE_TASK_KEY),
          HttpStatus.CONFLICT);
    }
    // First instantiate the order received occurrence
    Map<String, Object> params = new HashMap<>();
    // Contract ID is mandatory to help generate the other related parameters
    params.put(LifecycleResource.CONTRACT_KEY, contractId);
    params.put(LifecycleResource.REMARKS_KEY, ORDER_INITIALISE_MESSAGE);
    params.put(LifecycleResource.DATE_TIME_KEY, nextWorkingDateTime);
    this.lifecycleQueryService.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
    // Generate a new unique ID for the occurrence by retrieving the prefix from the
    // stage instance
    String defaultPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString());
    params.remove(QueryResource.ID_KEY);
    LifecycleResource.genIdAndInstanceParameters(defaultPrefix, LifecycleEventType.SERVICE_ORDER_RECEIVED, params);

    ResponseEntity<StandardApiResponse<?>> orderInstantiatedResponse = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
    if (orderInstantiatedResponse.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Retrieving the current dispatch details...");
      String prevDispatchId = this.getPreviousOccurrence(taskId, LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      ResponseEntity<StandardApiResponse<?>> response = this.getService.getInstance(prevDispatchId,
          LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      if (response.getStatusCode() == HttpStatus.OK) {
        Map<String, String> currentEntity = ((Map<String, Object>) response.getBody().data().items()
            .get(0)).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() instanceof SparqlResponseField
                    ? TypeCastUtils.castToObject(entry.getValue(),
                        SparqlResponseField.class).value()
                    : ""));
        params.putAll(currentEntity);
        params.put(LifecycleResource.REMARKS_KEY, ORDER_DISPATCH_MESSAGE);
        params.remove(QueryResource.ID_KEY);
        LifecycleResource.genIdAndInstanceParameters(defaultPrefix, LifecycleEventType.SERVICE_ORDER_DISPATCHED,
            params);
        params.put(LifecycleResource.ORDER_KEY, orderInstantiatedResponse.getBody().data().id());
        return this.addService.instantiate(LifecycleEventType.SERVICE_ORDER_DISPATCHED.getId(), params);
      }
    }
    throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY));
  }

  /**
   * Generate an occurrence for the order dispatch or delivery event of a
   * specified contract.
   * 
   * @param params    Required parameters with configurable parameters to
   *                  instantiate the occurrence.
   * @param eventType Target event type.
   */
  public ResponseEntity<StandardApiResponse<?>> genDispatchOrDeliveryOccurrence(Map<String, Object> params,
      LifecycleEventType eventType) {
    String remarksMsg;
    String successMsgId;
    LifecycleEventType succeedsEventType;
    params.put(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    switch (eventType) {
      case LifecycleEventType.SERVICE_EXECUTION:
        remarksMsg = ORDER_COMPLETE_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_COMPLETE_KEY;
        succeedsEventType = LifecycleEventType.SERVICE_ORDER_DISPATCHED;
        break;
      case LifecycleEventType.SERVICE_ORDER_DISPATCHED:
        remarksMsg = ORDER_DISPATCH_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_ASSIGN_KEY;
        succeedsEventType = LifecycleEventType.SERVICE_ORDER_RECEIVED;
        break;
      default:
        throw new IllegalStateException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    }

    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    String stage = this.lifecycleQueryService.getInstance(FileService.CONTRACT_STAGE_QUERY_RESOURCE, contractId,
        eventType.getStage()).getFieldValue(QueryResource.IRI_KEY);
    params.put(LifecycleResource.STAGE_KEY, stage);
    params.put(LifecycleResource.REMARKS_KEY, remarksMsg);
    String occurrenceId = params.get(QueryResource.ID_KEY).toString();
    try {
      // Get previous occurrence ID for the same event type if they exist
      occurrenceId = this.getPreviousOccurrence(QueryResource.ID_KEY, eventType, params);
      params.put(QueryResource.ID_KEY, occurrenceId);
    } catch (NullPointerException e) {
      // Fail silently as there is no previous occurrence and we should create a new
      // occurrence accordingly
    }
    params.put(LifecycleResource.ORDER_KEY,
        this.getPreviousOccurrence(QueryResource.IRI_KEY, succeedsEventType, params));

    return this.updateService.update(occurrenceId, eventType.getId(), successMsgId, params);
  }

  /**
   * Retrieves the previous occurrence instance based on its event type and latest
   * event id.
   * 
   * @param latestEventId The identifier of the latest event in the succeeds
   *                      chain.
   * @param eventType     Target event type to query for.
   */
  public String getPreviousOccurrence(String latestEventId, LifecycleEventType eventType) {
    return this.lifecycleQueryService
        .getInstance(FileService.CONTRACT_PREV_EVENT_QUERY_RESOURCE, latestEventId, eventType.getEvent())
        .getFieldValue(QueryResource.ID_KEY);
  }

  /**
   * Retrieves the previous occurrence instance based on its event type.
   * 
   * @param field     Retrieves either the IRI or id field from the query.
   * @param eventType Target event type to query for.
   * @param params    Mappings containing the contract and date value for the
   *                  query.
   */
  private String getPreviousOccurrence(String field, LifecycleEventType eventType, Map<String, Object> params) {
    return this.lifecycleQueryService
        .getContractEventQuery(params.get(LifecycleResource.CONTRACT_KEY).toString(),
            params.get(LifecycleResource.DATE_KEY).toString(), eventType)
        .poll().getFieldValue(field);
  }
}