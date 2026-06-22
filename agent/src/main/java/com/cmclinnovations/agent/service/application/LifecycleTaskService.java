package com.cmclinnovations.agent.service.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.LinkedList;
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
import com.cmclinnovations.agent.component.ParallelTaskExecutor;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.ColumnMetaPayload;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.model.util.DataManifest;
import com.cmclinnovations.agent.model.util.LifecycleTask;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
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
  private final List<ColumnMetaPayload> taskColumnMeta = new ArrayList<>();
  private final List<ColumnMetaPayload> taskEntityColumnMeta = new ArrayList<>();

  private static final String ORDER_INITIALISE_MESSAGE = "Order received and is being processed.";
  private static final String ORDER_DISPATCH_MESSAGE = "Order has been assigned and is awaiting execution.";
  private static final String ORDER_COMPLETE_MESSAGE = "Order has been completed successfully.";
  private static final String ORDER_ACCRUAL_MESSAGE = "Billables have been accrued successfully.";
  private static final int NUM_DAY_ORDER_GEN = 30;
  static final Logger LOGGER = LogManager.getLogger(LifecycleTaskService.class);

  private static final boolean IS_CONTRACT = false;

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

    this.taskColumnMeta.add(new ColumnMetaPayload(LifecycleResource.DATE_KEY,
        QueryResource.LITERAL_TYPE, ShaclResource.XSD_DATE));
    this.taskColumnMeta
        .add(new ColumnMetaPayload(QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY).getVarName(),
            QueryResource.LITERAL_TYPE, ShaclResource.XSD_DATE_TIME));
    this.taskColumnMeta
        .add(new ColumnMetaPayload(LifecycleResource.STATUS_KEY, QueryResource.LITERAL_TYPE, ShaclResource.XSD_STRING));
    this.taskColumnMeta
        .add(QueryResource.EVENT_ID_COL);
    this.taskEntityColumnMeta.add(new ColumnMetaPayload(LifecycleResource.SCHEDULE_TYPE_KEY, QueryResource.LITERAL_TYPE,
        ShaclResource.XSD_STRING));
  }

  /**
   * Retrieve the number of task in the specific stage and status.
   * 
   * @param resourceID     The target resource identifier for the instance class.
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param eventType      The current event type, affecting the query for
   *                       execution. Closed should be completed.
   * @param filters        Mappings between filter fields and their values.
   */
  public Integer getOccurrenceCount(String resourceID, String startTimestamp,
      String endTimestamp, LifecycleEventType eventType, Map<String, String> filters) {
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, IS_CONTRACT);
    String[] queryStatement = this.genLifecycleStatements(startTimestamp, endTimestamp, new HashSet<>(), parsedFilters,
        "", eventType, false);
    return this.getService.getCount(resourceID, queryStatement[0], LifecycleResource.TASK_ID_SORT_BY_PARAMS, filters,
        IS_CONTRACT);
  }

  /**
   * Retrieve filter options at the task level.
   * 
   * @param resourceID     The target resource identifier for the instance class.
   * @param field          The field of filtering.
   * @param search         String subset to narrow filter scope.
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param eventType      The current event type, affecting the query for
   *                       execution. Closed should be completed.
   * @param filters        Optional additional filters.
   */
  public List<String> getFilterOptions(String resourceID, String field, String search, String startTimestamp,
      String endTimestamp, LifecycleEventType eventType, Map<String, String> filters) {
    String originalField = LifecycleResource.revertLifecycleSpecialFields(field, IS_CONTRACT);
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, IS_CONTRACT);
    parsedFilters.remove(originalField);
    String[] queryStatement = this.genLifecycleStatements(startTimestamp, endTimestamp, new HashSet<>(), parsedFilters,
        originalField, eventType, false);
    List<String> options = this.getService.getAllFilterOptionsAsStrings(resourceID, originalField, queryStatement[0],
        search,
        parsedFilters);
    if (originalField.equals(LifecycleResource.SCHEDULE_RECURRENCE_KEY)) {
      return options.stream().map(LocalisationTranslator::getScheduleTypeFromRecurrence).toList();
    } else if (originalField.equals(LifecycleResource.EVENT_KEY)) {
      return options.stream().map(LocalisationTranslator::getEvent).toList();
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
   * @param eventType      The current event type, affecting the query for
   *                       execution. Closed should be completed.
   * @param pagination     Pagination state to filter results.
   * @param filters        Filters provided in the request parameters.
   */
  public ResponseEntity<StandardApiResponse<?>> getOccurrences(String startTimestamp, String endTimestamp,
      String entityType, LifecycleEventType eventType, PaginationState pagination, Map<String, String> filters) {
    var results = ParallelTaskExecutor.execParallelQueryTasks(
        () -> this.queryOccurrences(startTimestamp, endTimestamp, entityType, eventType, pagination),
        () -> this.getOccurrenceCount(entityType, startTimestamp, endTimestamp, eventType, filters),
        () -> this.getOccurrenceCount(entityType, startTimestamp, endTimestamp, eventType, new HashMap<>()));

    return this.responseEntityBuilder.success(
        null,
        results.filteredCount(),
        results.totalCount(),
        results.data().columns(),
        results.data().data());
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date(s) by executing the constructed SPARQL query.
   * 
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param entityType     Target resource ID.
   * @param eventType      The current event type, affecting the query for
   *                       execution. Closed should be completed.
   * @param pagination     Pagination state to filter results.
   */
  private DataManifest<List<Map<String, Object>>> queryOccurrences(String startTimestamp, String endTimestamp,
      String entityType, LifecycleEventType eventType, PaginationState pagination) {
    String[] lifecycleStatements = this.genLifecycleStatements(startTimestamp, endTimestamp,
        pagination.getSortedFields(), pagination.getFilters(), "", eventType, true);
    return this.executeOccurrenceQuery(entityType, lifecycleStatements, eventType, pagination);
  }

  /**
   * Retrieves a list of unique occurrence dates associated with a specific
   * contract within a given time range.
   *
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param entityType     Target resource ID.
   * @param contractId     The ID of the contract to filter occurrences by.
   */
  public List<LifecycleTask> getOccurrencesByContract(String startTimestamp, String endTimestamp,
      String entityType, String contractId) {
    Map<String, String> filter = new HashMap<>();
    filter.put("id", contractId);
    PaginationState pagination = new PaginationState(0, null,
        StringResource.DEFAULT_SORT_BY + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, filter);
    DataManifest<List<Map<String, Object>>> occurrenceManifest = this.queryOccurrences(startTimestamp, endTimestamp,
        entityType, LifecycleEventType.SERVICE_ORDER_RECEIVED, pagination);
    String eventIdVar = QueryResource.EVENT_ID_VAR.getVarName();
    return occurrenceManifest.data().stream()
        .filter(occurrenceMap -> occurrenceMap.containsKey(LifecycleResource.DATE_KEY)
            && occurrenceMap.containsKey(eventIdVar))
        .map(occurrenceMap -> {
          String date = TypeCastUtils
              .castToObject(occurrenceMap.get(LifecycleResource.DATE_KEY), SparqlResponseField.class).value();
          String eventId = TypeCastUtils.castToObject(occurrenceMap.get(eventIdVar), SparqlResponseField.class).value();
          eventId = StringResource.getLocalName(eventId);
          return new LifecycleTask(eventId, date);
        })
        .collect(Collectors.toList());
  }

  /**
   * Perform action of multiple services belong to the same contract. Services are
   * identified by their dates.
   */
  public ResponseEntity<StandardApiResponse<?>> updateTaskOfTerminatedContract(Map<String, Object> params,
      List<LifecycleTask> tasks, String type) {
    ResponseEntity<StandardApiResponse<?>> lastSuccessfulResponse = null;
    for (LifecycleTask task : tasks) {
      Map<String, Object> dateParams = new HashMap<>(params);
      dateParams.put(QueryResource.ID_KEY, task.id());
      dateParams.put(LifecycleResource.DATE_KEY, task.date());
      ResponseEntity<StandardApiResponse<?>> response = this.performSingleServiceAction(type, dateParams);
      if (!response.getStatusCode().equals(HttpStatus.OK)) {
        return response;
      }
      lastSuccessfulResponse = response;
    }
    return lastSuccessfulResponse;
  }

  /**
   * Performs a service action for a specific service action. Valid types include:
   * 1) report: Reports any unfulfilled service delivery
   * 2) cancel: Cancel any upcoming service
   * 3) exempt: Exempts any service from billing accrual
   */
  public ResponseEntity<StandardApiResponse<?>> performSingleServiceAction(String type, Map<String, Object> params) {
    LifecycleEventType eventType = LifecycleEventType.fromId(type.toLowerCase());
    String taskId = params.get(QueryResource.ID_KEY).toString();

    switch (eventType) {
      case LifecycleEventType.SERVICE_CANCELLATION:
        LOGGER.info("Received request to cancel the upcoming service...");
        // Service date selected for cancellation cannot be a past date
        if (this.dateTimeService.isFutureDate(this.dateTimeService.getCurrentDate(),
            params.get(LifecycleResource.DATE_KEY).toString())) {
          throw new IllegalArgumentException(
              LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_CANCEL_KEY));
        }

        String prevEventIri = this.getPreviousOccurrence(taskId, LifecycleEventType.SERVICE_ORDER_DISPATCHED,
            LifecycleEventType.SERVICE_ORDER_RECEIVED);
        params.put(LifecycleResource.ORDER_KEY, prevEventIri);

        return this.genOccurrence(LifecycleResource.CANCEL_RESOURCE, params, LifecycleEventType.SERVICE_CANCELLATION,
            TrackActionType.CANCELLATION, "Task has been successfully cancelled!",
            LocalisationResource.SUCCESS_CONTRACT_TASK_CANCEL_KEY);
      case LifecycleEventType.SERVICE_INCIDENT_REPORT:
        LOGGER.info("Received request to report an unfulfilled service...");
        // Service date selected for reporting an issue cannot be a future date
        if (this.dateTimeService.isFutureDate(params.get(LifecycleResource.DATE_KEY).toString())) {
          throw new IllegalArgumentException(
              LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_REPORT_KEY));
        }

        prevEventIri = this.getPreviousOccurrence(taskId, LifecycleEventType.SERVICE_ORDER_DISPATCHED,
            LifecycleEventType.SERVICE_ORDER_RECEIVED);
        params.put(LifecycleResource.ORDER_KEY, prevEventIri);

        return this.genOccurrence(LifecycleResource.REPORT_RESOURCE, params, LifecycleEventType.SERVICE_INCIDENT_REPORT,
            TrackActionType.ISSUE_REPORT, "Task has been successfully reported!",
            LocalisationResource.SUCCESS_CONTRACT_TASK_REPORT_KEY);
      case LifecycleEventType.SERVICE_EXEMPT:
        LOGGER.info("Received request to exempt the billable details for a service...");
        prevEventIri = this.getPreviousOccurrence(taskId, LifecycleEventType.SERVICE_EXECUTION,
            LifecycleEventType.SERVICE_CANCELLATION, LifecycleEventType.SERVICE_INCIDENT_REPORT);
        params.put(LifecycleResource.ORDER_KEY, prevEventIri);

        return this.genOccurrence(LifecycleResource.EXEMPT_RESOURCE, params, LifecycleEventType.SERVICE_EXEMPT,
            TrackActionType.EXEMPT, "Task has been exempted from billing successfully!",
            LocalisationResource.SUCCESS_CONTRACT_TASK_EXEMPT_KEY);
      default:
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_ROUTE_KEY, type));
    }
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
   * @param eventType        The current event type, affecting the query for
   *                         execution. Closed should be completed.
   * @param reqOriStatements Requires that the original statements are present.
   */
  private String[] genLifecycleStatements(String startTimestamp, String endTimestamp, Set<String> sortedFields,
      Map<String, Set<String>> filters, String field, LifecycleEventType eventType, boolean reqOriStatements) {
    String[] targetStartEndDates = this.dateTimeService.getStartEndDate(startTimestamp, endTimestamp,
        eventType.equals(LifecycleEventType.ACTIVE_SERVICE));
    Map<String, String> statementMappings = this.lifecycleQueryFactory.getServiceTasksQuery(null,
        targetStartEndDates[0], targetStartEndDates[1], eventType);
    // Get combined filter statements for events that matches any sort/filter
    // criteria
    String addFilterQueries = this.buildTaskEventFilterQueries(field, sortedFields, filters, eventType);

    Map<String, String> extendedMappings = this.lifecycleQueryFactory
        .insertExtendedLastModifiedFilters(statementMappings);
    // The add filters are only required for getting IDs and not the main query
    extendedMappings.put(LifecycleResource.LIFECYCLE_RESOURCE,
        statementMappings.get(LifecycleResource.LIFECYCLE_RESOURCE) + addFilterQueries);
    // Include an empty date statement to support filtering
    extendedMappings.put(LifecycleResource.DATE_KEY, "");
    String lifecycleStatements = this.lifecycleQueryService.genLifecycleStatements(extendedMappings, sortedFields,
        filters, field);
    if (reqOriStatements) {
      // Statements for entity properties
      String entityStatements = statementMappings.entrySet().stream()
          .filter(entry -> LifecycleResource.SCHEDULE_RECURRENCE_KEY.equals(entry.getKey()))
          .map(Map.Entry::getValue)
          .collect(Collectors.joining("\n"));

      // Statements for event properties
      String eventStatements = statementMappings.entrySet().stream()
          .filter(entry -> Set.of(LifecycleResource.EVENT_KEY, LifecycleResource.LAST_MODIFIED_KEY)
              .contains(entry.getKey()))
          .map(Map.Entry::getValue)
          .collect(Collectors.joining("\n"));

      return new String[] { lifecycleStatements, entityStatements, eventStatements };
    } else {
      return new String[] { lifecycleStatements };
    }
  }

  /**
   * Builds the filter queries for task events based on the provided parameters.
   *
   * @param field        The field for which to build filter queries.
   * @param sortedFields The set of fields for sorting.
   * @param filters      The map of service event filters.
   * @param eventType    The lifecycle event type.
   * @return The combined filter queries as a string.
   */
  private String buildTaskEventFilterQueries(String field, Set<String> sortedFields,
      Map<String, Set<String>> filters, LifecycleEventType rootEventType) {

    // Create a modifiable copy of the filters to adjust for the lookup field if
    // necessary
    Map<String, Set<String>> serviceEventFilters = new HashMap<>(filters);
    if (!field.isEmpty()) {
      // Override the field value for filter options, as it should ignore them
      serviceEventFilters.put(field, new HashSet<>());
    }

    // Gather all required statements for properties
    List<LifecycleEventType> lifecycleEventsChecklist = new ArrayList<>();
    lifecycleEventsChecklist.add(LifecycleEventType.SERVICE_ORDER_DISPATCHED);

    if (rootEventType.equals(LifecycleEventType.ACTIVE_SERVICE)
        || rootEventType.equals(LifecycleEventType.SERVICE_ACCRUAL)) {
      lifecycleEventsChecklist.add(LifecycleEventType.SERVICE_EXECUTION);
      lifecycleEventsChecklist.add(LifecycleEventType.SERVICE_CANCELLATION);
      lifecycleEventsChecklist.add(LifecycleEventType.SERVICE_INCIDENT_REPORT);
      lifecycleEventsChecklist.add(LifecycleEventType.SERVICE_EXEMPT);
    }

    Map<String, Map<LifecycleEventType, String>> propertyToEventMappings = this.genPropertyEventMappings(
        sortedFields, serviceEventFilters, lifecycleEventsChecklist);

    // Identify operation mode
    StringBuilder finalQueryAccumulator = new StringBuilder();
    boolean isLookupMode = field != null && !field.isEmpty() && serviceEventFilters.containsKey(field)
        && serviceEventFilters.get(field).isEmpty();

    // Separate background filters from the active lookup field
    Set<String> backgroundProperties = new LinkedHashSet<>(serviceEventFilters.keySet());
    if (isLookupMode) {
      backgroundProperties.remove(field);
    }

    // Explicitly clean out the sort key from background filtering structures
    backgroundProperties.remove(StringResource.SORT_KEY);

    // Process filtering constraints
    for (String propertyKey : backgroundProperties) {
      if (propertyKey.equals(StringResource.SORT_KEY)) {
        continue;
      }

      Map<LifecycleEventType, String> matchedEvents = propertyToEventMappings.get(propertyKey);
      if (matchedEvents == null || matchedEvents.isEmpty()) {
        continue;
      }

      String propertyValueVar = QueryResource.genVariable(propertyKey).getQueryString();
      List<String> unifiedEventBlocks = new ArrayList<>();
      List<String> eventVarsInvolved = new ArrayList<>();

      for (Map.Entry<LifecycleEventType, String> entry : matchedEvents.entrySet()) {
        LifecycleEventType currentEventType = entry.getKey();

        // Dynamically build a completely unique variable name for this specific
        // property block
        String uniqueVarStr = currentEventType.getId() + "_event_" + propertyKey;
        String uniqueEventVar = QueryResource.genVariable(uniqueVarStr).getQueryString();
        eventVarsInvolved.add(uniqueEventVar);

        String innerPathContent = prepareEventPropertyPath(entry.getValue(), currentEventType, uniqueEventVar);
        String fullEventPropertyPath = "{ " + innerPathContent + " }";
        unifiedEventBlocks.add(fullEventPropertyPath);

      }

      if (unifiedEventBlocks.isEmpty()) {
        continue;
      }

      String combinedGraphPath = QueryResource.union(unifiedEventBlocks.get(0),
          unifiedEventBlocks.stream().skip(1).toArray(String[]::new));

      Set<String> filterValues = serviceEventFilters.get(propertyKey);
      if (filterValues == null || filterValues.isEmpty()) {
        continue;
      }

      StringBuilder filterClauseBuilder = new StringBuilder();

      // Handle value injection for date type
      if (filterValues.contains(LifecycleResource.DATE_KEY)) {
        filterClauseBuilder.append("{ ").append(combinedGraphPath).append(" }\n");

        String dateFiltersStr = QueryResource.genDateFilterExpression(propertyKey, filterValues);
        filterClauseBuilder.append(dateFiltersStr).append("\n");
      } else if (filterValues.contains(QueryResource.TIME_TYPE)) {
        // Handle value injection for time type
        filterClauseBuilder.append("{ ").append(combinedGraphPath).append(" }\n");

        String timeFilterExpression = QueryResource.genTimeFilterExpression(propertyKey, filterValues);
        filterClauseBuilder.append(timeFilterExpression).append("\n");
      } else if (filterValues.contains(QueryResource.NUMERIC_TYPE)) {
        // Handle value injection for numerical type
        filterClauseBuilder.append("{ ").append(combinedGraphPath).append(" }\n");

        String numericalFilterExpression = QueryResource.genNumericalFilterExpression(propertyKey, filterValues);
        filterClauseBuilder.append(numericalFilterExpression).append("\n");
      } else {
        // handle string-base filtering
        String valuesListString = "";
        if (filterValues.size() > 1 || (filterValues.size() == 1 && !filterValues.contains(QueryResource.NULL_KEY))) {
          List<String> explicitValues = filterValues.stream()
              .filter(val -> !val.equals(QueryResource.NULL_KEY))
              .toList();
          valuesListString = String.join(", ", explicitValues);
        }

        // Blank-Only Selection
        if (filterValues.contains(QueryResource.NULL_KEY) && filterValues.size() == 1) {
          filterClauseBuilder.append(QueryResource.optional(combinedGraphPath)).append("\n");
          String blankExpr = "!BOUND(" + propertyValueVar + ")";
          filterClauseBuilder.append(QueryResource.filter(blankExpr)).append("\n");
        }
        // Mixed Selection - Explicit values OR Blanks
        // NOTE: Keeping this as is since genDefaultDatatypeFilters uses MINUS, which we
        // intentionally bypassed here
        else if (filterValues.contains(QueryResource.NULL_KEY)) {
          filterClauseBuilder.append(QueryResource.optional(combinedGraphPath)).append("\n");

          List<String> eventNotBoundConditions = eventVarsInvolved.stream()
              .map(eVar -> "!BOUND(" + eVar + ")")
              .toList();
          String eventsNotBoundCombined = String.join(" && ", eventNotBoundConditions);

          String mixedExpr = "(" + eventsNotBoundCombined + ") || " + propertyValueVar + " IN (" + valuesListString
              + ")";
          filterClauseBuilder.append(QueryResource.filter(mixedExpr)).append("\n");
        }
        // Strict selection with explicit values only
        else {
          filterClauseBuilder.append("{ ").append(combinedGraphPath).append(" }\n");
          String strictExpr = propertyValueVar + " IN (" + valuesListString + ")";
          filterClauseBuilder.append(QueryResource.filter(strictExpr)).append("\n");
        }
      }

      finalQueryAccumulator.append(filterClauseBuilder.toString());
    }

    // Process sorting constraints after all filtering constraints have been applied
    if (propertyToEventMappings.containsKey(StringResource.SORT_KEY)) {
      Map<LifecycleEventType, String> matchedEvents = propertyToEventMappings.get(StringResource.SORT_KEY);
      if (matchedEvents != null && !matchedEvents.isEmpty()) {

        for (Map.Entry<LifecycleEventType, String> entry : matchedEvents.entrySet()) {
          LifecycleEventType currentEventType = entry.getKey();
          String sortVarStr = currentEventType.getId() + "_event_sort";
          String uniqueSortEventVar = QueryResource.genVariable(sortVarStr).getQueryString();

          String innerOptionalContent = this.prepareEventPropertyPath(entry.getValue(), currentEventType,
              uniqueSortEventVar);
          finalQueryAccumulator.append(QueryResource.optional(innerOptionalContent)).append("\n");
        }
      }
    }

    // Handle active options lookup
    if (isLookupMode) {
      Map<LifecycleEventType, String> matchedEvents = propertyToEventMappings.get(field);
      if (matchedEvents != null && !matchedEvents.isEmpty()) {
        List<String> unifiedEventBlocks = new ArrayList<>();

        for (Map.Entry<LifecycleEventType, String> entry : matchedEvents.entrySet()) {
          LifecycleEventType currentEventType = entry.getKey();
          String lookupVarStr = currentEventType.getId() + "_event_lookup";
          String lookupEventVar = QueryResource.genVariable(lookupVarStr).getQueryString();

          String innerPathContent = this.prepareEventPropertyPath(entry.getValue(), currentEventType, lookupEventVar);
          unifiedEventBlocks.add("{ " + innerPathContent + " }");
        }

        if (!unifiedEventBlocks.isEmpty()) {
          String combinedGraphPath = QueryResource.union(unifiedEventBlocks.get(0),
              unifiedEventBlocks.stream().skip(1).toArray(String[]::new));

          finalQueryAccumulator.append(QueryResource.optional(combinedGraphPath)).append("\n");
        }
      }
    }

    return "\n" + finalQueryAccumulator.toString();
  }

  /*
   * Prepares the property path for a given event type and variable.
   *
   * @param pathStatement The SPARQL path statement.
   * 
   * @param eventType The lifecycle event type.
   * 
   * @param uniqueEventVar The unique variable for the event.
   * 
   * @return The prepared event property path.
   */
  private String prepareEventPropertyPath(String pathStatement, LifecycleEventType eventType, String uniqueEventVar) {
    // Generate the dynamic anchor statement bound to the explicit variable
    String anchor = LifecycleResource.genOccurrenceTargetQueryStatement(uniqueEventVar, eventType);

    // Swap the placeholder and clean up trailing punctuation
    String replacedPath = pathStatement.replace(QueryResource.IRI_VAR.getQueryString(), uniqueEventVar).trim();
    String finalizedPath = replacedPath.endsWith(".") ? replacedPath : replacedPath + " .";

    return anchor + " " + finalizedPath;
  }

  /**
   * Generates property-centric event mappings based on the provided parameters.
   *
   * @param sortedFields             The set of sorted fields.
   * @param serviceEventFilters      The map of service event filters.
   * @param lifecycleEventsChecklist The list of lifecycle events to check.
   * @return A map of property-centric event mappings.
   */
  private Map<String, Map<LifecycleEventType, String>> genPropertyEventMappings(Set<String> sortedFields,
      Map<String, Set<String>> serviceEventFilters,
      List<LifecycleEventType> lifecycleEventsChecklist) {

    // stores all matched statements for all event types
    Map<LifecycleEventType, Map<String, String>> globalLifecycleMappings = new HashMap<>();

    for (LifecycleEventType lifecycleEvent : lifecycleEventsChecklist) {

      Map<String, String> filteredStatementMappings = this.getService.getStatementMappingsForTargetFields(
          lifecycleEvent.getShaclReplacement(), sortedFields, serviceEventFilters);
      globalLifecycleMappings.put(lifecycleEvent, filteredStatementMappings);
    }

    // Pivot the data structure to be property-centric

    Map<String, Map<LifecycleEventType, String>> propertyToEventMappings = new HashMap<>();
    for (Map.Entry<LifecycleEventType, Map<String, String>> eventEntry : globalLifecycleMappings.entrySet()) {
      LifecycleEventType eventTypeKey = eventEntry.getKey();
      Map<String, String> propertyMappings = eventEntry.getValue();

      if (propertyMappings != null) {
        for (Map.Entry<String, String> propEntry : propertyMappings.entrySet()) {
          String propertyKey = propEntry.getKey();
          String sparqlStatement = propEntry.getValue();

          // Compute or create the inner map for this specific property
          propertyToEventMappings.computeIfAbsent(propertyKey, k -> new HashMap<>())
              .put(eventTypeKey, sparqlStatement);
        }
      }
    }

    return propertyToEventMappings;
  }

  /**
   * Executes the occurrence query and group them by the specified group variable.
   * 
   * @param entityType    Target resource ID.
   * @param queryMappings Additional query statements to be added if any.
   * @param eventType     The current event type, affecting the query for
   *                      execution. Closed should be completed.
   * @param pagination    Pagination state to filter results.
   */
  private DataManifest<List<Map<String, Object>>> executeOccurrenceQuery(String entityType,
      String[] lifecycleStatements,
      LifecycleEventType eventType, PaginationState pagination) {
    // ID retrieval and handling
    Queue<List<String>> ids = this.getService.getAllIds(entityType, lifecycleStatements[0], pagination);
    // Return early if nothing if found
    if (ids.isEmpty()) {
      return new DataManifest<>(new ArrayList<>(), new ArrayList<>());
    }

    List<List<String>> originalIds = new ArrayList<>(ids); // make it a persistent copy

    // Initialise separate queues for primary entity and events
    Set<List<String>> uniqueIdSet = new LinkedHashSet<>();
    Set<List<String>> uniqueEventIdSet = new LinkedHashSet<>();

    for (List<String> pair : originalIds) {
      if (pair != null && pair.size() >= 2) {
        uniqueIdSet.add(Collections.singletonList(pair.get(0)));
        uniqueEventIdSet.add(Collections.singletonList(pair.get(1)));
      }
    }

    // Convert back to queue
    Queue<List<String>> idQueue = new LinkedList<>(uniqueIdSet);
    Queue<List<String>> eventIdQueue = new LinkedList<>(uniqueEventIdSet);

    // Query preparation for primary entity
    Set<ColumnMetaPayload> entityVarSequences = new LinkedHashSet<>(this.taskEntityColumnMeta);
    String entityQuery = lifecycleStatements[1];

    // Query preparation for event

    Set<ColumnMetaPayload> varSequences = new LinkedHashSet<>(this.taskColumnMeta);
    String addQuery = lifecycleStatements[2];
    addQuery += this.parseEventOccurrenceQuery(LifecycleEventType.SERVICE_ORDER_DISPATCHED, varSequences);
    if (eventType.equals(LifecycleEventType.ACTIVE_SERVICE) || eventType.equals(LifecycleEventType.SERVICE_ACCRUAL)) {
      addQuery += this.parseEventOccurrenceQuery(LifecycleEventType.SERVICE_EXECUTION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(LifecycleEventType.SERVICE_CANCELLATION, varSequences);
      addQuery += this.parseEventOccurrenceQuery(LifecycleEventType.SERVICE_INCIDENT_REPORT, varSequences);
      addQuery += this.parseEventOccurrenceQuery(LifecycleEventType.SERVICE_EXEMPT, varSequences);
    }
    String occurrenceQueryString = this.genOccurrenceEventQueryStatement(varSequences, eventIdQueue, addQuery);

    // Execute primary entity and event queries in parallel
    List<DataManifest<Queue<SparqlBinding>>> parallelResults = ParallelTaskExecutor.execDualParallelQueries(
        // Query for entity
        () -> this.getService.getInstances(entityType, true, idQueue, entityQuery, new ArrayList<>(entityVarSequences)),
        // Query for event
        () -> {
          Queue<SparqlBinding> queue = this.getService.getInstances(occurrenceQueryString);
          return new DataManifest<>(queue, new ArrayList<>(varSequences));
        });

    // Unpack query results
    DataManifest<Queue<SparqlBinding>> resultsManifest = parallelResults.get(0);
    Queue<SparqlBinding> occurrenceResultQueue = parallelResults.get(1).data();

    // Combine results from entity query and event query

    // Convert results to map with IDs as the keys
    Map<String, Map<String, Object>> primaryById = mapBindingsById(resultsManifest.data(), QueryResource.ID_KEY);
    Map<String, Map<String, Object>> eventById = mapBindingsById(occurrenceResultQueue,
        QueryResource.EVENT_ID_VAR.getVarName());

    // Merge data of primary entity and event
    List<Map<String, Object>> mergedData = new ArrayList<>();
    for (List<String> pair : originalIds) {
      if (pair.size() < 2) {
        continue;
      }
      Map<String, Object> mergedRow = new LinkedHashMap<>();
      Map<String, Object> primaryRow = primaryById.get(pair.get(0));
      Map<String, Object> eventRow = eventById.get(pair.get(1));
      if (primaryRow != null) {
        mergedRow.putAll(primaryRow);
      }
      if (eventRow != null) {
        mergedRow.putAll(eventRow);
      }
      if (!mergedRow.isEmpty()) {
        mergedData.add(mergedRow);
      }
    }

    // Merge column metadata
    List<ColumnMetaPayload> mergedColumns = new ArrayList<>(resultsManifest.columns());
    mergedColumns.addAll(varSequences);

    return new DataManifest<>(mergedData, mergedColumns);
  }

  private Map<String, Map<String, Object>> mapBindingsById(Collection<SparqlBinding> bindings, String idKey) {
    return bindings.stream().map(binding -> Map.entry(
        binding.getFieldValue(idKey),
        this.lifecycleQueryService.parseLifecycleBinding(binding.get())))
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            Map.Entry::getValue,
            (existing, replacement) -> existing,
            LinkedHashMap::new));
  }

  private String genOccurrenceEventQueryStatement(Set<ColumnMetaPayload> varSequences,
      Queue<List<String>> eventIdQueue, String addQuery) {
    // Initialise blank template
    String occurrenceQueryString = QueryResource.getSelectQuery().getQueryString();
    occurrenceQueryString = occurrenceQueryString.substring(0,
        occurrenceQueryString.indexOf("WHERE {") + "WHERE {".length());

    // Append the core event anchor triple statements
    String eventAnchorString = "?order_event <https://spec.edmcouncil.org/fibo/ontology/FND/Relations/Relations/exemplifies> "
        +
        "<https://www.theworldavatar.com/kg/ontoservice/OrderReceivedEvent>;" +
        "<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/Occurrences/hasEventDate> ?event_date." +
        " BIND(xsd:date(?event_date) AS ?date)" +
        "?event_id a <https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>; "
        +
        " <https://www.omg.org/spec/Commons/DatesAndTimes/succeeds>* ?order_event.";
    occurrenceQueryString += eventAnchorString;

    varSequences.add(new ColumnMetaPayload(QueryResource.EVENT_ID_VAR.getVarName(), QueryResource.LITERAL_TYPE,
        ShaclResource.XSD_STRING)); // Add event ID column meta

    // Generate select variable clause
    List<String> selectVariables = new ArrayList<>();
    for (ColumnMetaPayload column : varSequences) {
      selectVariables.add(QueryResource.genVariable(column.value()).getQueryString());
    }

    occurrenceQueryString = occurrenceQueryString.replace("SELECT *",
        "SELECT DISTINCT " + String.join(" ", selectVariables));
    occurrenceQueryString = occurrenceQueryString.replace("?status", "?event ?event_status"); // Specific to event
                                                                                              // status

    // Generate value statement to include event IDs
    List<String> eventIds = new ArrayList<>();
    while (!eventIdQueue.isEmpty()) {
      List<String> item = eventIdQueue.poll();
      if (item != null && !item.isEmpty()) {
        eventIds.add("<" + item.get(0) + ">");
      }
    }

    occurrenceQueryString = occurrenceQueryString + "\n" + addQuery + "\n";
    occurrenceQueryString += QueryResource.values(eventIds, QueryResource.EVENT_ID_VAR.getVarName());
    occurrenceQueryString += "}";

    return occurrenceQueryString;
  }

  /**
   * Parses the event occurrence query to extract the variables and WHERE
   * contents.
   * 
   * @param lifecycleEvent Target event type.
   * @param columns        Set of columns names to be added.
   */
  private String parseEventOccurrenceQuery(LifecycleEventType lifecycleEvent, Set<ColumnMetaPayload> columns) {
    String replacementQueryLine = lifecycleEvent.getShaclReplacement();
    DataManifest<String> occurrenceQueryManifest = this.getService.getQuery(replacementQueryLine, true);
    List<ColumnMetaPayload> eventColumns = occurrenceQueryManifest.columns().stream()
        .filter(column -> !column.value().equals(QueryResource.ID_KEY)).toList();
    if (lifecycleEvent.equals(LifecycleEventType.SERVICE_ORDER_DISPATCHED)) {
      eventColumns = eventColumns.stream()
          .map(col -> new ColumnMetaPayload(col.value(), col.type(), col.datatype(), lifecycleEvent.getId(),
              col.arrayFields()))
          .toList();
    }
    columns.addAll(eventColumns);
    return LifecycleResource.extractOccurrenceQuery(occurrenceQueryManifest.data(), lifecycleEvent);
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
    return this.genOccurrence(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params, eventType, TrackActionType.IGNORED,
        successLogMessage, messageResource);
  }

  /**
   * Generate an occurrence for the specified resource.
   * 
   * @param resourceId        The target lifecycle resource for instantiation.
   * @param params            Existing configurable parameters that will be
   *                          amended to instantiate the occurrence.
   * @param eventType         Target event type.
   * @param trackAction       The action required for tracking.
   * @param successLogMessage Optional log message on success.
   * @param messageResource   Optional resource id of the message to be displayed
   *                          when successful.
   */
  public ResponseEntity<StandardApiResponse<?>> genOccurrence(String resourceId, Map<String, Object> params,
      LifecycleEventType eventType, TrackActionType action, String successLogMessage, String messageResource) {
    this.lifecycleQueryService.addOccurrenceParams(params, eventType);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(resourceId, params, successLogMessage,
        messageResource, TrackActionType.IGNORED);
    if (response.getStatusCode() == HttpStatus.OK && action != TrackActionType.IGNORED) {
      String orderTask = this.getPreviousOccurrence(params.get(QueryResource.ID_KEY).toString(), QueryResource.IRI_KEY,
          LifecycleEventType.SERVICE_ORDER_RECEIVED);
      this.addService.logActivity(orderTask, action);
    }
    return response;
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
        this.addService.instantiate(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params, TrackActionType.CREATION);
        // Error logs for any specified occurrence
      } catch (IllegalStateException _) {
        LOGGER.error("Error encountered while creating order for {} on {}! Read error logs for more details",
            contract, occurrenceDate);
        hasError = true;
      }
    }
    return hasError;
  }

  /**
   * Continues the task today by generating a new occurrence for the order
   * received and dispatch event of a specified contract.
   * 
   * @param taskId     The latest task ID.
   * @param contractId Target contract.
   */
  public ResponseEntity<StandardApiResponse<?>> continueTaskToday(String taskId, String contractId) {
    LOGGER.info("Generating the task for the next working day...");
    String currentDateTime = this.dateTimeService.getCurrentDateTime();
    // First instantiate the order received occurrence
    Map<String, Object> params = new HashMap<>();
    // Contract ID is mandatory to help generate the other related parameters
    params.put(LifecycleResource.CONTRACT_KEY, contractId);
    params.put(LifecycleResource.REMARKS_KEY, ORDER_INITIALISE_MESSAGE);
    params.put(LifecycleResource.DATE_TIME_KEY, currentDateTime);
    this.lifecycleQueryService.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
    // Generate a new unique ID for the occurrence by retrieving the prefix from the
    // stage instance
    String defaultPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString());
    params.remove(QueryResource.ID_KEY);
    String newTaskId = LifecycleResource.genIdAndInstanceParameters(defaultPrefix,
        LifecycleEventType.SERVICE_ORDER_RECEIVED, params);

    ResponseEntity<StandardApiResponse<?>> orderInstantiatedResponse = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params, TrackActionType.IGNORED);
    if (orderInstantiatedResponse.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Retrieving the current dispatch details...");
      ResponseEntity<StandardApiResponse<?>> prevDispatchResponse = this.getService.getInstance(taskId,
          LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      if (prevDispatchResponse.getStatusCode() == HttpStatus.OK) {
        Map<String, String> currentEntity = ((Map<String, Object>) prevDispatchResponse.getBody().data().items()
            .get(0)).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() instanceof SparqlResponseField
                    ? TypeCastUtils.castToObject(entry.getValue(),
                        SparqlResponseField.class).value()
                    : ""));
        params.putAll(currentEntity);
        params.put(LifecycleResource.REMARKS_KEY, ORDER_DISPATCH_MESSAGE);
        // Ensure new task ID is kept
        params.put(QueryResource.ID_KEY, newTaskId);
        LifecycleResource.genIdAndInstanceParameters(defaultPrefix, LifecycleEventType.SERVICE_ORDER_DISPATCHED,
            params);
        params.put(LifecycleResource.ORDER_KEY, orderInstantiatedResponse.getBody().data().id());
        return this.addService.instantiate(LifecycleEventType.SERVICE_ORDER_DISPATCHED.getId(), params,
            TrackActionType.IGNORED);
      }
    }
    throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY));
  }

  /**
   * Overwrite the date of order to a new specified date.
   * 
   * @param params Required parameters with configurable parameters to
   *               instantiate the occurrence.
   */
  public ResponseEntity<StandardApiResponse<?>> rescheduleTask(Map<String, Object> params) {
    LOGGER.info("Rescheduling task to new date...");
    // query for existing order occurrence and related IRIs
    SparqlBinding result = this.lifecycleQueryService.getInstance(FileService.RESCHEDULE_QUERY_RESOURCE,
        params.get(QueryResource.ID_KEY).toString());
    // parse related IRIs
    String lifecycleStartDate = result
        .getFieldValue(QueryResource.genVariable(LifecycleResource.SCHEDULE_START_DATE_KEY).getVarName());
    String lifecycleEndDate = result
        .getFieldValue(QueryResource.genVariable(LifecycleResource.SCHEDULE_END_DATE_KEY).getVarName());
    String expireStage = result.getFieldValue(LifecycleResource.STAGE_KEY);
    String orderEvent = result.getFieldValue(LifecycleResource.EVENT_KEY);
    // new date
    String rescheduleDate = params.get(LifecycleResource.RESCHEDULE_DATE_KEY).toString();
    String rescheduleDatetime = this.dateTimeService.getDateTimeFromDate(rescheduleDate);
    // update database
    String query = this.lifecycleQueryFactory.getRescheduleQuery(lifecycleStartDate, lifecycleEndDate, expireStage,
        orderEvent, rescheduleDate, rescheduleDatetime);
    ResponseEntity<StandardApiResponse<?>> response = this.updateService.update(query);
    if (response.getStatusCode() == HttpStatus.OK) {
      this.addService.logActivity(orderEvent, TrackActionType.RESCHEDULED);
    }
    return response;
  }

  /**
   * Generate an occurrence for the order dispatch, delivery, or accrual event of
   * a specified contract.
   * 
   * @param params      Required parameters with configurable parameters to
   *                    instantiate the occurrence.
   * @param eventType   Target event type.
   * @param trackAction The action required for tracking.
   */
  public ResponseEntity<StandardApiResponse<?>> genServiceEventOccurrence(Map<String, Object> params,
      LifecycleEventType eventType, TrackActionType action) {
    String remarksMsg;
    String successMsgId;
    List<LifecycleEventType> fallbackEvents = new ArrayList<>();
    params.put(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    switch (eventType) {
      case LifecycleEventType.SERVICE_EXECUTION:
        remarksMsg = ORDER_COMPLETE_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_COMPLETE_KEY;
        fallbackEvents.add(LifecycleEventType.SERVICE_ORDER_DISPATCHED);
        break;
      case LifecycleEventType.SERVICE_ORDER_DISPATCHED:
        remarksMsg = ORDER_DISPATCH_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_ASSIGN_KEY;
        fallbackEvents.add(LifecycleEventType.SERVICE_ORDER_RECEIVED);
        break;
      case LifecycleEventType.SERVICE_ACCRUAL:
        remarksMsg = ORDER_ACCRUAL_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_ACCRUAL_KEY;
        fallbackEvents.add(LifecycleEventType.SERVICE_EXECUTION);
        fallbackEvents.add(LifecycleEventType.SERVICE_CANCELLATION);
        fallbackEvents.add(LifecycleEventType.SERVICE_INCIDENT_REPORT);
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

    String orderId = params.get(QueryResource.ID_KEY).toString();
    // Get the order received IRI
    String orderEventIri = this.getPreviousOccurrence(orderId, QueryResource.IRI_KEY,
        LifecycleEventType.SERVICE_ORDER_RECEIVED);
    // Set previous occurrence
    String previousOccurrenceIri = this.getPreviousOccurrence(orderId,
        fallbackEvents.toArray(new LifecycleEventType[0]));
    params.put(LifecycleResource.ORDER_KEY, previousOccurrenceIri);
    ResponseEntity<StandardApiResponse<?>> response = this.updateService.update(orderId, eventType.getId(),
        successMsgId, params, TrackActionType.IGNORED);
    if (response.getStatusCode() == HttpStatus.OK) {
      this.addService.logActivity(orderEventIri, action);
    }
    return response;
  }

  /**
   * Gets the previous occurence iri based on the possible events.
   * 
   * @param eventId The identifier of the latest event in the succeeds chain.
   * @param events  The plausible events that may be previous occurrence.
   */
  public String getPreviousOccurrence(String eventId, LifecycleEventType... events) {
    String previousOccurrenceIri = null;
    for (LifecycleEventType fallbackEvent : events) {
      previousOccurrenceIri = this.getPreviousOccurrence(eventId, QueryResource.IRI_KEY, fallbackEvent);
      if (previousOccurrenceIri != null) {
        return previousOccurrenceIri;
      }
    }
    if (previousOccurrenceIri == null) {
      throw new NullPointerException("No valid previous occurrence found from fallback events!");
    }
    return previousOccurrenceIri;
  }

  /**
   * Retrieves the previous occurrence instance based on its event type and latest
   * event id.
   * 
   * @param latestEventId The identifier of the latest event in the succeeds
   *                      chain.
   * @param fieldKey      The field key to extract. Either id or iri.
   * @param eventType     Target event type to query for.
   */
  public String getPreviousOccurrence(String latestEventId, String fieldKey, LifecycleEventType eventType) {
    SparqlBinding instance = this.lifecycleQueryService
        .getInstance(FileService.CONTRACT_PREV_EVENT_QUERY_RESOURCE, true, latestEventId, eventType.getEvent());
    return instance == null ? null : instance.getFieldValue(fieldKey);
  }
}