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
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param isClosed       Indicates whether to retrieve closed tasks.
   * @param filters        Mappings between filter fields and their values.
   */
  public ResponseEntity<StandardApiResponse<?>> getOccurrenceCount(String startTimestamp,
      String endTimestamp, boolean isClosed, Map<String, String> filters) {
    String[] targetStartEndDates = this.dateTimeService.getStartEndDate(startTimestamp, endTimestamp, isClosed);
    Map<String, String> additionalFilters = this.lifecycleQueryFactory.getServiceTasksFilter(targetStartEndDates[0],
        targetStartEndDates[1], isClosed);
    Map<String, String> extendedFilters = this.lifecycleQueryFactory.insertExtendedTaskFilters(additionalFilters);
    extendedFilters.put(LifecycleResource.DATE_KEY,
        "BIND(STR(xsd:date(?date)) as ?" + LifecycleResource.NEW_DATE_KEY + ")");
    extendedFilters.put(QueryResource.SCHEDULE_RECURRENCE_VAR.getVarName(),
        "OPTIONAL{?iri ^<https://www.omg.org/spec/Commons/Collections/comprises>/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?recurrences.}"
            + "BIND(IF(BOUND(?recurrences),?recurrences,\"\") AS "
            + QueryResource.SCHEDULE_RECURRENCE_VAR.getQueryString() + ")");
    return this.responseEntityBuilder.success(null,
        String.valueOf(
            this.getService.getCount(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, extendedFilters, filters,
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
    String[] targetStartEndDates = this.dateTimeService.getStartEndDate(startTimestamp, endTimestamp, isClosed);
    Map<String, String> queryMappings = this.lifecycleQueryFactory.getServiceTasksQuery(null,
        targetStartEndDates[0], targetStartEndDates[1], isClosed);
    Map<String, String> extendedMappings = this.lifecycleQueryFactory.insertExtendedTaskFilters(queryMappings);
    String originalField = LocalisationResource.parseTranslationToOriginal(field, false);
    Map<String, Set<String>> targetFields = new HashMap<>();
    targetFields.put(field, new HashSet<>());
    // Dispatch parameters will always be present
    String additionalStatements = QueryResource
        .optional(this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_ORDER_DISPATCHED,
            new HashSet<>(), targetFields));
    if (isClosed) {
      // Only closed tasks will have the associated closed task parameters
      // that should be wrapped in optional
      additionalStatements += QueryResource
          .optional(this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_EXECUTION,
              new HashSet<>(), targetFields));
      additionalStatements += QueryResource
          .optional(this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_CANCELLATION,
              new HashSet<>(), targetFields));
      additionalStatements += QueryResource
          .optional(this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_INCIDENT_REPORT,
              new HashSet<>(), targetFields));
    }
    // Update lifecycle query statements accordingly
    extendedMappings.put(LifecycleResource.LIFECYCLE_RESOURCE,
        extendedMappings.get(LifecycleResource.LIFECYCLE_RESOURCE) + additionalStatements);
    extendedMappings.put(LifecycleResource.DATE_KEY,
        "BIND(STR(?date) as ?" + LifecycleResource.NEW_DATE_KEY + ")");
    List<String> options = this.getService.getAllFilterOptions(resourceID, originalField, extendedMappings, search,
        filters, false);
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
    Map<String, String> lifecycleStatements = this.lifecycleQueryFactory.getServiceTasksQuery(contract, null, null,
        null);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, lifecycleStatements, null,
        pagination);
    LOGGER.info("Successfuly retrieved all associated services!");
    return this.responseEntityBuilder.success(null, occurrences);
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
    String[] targetStartEndDates = this.dateTimeService.getStartEndDate(startTimestamp, endTimestamp, isClosed);
    Map<String, String> lifecycleStatements = this.lifecycleQueryFactory.getServiceTasksQuery(null,
        targetStartEndDates[0], targetStartEndDates[1], isClosed);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, lifecycleStatements,
        isClosed, pagination);
    LOGGER.info("Successfuly retrieved tasks!");
    return this.responseEntityBuilder.success(null, occurrences);
  }

  /**
   * Executes the occurrence query and group them by the specified group variable.
   * 
   * @param entityType    Target resource ID.
   * @param queryMappings Additional query statements to be added if any.
   * @param isClosed      Indicates whether to retrieve closed tasks.
   * @param pagination    Pagination state to filter results.
   */
  private List<Map<String, Object>> executeOccurrenceQuery(String entityType, Map<String, String> queryMappings,
      Boolean isClosed, PaginationState pagination) {
    // Retrieve query statements that matches any sort or filter criteria
    String addFilterQueries = this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_ORDER_DISPATCHED,
        pagination.sortedFields(), pagination.filters());
    // Non-closed tasks should not have the closed related statements
    if (isClosed) {
      addFilterQueries += this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_EXECUTION,
          pagination.sortedFields(), pagination.filters());
      addFilterQueries += this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_CANCELLATION,
          pagination.sortedFields(), pagination.filters());
      addFilterQueries += this.genEventOccurrenceSortQueryStatements(LifecycleEventType.SERVICE_INCIDENT_REPORT,
          pagination.sortedFields(), pagination.filters());
    }
    // Update lifecycle statements accordingly
    Map<String, String> extendedQueryMappings = this.lifecycleQueryFactory
        .insertExtendedTaskFilters(queryMappings);
    queryMappings.put(LifecycleResource.LIFECYCLE_RESOURCE,
        queryMappings.get(LifecycleResource.LIFECYCLE_RESOURCE) + addFilterQueries);
    extendedQueryMappings.put(LifecycleResource.LIFECYCLE_RESOURCE,
        queryMappings.get(LifecycleResource.LIFECYCLE_RESOURCE));
    extendedQueryMappings.put(LifecycleResource.DATE_KEY,
        "BIND(STR(?date) as ?" + LifecycleResource.NEW_DATE_KEY + ")");
    Queue<List<String>> ids = this.getService.getAllIds(entityType, extendedQueryMappings, pagination);
    Map<Variable, List<Integer>> varSequences = new HashMap<>(this.taskVarSequence);
    String addQuery = queryMappings.values().stream().collect(Collectors.joining("\n"));
    addQuery += this.parseEventOccurrenceQuery(-4, LifecycleEventType.SERVICE_ORDER_DISPATCHED, varSequences);
    if (isClosed) {
      addQuery += this.parseEventOccurrenceQuery(-3, LifecycleEventType.SERVICE_EXECUTION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(-2, LifecycleEventType.SERVICE_CANCELLATION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(-1, LifecycleEventType.SERVICE_INCIDENT_REPORT, varSequences);
    }
    Queue<SparqlBinding> results = this.getService.getInstances(entityType, true, ids, addQuery, varSequences);
    return results.stream()
        .map(binding -> {
          return (Map<String, Object>) binding.get().entrySet().stream()
              .filter(entry -> !entry.getKey()
                  .equals(QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY).getVarName()))
              .map(entry -> {
                if (entry.getKey().equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
                  SparqlResponseField recurrence = TypeCastUtils.castToObject(entry.getValue(),
                      SparqlResponseField.class);
                  return new AbstractMap.SimpleEntry<>(
                      LocalisationTranslator.getMessage(LocalisationResource.VAR_SCHEDULE_TYPE_KEY),
                      new SparqlResponseField(recurrence.type(),
                          LocalisationTranslator.getScheduleTypeFromRecurrence(recurrence.value()),
                          recurrence.dataType(), recurrence.lang()));
                }
                if (entry.getKey().equals(LifecycleResource.EVENT_KEY)) {
                  SparqlResponseField eventField = TypeCastUtils.castToObject(entry.getValue(),
                      SparqlResponseField.class);
                  // For any pending completion events, simply reset it to the previous event
                  // status as they are incomplete or in a saved state, and should still be
                  // outstanding
                  String eventType = eventField.value();
                  return new AbstractMap.SimpleEntry<>(
                      LocalisationTranslator.getMessage(LocalisationResource.VAR_STATUS_KEY),
                      // Add a new response field
                      new SparqlResponseField(eventField.type(),
                          LocalisationTranslator.getEvent(eventType),
                          eventField.dataType(), eventField.lang()));
                }
                return entry;
              })
              .collect(Collectors.toMap(
                  Map.Entry::getKey,
                  (entry -> TypeCastUtils.castToObject(entry.getValue(), Object.class)),
                  (oldVal, newVal) -> oldVal,
                  LinkedHashMap::new));
        })
        .toList();
  }

  /**
   * Generates the query statements to sort by event occurrences.
   * 
   * @param lifecycleEvent Target event type.
   * @param sortedFields   Set of fields for sorting that should be included.
   * @param filters        Filters with name and values.
   */
  private String genEventOccurrenceSortQueryStatements(LifecycleEventType lifecycleEvent, Set<String> sortedFields,
      Map<String, Set<String>> filters) {
    String sortQueryStatements = this.getService.getQueryStatementsForTargetFields(lifecycleEvent.getShaclReplacement(),
        sortedFields, filters);
    if (sortQueryStatements.isEmpty()) {
      return sortQueryStatements;
    }
    return LifecycleResource.parseOccurrenceSortQueryStatements(sortQueryStatements, lifecycleEvent);
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
   * Generate occurrences for the order received event of a specified contract.
   * 
   * @param contract Target contract.
   * @return boolean indicating if the occurrences have been generated
   *         successfully.
   */
  public boolean genOrderReceivedOccurrences(String contract) {
    LOGGER.info("Generating all orders for the active contract {}...", contract);
    // Retrieve schedule information for the specific contract
    SparqlBinding bindings = this.lifecycleQueryService.getInstance(FileService.CONTRACT_SCHEDULE_QUERY_RESOURCE,
        contract, contract);
    // Extract specific schedule info
    String startDate = bindings
        .getFieldValue(QueryResource.SCHEDULE_START_DATE_VAR.getVarName());
    String endDate = bindings
        .getFieldValue(QueryResource.SCHEDULE_END_DATE_VAR.getVarName());
    String recurrence = bindings
        .getFieldValue(LifecycleResource.SCHEDULE_RECURRENCE_PLACEHOLDER_KEY);
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
    String nextEventQuery = this.lifecycleQueryService.getContractEventQuery(contractId,
        this.dateTimeService.getDateFromDateTime(nextWorkingDateTime),
        LifecycleEventType.SERVICE_ORDER_RECEIVED);
    Queue<SparqlBinding> nextEvents = this.getService.getInstances(nextEventQuery);
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
    String eventQuery = this.lifecycleQueryService.getContractEventQuery(
        params.get(LifecycleResource.CONTRACT_KEY).toString(),
        params.get(LifecycleResource.DATE_KEY).toString(),
        eventType);
    return this.getService.getInstance(eventQuery).getFieldValue(field);
  }
}