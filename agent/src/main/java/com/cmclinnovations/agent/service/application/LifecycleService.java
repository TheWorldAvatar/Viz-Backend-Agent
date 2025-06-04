package com.cmclinnovations.agent.service.application;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.ApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.template.LifecycleQueryFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.StringResource;

@Service
public class LifecycleService {
  private final AddService addService;
  private final DateTimeService dateTimeService;
  private final DeleteService deleteService;
  private final GetService getService;
  private final LifecycleQueryFactory lifecycleQueryFactory;
  private final Map<String, List<Integer>> lifecycleVarSequence = new HashMap<>();
  private final Map<String, List<Integer>> taskVarSequence = new HashMap<>();

  private static final String ORDER_INITIALISE_MESSAGE = "Order received and is being processed.";
  private static final String ORDER_DISPATCH_MESSAGE = "Order has been assigned and is awaiting execution.";
  private static final String ORDER_COMPLETE_MESSAGE = "Order has been completed successfully.";
  private static final String SERVICE_DISCHARGE_MESSAGE = "Service has been completed successfully.";
  private static final Logger LOGGER = LogManager.getLogger(LifecycleService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   */
  public LifecycleService(AddService addService, DateTimeService dateTimeService, DeleteService deleteService,
      GetService getService) {
    this.addService = addService;
    this.dateTimeService = dateTimeService;
    this.deleteService = deleteService;
    this.getService = getService;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();

    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_START_DATE_KEY, List.of(2, 0));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_END_DATE_KEY, List.of(2, 1));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_START_TIME_KEY, List.of(2, 2));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_END_TIME_KEY, List.of(2, 3));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_TYPE_KEY, List.of(2, 4));

    this.taskVarSequence.put(LifecycleResource.DATE_KEY, List.of(-3, 1));
    this.taskVarSequence.put(LifecycleResource.EVENT_KEY, List.of(-3, 2));
    this.taskVarSequence.put(LifecycleResource.EVENT_ID_KEY, List.of(1000, 999));
  }

  /**
   * Add the required stage instance into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addStageInstanceToParams(Map<String, Object> params, LifecycleEventType eventType) {
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    LOGGER.debug("Adding stage parameters for {}...", contractId);
    String query = this.lifecycleQueryFactory.getStageQuery(contractId, eventType);
    String stage = this.getService.getInstance(query).getFieldValue(LifecycleResource.IRI_KEY);
    params.put(LifecycleResource.STAGE_KEY, stage);
  }

  /**
   * Populate the remaining occurrence parameters into the request parameters.
   * Defaults to the current date as date is not supplied.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addOccurrenceParams(Map<String, Object> params, LifecycleEventType eventType) {
    addOccurrenceParams(params, eventType, this.dateTimeService.getCurrentDate());
  }

  /**
   * Populate the remaining occurrence parameters into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   * @param date      Date in YYYY-MM-DD format.
   */
  public void addOccurrenceParams(Map<String, Object> params, LifecycleEventType eventType, String date) {
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    LOGGER.debug("Adding occurrence parameters for {}...", contractId);
    String query = this.lifecycleQueryFactory.getStageQuery(contractId, eventType);
    String stage = this.getService.getInstance(query).getFieldValue(LifecycleResource.IRI_KEY);
    params.putIfAbsent("id",
        StringResource.getPrefix(stage) + "/" + eventType.getId() + "/"
            + UUID.randomUUID());
    params.put(LifecycleResource.STAGE_KEY, stage);
    params.put(LifecycleResource.EVENT_KEY, eventType.getEvent());
    // Only update the date field if there is no pre-existing field
    params.putIfAbsent(LifecycleResource.DATE_KEY, date);
    params.putIfAbsent(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    // Update the order enum with the specific event instance if it exist
    params.computeIfPresent(LifecycleResource.ORDER_KEY, (key, value) -> {
      String orderEnum = value.toString();
      String eventQuery = this.lifecycleQueryFactory.getEventQuery(
          params.get(LifecycleResource.CONTRACT_KEY).toString(),
          params.get(LifecycleResource.DATE_KEY).toString(),
          LifecycleResource.getEventClassFromOrderEnum(orderEnum));
      return this.getService.getInstance(eventQuery).getFieldValue(LifecycleResource.IRI_KEY);
    });
  }

  /**
   * Retrieve the status of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<ApiResponse> getContractStatus(String contract) {
    LOGGER.debug("Retrieving the status of the contract...");
    String query = this.lifecycleQueryFactory.getServiceStatusQuery(contract);
    SparqlBinding result = this.getService.getInstance(query);
    LOGGER.info("Successfuly retrieved contract status!");
    return new ResponseEntity<>(
        new ApiResponse(result.getFieldValue(LifecycleResource.STATUS_KEY),
            result.getFieldValue(LifecycleResource.IRI_KEY)),
        HttpStatus.OK);
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
   * Retrieve all the contract instances and their information based on the
   * resource ID.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param eventType  The target event type to retrieve.
   */
  public ResponseEntity<?> getContracts(String resourceID, boolean requireLabel, LifecycleEventType eventType) {
    LOGGER.debug("Retrieving all contracts...");
    String additionalQueryStatement = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    Queue<SparqlBinding> instances = this.getService.getInstances(resourceID, null, "", additionalQueryStatement,
        requireLabel, this.lifecycleVarSequence);
    return new ResponseEntity<>(
        instances.stream()
            .map(SparqlBinding::get)
            .toList(),
        HttpStatus.OK);
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date.
   * 
   * @param contract   The contract identifier.
   * @param entityType Target resource ID.
   */
  public ResponseEntity<List<Map<String, Object>>> getOccurrences(String contract, String entityType) {
    String activeServiceQuery = this.lifecycleQueryFactory.getServiceTasksQuery(contract, null);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, activeServiceQuery);
    LOGGER.info("Successfuly retrieved all associated services!");
    return new ResponseEntity<>(occurrences, HttpStatus.OK);
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date.
   * 
   * @param timestamp  Timestamp in UNIX format.
   * @param entityType Target resource ID.
   */
  public ResponseEntity<List<Map<String, Object>>> getOccurrences(long timestamp, String entityType) {
    // Get date from timestamp
    String targetDate = this.dateTimeService.getDateFromTimestamp(timestamp);
    String activeServiceQuery = this.lifecycleQueryFactory.getServiceTasksQuery(null, targetDate);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, activeServiceQuery);
    LOGGER.info("Successfuly retrieved services in progress!");
    return new ResponseEntity<>(occurrences, HttpStatus.OK);
  }

  /**
   * Executes the occurrence query and group them by the specified group variable.
   * 
   * @param entityType      Target resource ID.
   * @param additionalQuery Additional query to append to the main query.
   */
  private List<Map<String, Object>> executeOccurrenceQuery(String entityType, String additionalQuery) {
    Map<String, List<Integer>> varSequences = new HashMap<>(this.taskVarSequence);
    String addQuery = "";
    addQuery += this.parseEventOccurrenceQuery(-2, LifecycleEventType.SERVICE_ORDER_DISPATCHED, varSequences);
    addQuery += this.parseEventOccurrenceQuery(-1, LifecycleEventType.SERVICE_EXECUTION, varSequences);
    addQuery += additionalQuery;
    Queue<SparqlBinding> results = this.getService.getInstances(entityType, null, "", addQuery,
        true, varSequences);
    return results.stream()
        .map(SparqlBinding::get)
        .toList();
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
      Map<String, List<Integer>> varSequences) {
    String replacementQueryLine = lifecycleEvent.getShaclReplacement();
    Queue<String> occurrenceQuery = this.getService.getQuery(replacementQueryLine, "", true);
    // First query is non-necessary and can be used to extract the variables
    Map<String, List<Integer>> dispatchVars = LifecycleResource.extractOccurrenceVariables(occurrenceQuery.poll(),
        groupIndex);
    varSequences.putAll(dispatchVars);
    return LifecycleResource.extractOccurrenceQuery(occurrenceQuery.poll(), lifecycleEvent);
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
        .getFieldValue(StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_START_DATE_KEY));
    String endDate = bindings.getFieldValue(StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_END_DATE_KEY));
    String recurrence = bindings
        .getFieldValue(StringResource.parseQueryVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY));
    Queue<String> occurrences = new ArrayDeque<>();
    // Extract date of occurrences based on the schedule information
    // For single time schedules, simply add the start date
    if (recurrence.equals("P1D")) {
      occurrences.offer(startDate);
    } else if (recurrence.equals("P2D")) {
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
    String orderPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString()) + "/"
        + LifecycleEventType.SERVICE_ORDER_RECEIVED.getId() + "/";
    // Instantiate each occurrence
    boolean hasError = false;
    while (!occurrences.isEmpty()) {
      // Retrieve and update the date of occurrence
      String occurrenceDate = occurrences.poll();
      // set new id each time
      params.put("id", orderPrefix + UUID.randomUUID());
      params.put(LifecycleResource.DATE_KEY, occurrenceDate);
      ResponseEntity<ApiResponse> response = this.addService.instantiate(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      // Error logs for any specified occurrence
      if (response.getStatusCode() != HttpStatus.CREATED) {
        LOGGER.error("Error encountered while creating order for {} on {}! Read error message for more details: {}",
            contract, occurrenceDate, response.getBody().getMessage());
        hasError = true;
      }
    }
    return hasError;
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
      String currentContract = results.poll().getFieldValue(LifecycleResource.IRI_KEY);
      params.put(LifecycleResource.CONTRACT_KEY, currentContract);
      this.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_COMPLETION);
      ResponseEntity<ApiResponse> response = this.addService.instantiate(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      // Error logs for any specified occurrence
      if (response.getStatusCode() != HttpStatus.CREATED) {
        LOGGER.error("Error encountered while discharging the contract for {}! Read error message for more details: {}",
            currentContract, response.getBody().getMessage());
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
  public ResponseEntity<ApiResponse> genDispatchOrDeliveryOccurrence(Map<String, Object> params,
      LifecycleEventType eventType) {
    String remarksMsg;
    String createErrorMsg;
    String successMsg;
    switch (eventType) {
      case LifecycleEventType.SERVICE_EXECUTION:
        remarksMsg = ORDER_COMPLETE_MESSAGE;
        createErrorMsg = "Error encountered while completing the order : {}! Read error message for more details: {}";
        successMsg = "Service has been completed successfully!";
        break;
      case LifecycleEventType.SERVICE_ORDER_DISPATCHED:
        remarksMsg = ORDER_DISPATCH_MESSAGE;
        createErrorMsg = "Error encountered while dispatching details for the order: {}! Read error message for more details: {}";
        successMsg = "Assigment of dispatch details is successful!";
        break;
      default:
        return new ResponseEntity<>(
            new ApiResponse("Invalid event type invocation!"),
            HttpStatus.INTERNAL_SERVER_ERROR);
    }
    params.put(LifecycleResource.REMARKS_KEY, remarksMsg);
    this.addOccurrenceParams(params, eventType);

    // Attempt to delete any existing occurrence before any updates
    ResponseEntity<ApiResponse> response = this.deleteService.delete(
        eventType.getId(), params.get("id").toString());
    // Log responses
    LOGGER.info(response.getBody().getMessage());

    // Ensure that the event identifier mapped directly to the jsonLd file name
    response = this.addService.instantiate(eventType.getId(), params);
    if (response.getStatusCode() != HttpStatus.CREATED) {
      LOGGER.error(createErrorMsg, params.get(LifecycleResource.ORDER_KEY), response.getBody().getMessage());
    }
    LOGGER.info(successMsg);
    return response;
  }

  /**
   * Retrieves the form template for the specified event type.
   * 
   * @param eventType The target event type.
   * @param targetId  The target instance IRI.
   */
  public ResponseEntity<Map<String, Object>> getForm(LifecycleEventType eventType, String targetId) {
    // Ensure that there is a specific event type target
    String replacementQueryLine = eventType.getShaclReplacement();
    Map<String, Object> currentEntity = new HashMap<>();
    if (targetId != null) {
      LOGGER.debug("Detected specific entity ID! Retrieving target event occurrence of {}...", eventType);
      ResponseEntity<?> currentEntityResponse = this.getOccurrenceDetails(eventType, targetId, false);
      if (currentEntityResponse.getStatusCode() == HttpStatus.OK) {
        currentEntity = (Map<String, Object>) currentEntityResponse.getBody();
      }
    }
    return this.getService.getForm(replacementQueryLine, true, currentEntity);
  }

  /**
   * Retrieves the occurrence's dynamic details for the specified event type.
   * 
   * @param eventType    The target event type.
   * @param targetId     The target instance IRI.
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   */
  private ResponseEntity<?> getOccurrenceDetails(LifecycleEventType eventType, String targetId, boolean requireLabel) {
    // Ensure that there is a specific event type target
    String replacementQueryLine = eventType.getShaclReplacement();
    String query = this.lifecycleQueryFactory.getEventQuery(targetId, eventType);
    String targetOccurrence;
    try {
      targetOccurrence = this.getService.getInstance(query)
          .getFieldValue(LifecycleResource.IRI_KEY);
    } catch (NullPointerException e) {
      return new ResponseEntity<>(new HashMap<>(), HttpStatus.NOT_FOUND);
    }
    LOGGER.debug("Retrieving relevant entity information for occurrence of {}...", eventType);
    return this.getService.getInstance(targetOccurrence, requireLabel, replacementQueryLine);

  }
}