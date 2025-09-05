package com.cmclinnovations.agent.service.application;

import java.util.AbstractMap;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
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
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
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
  private final DeleteService deleteService;
  private final GetService getService;
  private final ResponseEntityBuilder responseEntityBuilder;

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
      GetService getService, ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.dateTimeService = dateTimeService;
    this.deleteService = deleteService;
    this.getService = getService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.lifecycleQueryFactory = new LifecycleQueryFactory();

    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_START_DATE_KEY, List.of(2, 0));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_END_DATE_KEY, List.of(2, 1));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_START_TIME_KEY, List.of(2, 2));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_END_TIME_KEY, List.of(2, 3));
    this.lifecycleVarSequence.put(LifecycleResource.SCHEDULE_TYPE_KEY, List.of(2, 4));

    this.taskVarSequence.put(LifecycleResource.DATE_KEY, List.of(-3, 1));
    this.taskVarSequence.put(LifecycleResource.EVENT_KEY, List.of(-3, 2));
    this.taskVarSequence.put(LifecycleResource.EVENT_ID_KEY, List.of(1000, 999));
    this.taskVarSequence.put(LifecycleResource.EVENT_STATUS_KEY, List.of(1000, 1000));
  }

  /**
   * Add the required stage instance into the request parameters.
   * 
   * @param params    The target parameters to update.
   * @param eventType The target event type to retrieve.
   */
  public void addStageInstanceToParams(Map<String, Object> params, LifecycleEventType eventType) {
    String contractId = params.get(StringResource.ID_KEY).toString();
    LOGGER.debug("Adding stage parameters for contract...");
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
    LifecycleResource.genIdAndInstanceParameters(StringResource.getPrefix(stage), eventType, params);
    params.put(LifecycleResource.STAGE_KEY, stage);
    params.put(LifecycleResource.EVENT_KEY, eventType.getEvent());
    // Only update the date field if there is no pre-existing field
    params.putIfAbsent(LifecycleResource.DATE_KEY, date);
    params.putIfAbsent(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
    // Update the order enum with the specific event instance if it exist
    params.computeIfPresent(LifecycleResource.ORDER_KEY, (key, value) -> {
      String orderEnum = value.toString();
      String eventQuery = this.lifecycleQueryFactory.getContractEventQuery(
          params.get(LifecycleResource.CONTRACT_KEY).toString(),
          params.get(LifecycleResource.DATE_KEY).toString(),
          null,
          LifecycleResource.getEventClassFromOrderEnum(orderEnum));
      return this.getService.getInstance(eventQuery).getFieldValue(LifecycleResource.IRI_KEY);
    });
  }

  /**
   * Retrieve the status of the contract.
   * 
   * @param contract The target contract id.
   */
  public ResponseEntity<StandardApiResponse> getContractStatus(String contract) {
    LOGGER.debug("Retrieving the status of the contract...");
    String query = this.lifecycleQueryFactory.getServiceStatusQuery(contract);
    SparqlBinding result = this.getService.getInstance(query);
    LOGGER.info("Successfuly retrieved contract status!");
    return this.responseEntityBuilder.success(result.getFieldValue(LifecycleResource.IRI_KEY),
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
   * Retrieve all the contract instances and their information based on the
   * resource ID.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param eventType  The target event type to retrieve.
   */
  public ResponseEntity<StandardApiResponse> getContracts(String resourceID, boolean requireLabel,
      LifecycleEventType eventType) {
    LOGGER.debug("Retrieving all contracts...");
    String additionalQueryStatement = this.lifecycleQueryFactory.genLifecycleFilterStatements(eventType);
    Queue<SparqlBinding> instances = this.getService.getInstances(resourceID, null, "", additionalQueryStatement,
        requireLabel, this.lifecycleVarSequence);
    return this.responseEntityBuilder.success(null,
        instances.stream()
            .map(SparqlBinding::get)
            .toList());
  }

  /**
   * Retrieve all service related occurrences in the lifecycle for the specified
   * date.
   * 
   * @param contract   The contract identifier.
   * @param entityType Target resource ID.
   */
  public ResponseEntity<StandardApiResponse> getOccurrences(String contract, String entityType) {
    String activeServiceQuery = this.lifecycleQueryFactory.getServiceTasksQuery(contract, null, null);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, activeServiceQuery, null);
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
   */
  public ResponseEntity<StandardApiResponse> getOccurrences(String startTimestamp, String endTimestamp,
      String entityType, boolean isClosed) {
    String targetStartDate = "";
    String targetEndDate = "";
    if (startTimestamp == null && endTimestamp == null) {
      targetEndDate = this.dateTimeService.getCurrentDate();
    } else {
      targetStartDate = this.dateTimeService.getDateFromTimestamp(startTimestamp);
      targetEndDate = this.dateTimeService.getDateFromTimestamp(endTimestamp);
      // Verify that the end date occurs after the start date
      if (this.dateTimeService.isFutureDate(targetStartDate, targetEndDate)) {
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_CHRONOLOGY_KEY));
      }
      // Users can only view upcoming scheduled tasks after today
      if (!isClosed && !this.dateTimeService.isFutureDate(targetStartDate)) {
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_SCHEDULED_PRESENT_KEY));
      }
    }
    String activeServiceQuery = this.lifecycleQueryFactory.getServiceTasksQuery(null, targetStartDate, targetEndDate);
    List<Map<String, Object>> occurrences = this.executeOccurrenceQuery(entityType, activeServiceQuery, isClosed);
    LOGGER.info("Successfuly retrieved tasks!");
    return this.responseEntityBuilder.success(null, occurrences);
  }

  /**
   * Executes the occurrence query and group them by the specified group variable.
   * 
   * @param entityType      Target resource ID.
   * @param additionalQuery Additional query to append to the main query.
   * @param isClosed        Indicates whether to retrieve closed tasks.
   */
  private List<Map<String, Object>> executeOccurrenceQuery(String entityType, String additionalQuery,
      Boolean isClosed) {
    Map<String, List<Integer>> varSequences = new HashMap<>(this.taskVarSequence);
    String addQuery = "";
    addQuery += this.parseEventOccurrenceQuery(-2, LifecycleEventType.SERVICE_ORDER_DISPATCHED, varSequences);
    addQuery += this.parseEventOccurrenceQuery(-1, LifecycleEventType.SERVICE_EXECUTION, varSequences);
    addQuery += additionalQuery;
    Queue<SparqlBinding> results = this.getService.getInstances(entityType, null, "", addQuery,
        true, varSequences);
    return results.stream()
        .filter(binding -> {
          // If filter is not required, break early
          if (isClosed == null) {
            return false;
          }
          String eventType = binding.getFieldValue(LifecycleResource.EVENT_KEY);
          String eventStatus = binding.getFieldValue(LifecycleResource.EVENT_STATUS_KEY);
          // Verify if event matches closed or open state
          if (isClosed) {
            // When event is delivery and has completed status, it is closed
            return (eventType.equals(LifecycleResource.EVENT_DELIVERY)
                && LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS.equals(eventStatus)) ||
                eventType.equals(LifecycleResource.EVENT_CANCELLATION) ||
                eventType.equals(LifecycleResource.EVENT_INCIDENT_REPORT);
          } else {
            // When event is delivery and has pending status, it is still open for changes
            return (eventType.equals(LifecycleResource.EVENT_DELIVERY)
                && LifecycleResource.COMPLETION_EVENT_PENDING_STATUS.equals(eventStatus)) ||
                eventType.equals(LifecycleResource.EVENT_DISPATCH) ||
                eventType.equals(LifecycleResource.EVENT_ORDER_RECEIVED);
          }
        })
        .map(binding -> {
          String eventStatus = binding.getFieldValue(LifecycleResource.EVENT_STATUS_KEY);

          return (Map<String, Object>) binding.get().entrySet().stream()
              .filter(entry -> !entry.getKey()
                  .equals(QueryResource.genVariable(LifecycleResource.EVENT_STATUS_KEY).getVarName()))
              .map(entry -> {
                if (entry.getKey().equals(LifecycleResource.EVENT_KEY)) {
                  SparqlResponseField eventField = TypeCastUtils.castToObject(entry.getValue(),
                      SparqlResponseField.class);
                  // For any pending completion events, simply reset it to the previous event
                  // status as they are incomplete or in a saved state, and should still be
                  // outstanding
                  String eventType = eventField.value();
                  if (eventType.equals(LifecycleResource.EVENT_DELIVERY)
                      && LifecycleResource.COMPLETION_EVENT_PENDING_STATUS.equals(eventStatus)) {
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
        .getFieldValue(QueryResource.genVariable(LifecycleResource.SCHEDULE_START_DATE_KEY).getVarName());
    String endDate = bindings.getFieldValue(QueryResource.genVariable(LifecycleResource.SCHEDULE_END_DATE_KEY).getVarName());
    String recurrence = bindings
        .getFieldValue(QueryResource.genVariable(LifecycleResource.SCHEDULE_RECURRENCE_KEY).getVarName());
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
    String orderPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString());
    // Instantiate each occurrence
    boolean hasError = false;
    while (!occurrences.isEmpty()) {
      // Retrieve and update the date of occurrence
      String occurrenceDate = occurrences.poll();
      // set new id each time
      params.remove(StringResource.ID_KEY);
      LifecycleResource.genIdAndInstanceParameters(orderPrefix, LifecycleEventType.SERVICE_ORDER_RECEIVED, params);
      params.put(LifecycleResource.DATE_KEY, occurrenceDate);
      ResponseEntity<StandardApiResponse> response = this.addService.instantiate(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      // Error logs for any specified occurrence
      if (response.getStatusCode() != HttpStatus.OK) {
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
  public ResponseEntity<StandardApiResponse> continueTaskOnNextWorkingDay(String taskId, String contractId) {
    LOGGER.info("Generating the task for the next working day...");
    // First instantiate the order received occurrence
    Map<String, Object> params = new HashMap<>();
    // Contract ID is mandatory to help generate the other related parameters
    params.put(LifecycleResource.CONTRACT_KEY, contractId);
    params.put(LifecycleResource.REMARKS_KEY, ORDER_INITIALISE_MESSAGE);
    params.put(LifecycleResource.DATE_KEY, this.dateTimeService.getNextWorkingDate());
    this.addOccurrenceParams(params, LifecycleEventType.SERVICE_ORDER_RECEIVED);
    // Generate a new unique ID for the occurrence by retrieving the prefix from the
    // stage instance
    String defaultPrefix = StringResource.getPrefix(params.get(LifecycleResource.STAGE_KEY).toString());
    params.remove(StringResource.ID_KEY);
    LifecycleResource.genIdAndInstanceParameters(defaultPrefix, LifecycleEventType.SERVICE_ORDER_RECEIVED, params);

    ResponseEntity<StandardApiResponse> orderInstantiatedResponse = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
    if (orderInstantiatedResponse.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Retrieving the current dispatch details...");
      String query = this.lifecycleQueryFactory.getContractEventQuery(contractId, null, taskId,
          LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      String prevDispatchId = this.getService.getInstance(query).getFieldValue(StringResource.ID_KEY);
      ResponseEntity<StandardApiResponse> response = this.getService.getInstance(prevDispatchId,
          LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      if (response.getStatusCode() == HttpStatus.OK) {
        Map<String, String> currentEntity = ((Map<String, Object>) response.getBody().data().items()
            .get(0)).entrySet().stream()
            .collect(Collectors.toMap(
                Map.Entry::getKey,
                entry -> entry.getValue() instanceof SparqlResponseField
                    ? ((SparqlResponseField) entry.getValue()).value()
                    : ""));
        params.putAll(currentEntity);
        params.put(LifecycleResource.REMARKS_KEY, ORDER_DISPATCH_MESSAGE);
        params.remove(StringResource.ID_KEY);
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
      String currentContract = results.poll().getFieldValue(LifecycleResource.IRI_KEY);
      params.put(LifecycleResource.CONTRACT_KEY, currentContract);
      this.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_COMPLETION);
      ResponseEntity<StandardApiResponse> response = this.addService.instantiate(
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
  public ResponseEntity<StandardApiResponse> genDispatchOrDeliveryOccurrence(Map<String, Object> params,
      LifecycleEventType eventType) {
    String remarksMsg;
    String successMsgId;
    switch (eventType) {
      case LifecycleEventType.SERVICE_EXECUTION:
        remarksMsg = ORDER_COMPLETE_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_COMPLETE_KEY;
        break;
      case LifecycleEventType.SERVICE_ORDER_DISPATCHED:
        remarksMsg = ORDER_DISPATCH_MESSAGE;
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_ASSIGN_KEY;
        break;
      default:
        throw new IllegalStateException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    }
    params.put(LifecycleResource.REMARKS_KEY, remarksMsg);
    this.addOccurrenceParams(params, eventType);

    // Attempt to delete any existing occurrence before any updates
    ResponseEntity<StandardApiResponse> response = this.deleteService.delete(
        eventType.getId(), params.get(StringResource.ID_KEY).toString());
    // Log responses
    LOGGER.info(response.getBody().data());
    // Ensure that the event identifier mapped directly to the jsonLd file name
    try {
      // Update the date of the task
      params.put(LifecycleResource.DATE_KEY, this.dateTimeService.getCurrentDate());
      params.put(LifecycleResource.DATE_TIME_KEY, this.dateTimeService.getCurrentDateTime());
      response = this.addService.instantiate(eventType.getId(), params);
    } catch (IllegalArgumentException exception) {
      LOGGER.error(LocalisationTranslator.getMessage(successMsgId), params.get(LifecycleResource.ORDER_KEY),
          exception.getMessage());
    }
    return response;
  }

  /**
   * Retrieves the form template for the specified event type.
   * 
   * @param eventType The target event type.
   * @param targetId  The target instance IRI.
   */
  public ResponseEntity<StandardApiResponse> getForm(LifecycleEventType eventType, String targetId) {
    // Ensure that there is a specific event type target
    String replacementQueryLine = eventType.getShaclReplacement();
    Map<String, Object> currentEntity = new HashMap<>();
    if (targetId != null) {
      LOGGER.debug("Detected specific entity ID! Retrieving target event occurrence of {}...", eventType);
      ResponseEntity<StandardApiResponse> currentEntityResponse = this.getService.getInstance(targetId, eventType);

      if (currentEntityResponse.getStatusCode() == HttpStatus.OK) {
        currentEntity = (Map<String, Object>) currentEntityResponse.getBody().data().items().get(0);
      }
    }
    return this.getService.getForm(replacementQueryLine, true, currentEntity);
  }
}