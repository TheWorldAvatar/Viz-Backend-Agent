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
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class LifecycleService {
  private final AddService addService;
  private final DateTimeService dateTimeService;
  private final GetService getService;
  private final UpdateService updateService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private final LifecycleQueryFactory lifecycleQueryFactory;
  private final Map<Variable, List<Integer>> lifecycleVarSequence = new HashMap<>();
  private final Map<Variable, List<Integer>> taskVarSequence = new HashMap<>();

  private static final String ORDER_INITIALISE_MESSAGE = "Order received and is being processed.";
  private static final String ORDER_DISPATCH_MESSAGE = "Order has been assigned and is awaiting execution.";
  private static final String ORDER_COMPLETE_MESSAGE = "Order has been completed successfully.";
  private static final String SERVICE_DISCHARGE_MESSAGE = "Service has been completed successfully.";
  private static final Logger LOGGER = LogManager.getLogger(LifecycleService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   */
  public LifecycleService(AddService addService, DateTimeService dateTimeService, GetService getService,
      UpdateService updateService, ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.dateTimeService = dateTimeService;
    this.getService = getService;
    this.updateService = updateService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();

    this.lifecycleVarSequence.put(QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY), List.of(-3, 2));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_START_DATE_VAR, List.of(2, 0));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_END_DATE_VAR, List.of(2, 1));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_START_TIME_VAR, List.of(2, 2));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_END_TIME_VAR, List.of(2, 3));
    this.lifecycleVarSequence.put(QueryResource.genVariable(LifecycleResource.SCHEDULE_TYPE_KEY), List.of(2, 4));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_RECURRENCE_VAR, List.of(2, 5));

    Integer MIN_INDEX = -5;

    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.DATE_KEY), List.of(MIN_INDEX, 1));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY), List.of(MIN_INDEX, 2));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_KEY), List.of(MIN_INDEX, 3));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_ID_KEY), List.of(1000, 999));
    this.taskVarSequence.put(QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY), List.of(1000, 1000));
    this.taskVarSequence.put(QueryResource.SCHEDULE_RECURRENCE_VAR, List.of(1000, 1001));
  }

  /**
   * Add the required stage instance into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addStageInstanceToParams(Map<String, Object> params, LifecycleEventType eventType) {
    String contractId = params.get(QueryResource.ID_KEY).toString();
    LOGGER.debug("Adding stage parameters for contract...");
    String query = this.lifecycleQueryFactory.getStageQuery(contractId, eventType);
    String stage = this.getService.getInstance(query).getFieldValue(QueryResource.IRI_KEY);
    params.put(LifecycleResource.STAGE_KEY, stage);
  }

  /**
   * Populate the remaining occurrence parameters into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addOccurrenceParams(Map<String, Object> params, LifecycleEventType eventType) {
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    LOGGER.debug("Adding occurrence parameters for {}...", contractId);
    String query = this.lifecycleQueryFactory.getStageQuery(contractId, eventType);
    String stage = this.getService.getInstance(query).getFieldValue(QueryResource.IRI_KEY);
    LifecycleResource.genIdAndInstanceParameters(StringResource.getPrefix(stage), eventType, params);
    params.put(LifecycleResource.STAGE_KEY, stage);
    params.put(LifecycleResource.EVENT_KEY, eventType.getEvent());
    params.putIfAbsent(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    // Update the order enum with the specific event instance if it exist
    params.computeIfPresent(LifecycleResource.ORDER_KEY, (key, value) -> {
      String orderEnum = value.toString();
      return this.getPreviousOccurrence(QueryResource.IRI_KEY,
          LifecycleResource.getEventClassFromOrderEnum(orderEnum), params);
    });
  }

  /**
   * Retrieve the status of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<StandardApiResponse<?>> getContractStatus(String contract) {
    LOGGER.debug("Retrieving the status of the contract...");
    String query = this.lifecycleQueryFactory.getServiceStatusQuery(contract);
    SparqlBinding result = this.getService.getInstance(query);
    LOGGER.info("Successfuly retrieved contract status!");
    return this.responseEntityBuilder.success(result.getFieldValue(QueryResource.IRI_KEY),
        result.getFieldValue(LifecycleResource.STATUS_KEY));
  }

  /**
   * Retrieve the schedule details of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<Map<String, Object>> getSchedule(String contract) {
    LOGGER.debug("Retrieving the schedule details of the contract...");
    String query = this.lifecycleQueryFactory.getServiceScheduleQuery(contract);
    SparqlBinding result = this.getService.getInstance(query);
    LOGGER.info("Successfuly retrieved schedule!");
    return new ResponseEntity<>(result.get(), HttpStatus.OK);
  }

  /**
   * Retrieve the number of contract instances in the specific stage.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param eventType  The target event type to retrieve.
   * @param filters    Mappings between filter fields and their values.
   */
  public ResponseEntity<StandardApiResponse<?>> getContractCount(String resourceID, LifecycleEventType eventType,
      Map<String, String> filters) {
    Map<String, String> lifecycleStatements = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    // Use extended lifecycle statements for applying filters
    Map<String, String> extendedLifecycleStatements = this.lifecycleQueryFactory
        .insertExtendedScheduleFilters(lifecycleStatements);
    return this.responseEntityBuilder.success(null,
        String.valueOf(this.getService.getCount(resourceID, extendedLifecycleStatements, filters, true)));
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
        "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> ?date."
            + "BIND(STR(xsd:date(?date)) as ?" + LifecycleResource.NEW_DATE_KEY + ")");
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
   * Retrieve filter options at the contract level.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param field      The field of filtering.
   * @param search     String subset to narrow filter scope.
   * @param eventType  The target event type to retrieve.
   * @param filters    Optional additional filters.
   */
  public List<String> getFilterOptions(String resourceID, String field, String search, LifecycleEventType eventType,
      Map<String, String> filters) {
    Map<String, String> lifecycleStatements = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    // Use extended lifecycle statements for applying filters
    Map<String, String> extendedLifecycleStatements = this.lifecycleQueryFactory
        .insertExtendedScheduleFilters(lifecycleStatements);
    String originalField = LocalisationResource.parseTranslationToOriginal(field, true);
    List<String> options = this.getService.getAllFilterOptions(resourceID, originalField, extendedLifecycleStatements,
        search, filters, true);
    if (originalField.equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
      return options.stream().map(option -> LocalisationTranslator.getScheduleTypeFromRecurrence(option)).toList();
    }
    return options;

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
    List<String> options = this.getService.getAllFilterOptions(resourceID, originalField, extendedMappings, search,
        filters, false);
    if (originalField.equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
      return options.stream().map(option -> LocalisationTranslator.getScheduleTypeFromRecurrence(option)).toList();
    }
    return options;
  }

  /**
   * Retrieve all the contract instances and their information based on the
   * resource ID.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param eventType  The target event type to retrieve.
   * @param pagination Pagination state to filter results.
   */
  public ResponseEntity<StandardApiResponse<?>> getContracts(String resourceID, boolean requireLabel,
      LifecycleEventType eventType, PaginationState pagination) {
    LOGGER.debug("Retrieving all contracts...");
    Map<String, String> lifecycleStatements = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    Map<Variable, List<Integer>> contractVariables = new HashMap<>(this.lifecycleVarSequence);
    if (eventType.equals(LifecycleEventType.APPROVED)) {
      contractVariables.put(
          QueryResource.genVariable(LocalisationTranslator.getMessage(LocalisationResource.VAR_STATUS_KEY)),
          List.of(1, 1));
    }
    // Use extended lifecycle statements for applying filters when getting IDs only
    Map<String, String> extendedLifecycleStatements = this.lifecycleQueryFactory
        .insertExtendedScheduleFilters(lifecycleStatements);
    Queue<String> ids = this.getService.getAllIds(resourceID, extendedLifecycleStatements, pagination);
    Queue<SparqlBinding> instances = this.getService.getInstances(resourceID, requireLabel, ids,
        lifecycleStatements.values().stream().collect(Collectors.joining("\n")),
        contractVariables);
    return this.responseEntityBuilder.success(null, instances.stream()
        .map(binding -> {
          return (Map<String, Object>) binding.get().entrySet().stream()
              .map(entry -> {
                // Replace recurrence with schedule type
                if (entry.getKey().equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
                  SparqlResponseField recurrence = TypeCastUtils.castToObject(entry.getValue(),
                      SparqlResponseField.class);
                  return new AbstractMap.SimpleEntry<>(
                      LocalisationTranslator.getMessage(LocalisationResource.VAR_SCHEDULE_TYPE_KEY),
                      new SparqlResponseField(recurrence.type(),
                          LocalisationTranslator.getScheduleTypeFromRecurrence(recurrence.value()),
                          recurrence.dataType(), recurrence.lang()));
                }
                return entry;
              })
              .collect(Collectors.toMap(
                  Map.Entry::getKey,
                  (entry -> TypeCastUtils.castToObject(entry.getValue(), Object.class)),
                  (oldVal, newVal) -> oldVal,
                  LinkedHashMap::new));
        })
        .toList());
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
    Queue<String> ids = this.getService.getAllIds(entityType, extendedQueryMappings, pagination);
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
          String eventStatus = binding.getFieldValue(LifecycleResource.EVENT_STATUS_KEY);
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
                  if (eventType.equals(LifecycleResource.EVENT_DELIVERY)
                      && LifecycleResource.EVENT_PENDING_STATUS.equals(eventStatus)) {
                    eventType = LifecycleResource.EVENT_DISPATCH;
                  }
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
   * Generate occurrences for the order received event of a specified contract.
   * 
   * @param contract Target contract.
   * @return boolean indicating if the occurrences have been generated
   *         successfully.
   */
  public boolean genOrderReceivedOccurrences(String contract) {
    LOGGER.info("Generating all orders for the active contract {}...", contract);
    // Retrieve schedule information for the specific contract
    String query = this.lifecycleQueryFactory.getServiceScheduleQuery(contract);
    SparqlBinding bindings = this.getService.getInstance(query);
    // Extract specific schedule info
    String startDate = bindings
        .getFieldValue(QueryResource.SCHEDULE_START_DATE_VAR.getVarName());
    String endDate = bindings
        .getFieldValue(QueryResource.SCHEDULE_END_DATE_VAR.getVarName());
    String recurrence = bindings
        .getFieldValue(QueryResource.SCHEDULE_RECURRENCE_VAR.getVarName());
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
    this.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
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
    String nextEventQuery = this.lifecycleQueryFactory.getContractEventQuery(contractId,
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
    this.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
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
   * Discharges any active contracts that should have expired today.
   */
  public void dischargeExpiredContracts() {
    LOGGER.info("Retrieving all active contracts that are expiring...");
    String query = this.lifecycleQueryFactory.getExpiredActiveContractQuery();
    Queue<SparqlBinding> results = this.getService.getInstances(query);
    Map<String, Object> paramTemplate = new HashMap<>();
    paramTemplate.put(LifecycleResource.REMARKS_KEY, SERVICE_DISCHARGE_MESSAGE);
    LOGGER.debug("Instanting completed occurrences for these contracts...");
    while (!results.isEmpty()) {
      Map<String, Object> params = new HashMap<>(paramTemplate);
      String currentContract = results.poll().getFieldValue(QueryResource.IRI_KEY);
      params.put(LifecycleResource.CONTRACT_KEY, currentContract);
      this.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_COMPLETION);
      ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      // Error logs for any specified occurrence
      if (response.getStatusCode() != HttpStatus.OK) {
        LOGGER.error("Error encountered while discharging the contract for {}! Read error logs for more details.",
            currentContract);
      }
    }
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
    String query = this.lifecycleQueryFactory.getStageQuery(contractId, eventType);
    String stage = this.getService.getInstance(query).getFieldValue(QueryResource.IRI_KEY);
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
   * Updates the contract status to Pending from its current status.
   * 
   * @param id The contract identifier.
   */
  public ResponseEntity<StandardApiResponse<?>> updateContractStatus(String id) {
    String updateQuery = this.lifecycleQueryFactory.genContractEventStatusUpdateQuery(id);
    return this.updateService.update(updateQuery);
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
    String query = this.lifecycleQueryFactory.getContractEventQuery(latestEventId, eventType);
    return this.getService.getInstance(query).getFieldValue(QueryResource.ID_KEY);
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
    String eventQuery = this.lifecycleQueryFactory.getContractEventQuery(
        params.get(LifecycleResource.CONTRACT_KEY).toString(),
        params.get(LifecycleResource.DATE_KEY).toString(),
        eventType);
    return this.getService.getInstance(eventQuery).getFieldValue(field);
  }
}