package com.cmclinnovations.agent.service.application;

import java.util.AbstractMap;
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
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class LifecycleContractService {
  private final AddService addService;
  private final GetService getService;
  private final UpdateService updateService;
  private final LifecycleQueryService lifecycleQueryService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private final LifecycleQueryFactory lifecycleQueryFactory;
  private final Map<Variable, List<Integer>> lifecycleVarSequence = new HashMap<>();
  private static final String SERVICE_DISCHARGE_MESSAGE = "Service has been completed successfully.";
  private static final Logger LOGGER = LogManager.getLogger(LifecycleContractService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   */
  public LifecycleContractService(AddService addService, GetService getService, UpdateService updateService,
      LifecycleQueryService lifecycleQueryService, ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.getService = getService;
    this.updateService = updateService;
    this.lifecycleQueryService = lifecycleQueryService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();

    this.lifecycleVarSequence.put(QueryResource.genVariable(LifecycleResource.LAST_MODIFIED_KEY), List.of(-3, 2));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_START_DATE_VAR, List.of(2, 0));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_END_DATE_VAR, List.of(2, 1));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_START_TIME_VAR, List.of(2, 2));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_END_TIME_VAR, List.of(2, 3));
    this.lifecycleVarSequence.put(QueryResource.genVariable(LifecycleResource.SCHEDULE_TYPE_KEY), List.of(2, 4));
    this.lifecycleVarSequence.put(QueryResource.SCHEDULE_RECURRENCE_VAR, List.of(2, 5));
  }

  /**
   * Retrieve the status of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<StandardApiResponse<?>> getContractStatus(String contract) {
    LOGGER.debug("Retrieving the status of the contract...");
    SparqlBinding result = this.lifecycleQueryService.getInstance(FileService.CONTRACT_STATUS_QUERY_RESOURCE, contract);
    LOGGER.info("Successfuly retrieved contract status!");
    return this.responseEntityBuilder.success(result.getFieldValue(QueryResource.IRI_KEY),
        result.getFieldValue(LifecycleResource.STATUS_KEY));
  }

  /**
   * Verify if the contract should guard against approval.
   * 
   * @param contract The target contract id.
   */
  public boolean guardAgainstApproval(String contract) {
    String contractStatus = this.lifecycleQueryService.getInstance(FileService.CONTRACT_STATUS_QUERY_RESOURCE, contract)
        .getFieldValue(LifecycleResource.STATUS_KEY);
    return !contractStatus.equals("Pending");
  }

  /**
   * Generates a report instance.
   * 
   * @param contractId The ID of the target contract to report on.
   */
  public void genReportInstance(String contractId) {
    String contract = this.lifecycleQueryService.getInstance(FileService.CONTRACT_QUERY_RESOURCE, contractId)
        .getFieldValue(QueryResource.IRI_KEY);
    Map<String, Object> reportParams = new HashMap<>();
    reportParams.put(LifecycleResource.CONTRACT_KEY, contract);
    this.addService.instantiate(LifecycleResource.LIFECYCLE_REPORT_RESOURCE, reportParams, null,
        LocalisationResource.SUCCESS_ADD_REPORT_KEY);
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
   * Retrieve the schedule details of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<Map<String, Object>> getSchedule(String contract) {
    LOGGER.debug("Retrieving the schedule details of the contract...");
    SparqlBinding result = this.lifecycleQueryService.getInstance(FileService.CONTRACT_SCHEDULE_QUERY_RESOURCE,
        contract, contract);
    LOGGER.info("Successfuly retrieved schedule!");
    return new ResponseEntity<>(result.get(), HttpStatus.OK);
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
    String stage = this.lifecycleQueryService.getInstance(FileService.CONTRACT_STAGE_QUERY_RESOURCE, contractId,
        eventType.getStage())
        .getFieldValue(QueryResource.IRI_KEY);
    params.put(LifecycleResource.STAGE_KEY, stage);
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
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, false);
    // Sorting is irrelevant for count
    String[] addStatements = this.genLifecycleStatements(eventType, new HashSet<>(), parsedFilters, "", false);
    return this.responseEntityBuilder.success(null,
        String.valueOf(this.getService.getCount(resourceID, addStatements[0], "", filters, true)));
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
    String originalField = LocalisationResource.parseTranslationToOriginal(field, true);
    Map<String, Set<String>> parsedFilters = StringResource.parseFilters(filters, false);
    parsedFilters.remove(originalField);
    // Sorting is irrelevant for specific lifecycle statements
    String[] addStatements = this.genLifecycleStatements(eventType, new HashSet<>(), parsedFilters, originalField,
        false);
    List<String> options = this.getService.getAllFilterOptions(resourceID, originalField, addStatements[0], search,
        parsedFilters);
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
    Map<Variable, List<Integer>> contractVariables = new HashMap<>(this.lifecycleVarSequence);
    if (eventType.equals(LifecycleEventType.APPROVED)) {
      contractVariables.put(
          QueryResource.genVariable(LocalisationTranslator.getMessage(LocalisationResource.VAR_STATUS_KEY)),
          List.of(1, 1));
    }
    String[] addStatements = this.genLifecycleStatements(eventType, pagination.sortedFields(), pagination.filters(), "",
        true);
    Queue<List<String>> ids = this.getService.getAllIds(resourceID, addStatements[0], pagination);
    Queue<SparqlBinding> instances = this.getService.getInstances(resourceID, requireLabel, ids,
        addStatements[1], contractVariables);
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
      this.lifecycleQueryService.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_COMPLETION);
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
   * Generates additional query statements based on the current event during the
   * lifecycle.
   * 
   * @param eventType        The target event type to retrieve.
   * @param sortedFields     Set of fields for sorting that should be included.
   * @param filters          Filters set by the user.
   * @param field            Optional target to include the corresponding filter
   *                         statements.
   * @param reqOriStatements Requires that the original statements are present.
   */
  private String[] genLifecycleStatements(LifecycleEventType eventType, Set<String> sortedFields,
      Map<String, Set<String>> filters, String field, boolean reqOriStatements) {
    Map<String, String> statementMappings = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    Map<String, String> extendedMappings = this.lifecycleQueryFactory
        .insertExtendedScheduleFilters(statementMappings);
    // Process end date first as it will be removed if not required
    String endDateFilter = this.processEndDateScheduleForFilter(filters, extendedMappings);
    String lifecycleStatements = this.lifecycleQueryService.genLifecycleStatements(extendedMappings, sortedFields,
        filters, field);
    lifecycleStatements += endDateFilter;
    if (reqOriStatements) {
      return new String[] { lifecycleStatements,
          statementMappings.values().stream().collect(Collectors.joining("\n")) };
    } else {
      return new String[] { lifecycleStatements };
    }
  }

  /**
   * Processes end date only if it is present in the filters. Method will remove this variable from main mappings.
   * 
   * @param filters           The filters passed through the request.
   * @param statementMappings Stores the current statements being used.
   */
  private String processEndDateScheduleForFilter(
      Map<String, Set<String>> filters, Map<String, String> statementMappings) {
    String endDateVar = QueryResource.SCHEDULE_END_DATE_VAR.getVarName();
    if (filters.containsKey(endDateVar)) {
      Set<String> endDateFilters = filters.get(endDateVar);
      String output;
      if (endDateFilters.contains(QueryResource.NULL_KEY)) {
        String filterClause = QueryResource.filterOrExpressions(StringResource.ORIGINAL_PREFIX + endDateVar,
            endDateFilters);
        output = statementMappings.get(endDateVar) + filterClause;
      } else {
        StringBuilder builder = new StringBuilder();
        QueryResource.genFilterStatements(statementMappings.get(endDateVar), endDateVar, endDateFilters, builder);
        output = builder.toString();
      }
      statementMappings.remove(endDateVar);
      return output;
    }
    return "";
  }
}