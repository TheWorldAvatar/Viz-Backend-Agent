package com.cmclinnovations.agent;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.LifecycleReportService;
import com.cmclinnovations.agent.service.application.LifecycleService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/contracts")
public class LifecycleController {
  private final AddService addService;
  private final GetService getService;
  private final UpdateService updateService;
  private final DateTimeService dateTimeService;
  private final LifecycleService lifecycleService;
  private final LifecycleReportService lifecycleReportService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(LifecycleController.class);

  public LifecycleController(AddService addService, GetService getService, UpdateService updateService,
      DateTimeService dateTimeService, LifecycleService lifecycleService, LifecycleReportService lifecycleReportService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.addService = addService;
    this.getService = getService;
    this.updateService = updateService;
    this.dateTimeService = dateTimeService;
    this.lifecycleService = lifecycleService;
    this.lifecycleReportService = lifecycleReportService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Create a contract lifecycle (stages and events) for the specified contract,
   * and set it in draft state ie awaiting approval.
   */
  @PostMapping("/draft")
  public ResponseEntity<StandardApiResponse<?>> genContractLifecycle(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    // Add current date into parameters
    params.put(LifecycleResource.CURRENT_DATE_KEY, this.dateTimeService.getCurrentDate());
    params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.EVENT_PENDING_STATUS);
    LOGGER.info("Received request to generate a new lifecycle for contract <{}>...", contractId);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(LifecycleResource.LIFECYCLE_RESOURCE,
        params);
    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("The lifecycle of the contract has been successfully drafted!");
      // Execute request for schedule as well
      ResponseEntity<StandardApiResponse<?>> scheduleResponse = this.genContractSchedule(params);
      if (scheduleResponse.getStatusCode() == HttpStatus.OK) {
        LOGGER.info("Contract has been successfully drafted!");
        return this.responseEntityBuilder
            .success(scheduleResponse.getBody().data().id(),
                LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_DRAFT_KEY));
      }
      return scheduleResponse;
    } else {
      return response;
    }
  }

  /**
   * Create an upcoming schedule for the specified contract.
   */
  @PostMapping("/schedule")
  public ResponseEntity<StandardApiResponse<?>> genContractSchedule(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to generate the schedule details for contract...");
    this.lifecycleService.addStageInstanceToParams(params, LifecycleEventType.SERVICE_EXECUTION);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(LifecycleResource.SCHEDULE_RESOURCE,
        params);
    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Schedule has been successfully drafted for contract!");
      return this.responseEntityBuilder
          .success(response.getBody().data().id(),
              LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_SCHEDULE_DRAFT_KEY));
    } else {
      return response;
    }
  }

  /**
   * Signal the commencement of the services for the specified contract.
   */
  @PostMapping("/service/commence")
  public ResponseEntity<StandardApiResponse<?>> commenceContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to commence the services for a contract...");
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    boolean hasError = this.lifecycleService.genOrderReceivedOccurrences(contractId);
    if (hasError) {
      throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY));
    } else {
      LOGGER.info("All orders has been successfully received!");
      JsonNode report = this.lifecycleReportService.genReportInstance(contractId);
      ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiateJsonLd(report, "unknown",
          LocalisationResource.SUCCESS_ADD_REPORT_KEY);
      if (response.getStatusCode() != HttpStatus.OK) {
        return response;
      }
      this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.APPROVED);
      response = this.addService.instantiate(
          LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
      if (response.getStatusCode() == HttpStatus.OK) {
        LOGGER.info("Contract has been approved for service execution!");
        return this.responseEntityBuilder
            .success(response.getBody().data().id(),
                LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_APPROVED_KEY));
      } else {
        return response;
      }
    }
  }

  /**
   * Updates a completed, saved, or dispatch event. Valid types include:
   * 1) dispatch: Assign dispatch details for the specified event
   * 2) saved: Saves a completed records in a pending state
   * 3) complete: Completes a specific service order
   */
  @PutMapping("/service/{type}")
  public ResponseEntity<StandardApiResponse<?>> assignDispatchDetails(@PathVariable String type,
      @RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LifecycleEventType eventType = null;
    switch (type.toLowerCase()) {
      case "complete":
        LOGGER.info("Received request to complete a service order with completion details...");
        params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS);
        eventType = LifecycleEventType.SERVICE_EXECUTION;
        break;
      case "dispatch":
        LOGGER.info("Received request to assign the dispatch details for a service order...");
        eventType = LifecycleEventType.SERVICE_ORDER_DISPATCHED;
        break;
      case "saved":
        LOGGER.info("Received request to save a service order with completion details...");
        params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.EVENT_PENDING_STATUS);
        eventType = LifecycleEventType.SERVICE_EXECUTION;
        break;
      default:
        break;
    }
    return this.lifecycleService.genDispatchOrDeliveryOccurrence(params, eventType);
  }

  /**
   * Performs a service action for a specific service action. Valid types include:
   * 1) report: Reports any unfulfilled service delivery
   * 2) cancel: Cancel any upcoming service
   */
  @PostMapping("/service/{type}")
  public ResponseEntity<StandardApiResponse<?>> performServiceAction(@PathVariable String type,
      @RequestBody Map<String, Object> params) {
    // Common check for all routes
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);

    String successMsgId = "";
    switch (type.toLowerCase()) {
      case "cancel":
        LOGGER.info("Received request to cancel the upcoming service...");
        this.checkMissingParams(params, LifecycleResource.DATE_KEY);

        // Service date selected for cancellation cannot be a past date
        if (this.dateTimeService.isFutureDate(this.dateTimeService.getCurrentDate(),
            params.get(LifecycleResource.DATE_KEY).toString())) {
          throw new IllegalArgumentException(
              LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_CANCEL_KEY));
        }
        this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.SERVICE_CANCELLATION);
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_CANCEL_KEY;
        break;
      case "report":
        LOGGER.info("Received request to report an unfulfilled service...");
        this.checkMissingParams(params, LifecycleResource.DATE_KEY);
        // Service date selected for reporting an issue cannot be a future date
        if (this.dateTimeService.isFutureDate(params.get(LifecycleResource.DATE_KEY).toString())) {
          throw new IllegalArgumentException(
              LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_REPORT_KEY));
        }
        this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.SERVICE_INCIDENT_REPORT);
        successMsgId = LocalisationResource.SUCCESS_CONTRACT_TASK_REPORT_KEY;
        break;
      default:
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_ROUTE_KEY, type));
    }
    // Executes common code only for cancel or report route
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_LINK_RESOURCE, params);
    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info(successMsgId);
      return this.responseEntityBuilder.success(response.getBody().data().id(),
          LocalisationTranslator.getMessage(successMsgId, type));
    }
    return response;
  }

  /**
   * Continues the task on the next working day by generating the same details on
   * new occurrences.
   */
  @PostMapping("/service/continue")
  public ResponseEntity<StandardApiResponse<?>> continueTask(@RequestBody Map<String, Object> params) {
    String taskId = params.get(QueryResource.ID_KEY).toString();
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    return this.lifecycleService.continueTaskOnNextWorkingDay(taskId, contractId);
  }

  /**
   * Rescind the ongoing contract specified.
   */
  @PostMapping("/archive/rescind")
  public ResponseEntity<StandardApiResponse<?>> rescindContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to rescind the contract...");
    this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_RESCINDMENT);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Contract has been successfully rescinded!");
      return this.responseEntityBuilder.success(response.getBody().data().id(),
          LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_RESCIND_KEY));
    } else {
      return response;
    }
  }

  /**
   * Terminate the ongoing contract specified.
   */
  @PostMapping("/archive/terminate")
  public ResponseEntity<StandardApiResponse<?>> terminateContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to terminate the contract...");
    this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.ARCHIVE_TERMINATION);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Contract has been successfully terminated!");
      return this.responseEntityBuilder.success(response.getBody().data().id(),
          LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_TERMINATE_KEY));
    } else {
      return response;
    }
  }

  /**
   * Reset the draft contract's status to pending.
   */
  @PutMapping("/draft/{id}")
  public ResponseEntity<StandardApiResponse<?>> resetDraftContractStatus(@PathVariable String id) {
    LOGGER.info("Received request to reset status of draft contract...");
    return this.lifecycleService.updateContractStatus(id);
  }

  /**
   * Update the draft contract's lifecycle details in the knowledge graph.
   */
  @PutMapping("/draft")
  public ResponseEntity<StandardApiResponse<?>> updateDraftContract(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update draft contract...");
    String targetId = params.get(QueryResource.ID_KEY).toString();
    // Add current date into parameters
    params.put(LifecycleResource.CURRENT_DATE_KEY, this.dateTimeService.getCurrentDate());
    params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.EVENT_AMENDED_STATUS);
    ResponseEntity<StandardApiResponse<?>> scheduleResponse = this.updateContractSchedule(params);
    if (scheduleResponse.getStatusCode() == HttpStatus.OK) {
      return this.updateService.update(
          targetId, LifecycleResource.LIFECYCLE_RESOURCE, LocalisationResource.SUCCESS_CONTRACT_DRAFT_UPDATE_KEY,
          params);
    }
    return scheduleResponse;
  }

  /**
   * Update the draft schedule details in the knowledge graph.
   */
  @PutMapping("/schedule")
  public ResponseEntity<StandardApiResponse<?>> updateContractSchedule(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update a draft schedule...");
    this.lifecycleService.addStageInstanceToParams(params, LifecycleEventType.SERVICE_EXECUTION);
    String targetId = params.get(QueryResource.ID_KEY).toString();
    return this.updateService.update(targetId, LifecycleResource.SCHEDULE_RESOURCE,
        LocalisationResource.SUCCESS_SCHEDULE_DRAFT_UPDATE_KEY, params);
  }

  /**
   * Retrieve the status of the contract
   */
  @GetMapping("/status/{id}")
  public ResponseEntity<StandardApiResponse<?>> getContractStatus(@PathVariable String id) {
    LOGGER.info("Received request to retrieve the status for the contract: {}...", id);
    return this.lifecycleService.getContractStatus(id);
  }

  /**
   * Retrieve the schedule details for the specified contract to populate the form
   * template
   */
  @GetMapping("/schedule/{id}")
  public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String id) {
    LOGGER.info("Received request to retrieve the schedule for the contract: {}...", id);
    return this.lifecycleService.getSchedule(id);
  }

  /**
   * Retrieve all draft contracts ie awaiting approval.
   */
  @GetMapping("/draft")
  public ResponseEntity<StandardApiResponse<?>> getDraftContracts(
      @RequestParam(required = true) String type,
      @RequestParam(defaultValue = "false") boolean label) {
    LOGGER.info("Received request to retrieve draft contracts...");
    return this.lifecycleService.getContracts(type, label, LifecycleEventType.APPROVED);
  }

  /**
   * Retrieve all contracts that are currently in progress.
   */
  @GetMapping("/service")
  public ResponseEntity<StandardApiResponse<?>> getInProgressContracts(
      @RequestParam(required = true) String type,
      @RequestParam(defaultValue = "false") boolean label) {
    LOGGER.info("Received request to retrieve contracts in progress...");
    return this.lifecycleService.getContracts(type, label, LifecycleEventType.SERVICE_EXECUTION);
  }

  /**
   * Retrieve all archived contracts.
   */
  @GetMapping("/archive")
  public ResponseEntity<StandardApiResponse<?>> getArchivedContracts(
      @RequestParam(required = true) String type,
      @RequestParam(defaultValue = "false") boolean label) {
    LOGGER.info("Received request to retrieve archived contracts...");
    return this.lifecycleService.getContracts(type, label, LifecycleEventType.ARCHIVE_COMPLETION);
  }

  /**
   * Retrieve all outstanding tasks.
   */
  @GetMapping("/service/outstanding")
  public ResponseEntity<StandardApiResponse<?>> getAllOutstandingTasks(
      @RequestParam(required = true) String type) {
    LOGGER.info("Received request to retrieve outstanding tasks...");
    return this.lifecycleService.getOccurrences(null, null, type, false);
  }

  /**
   * Retrieve all scheduled tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/service/scheduled")
  public ResponseEntity<StandardApiResponse<?>> getAllScheduledTasks(
      @RequestParam(required = true) String type,
      @RequestParam(required = true) String startTimestamp,
      @RequestParam(required = true) String endTimestamp) {
    LOGGER.info("Received request to retrieve scheduled tasks for the specified dates...");
    return this.lifecycleService.getOccurrences(startTimestamp, endTimestamp, type, false);
  }

  /**
   * Retrieve all closed tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/service/closed")
  public ResponseEntity<StandardApiResponse<?>> getAllClosedTasks(
      @RequestParam(required = true) String type,
      @RequestParam(required = true) String startTimestamp,
      @RequestParam(required = true) String endTimestamp) {
    LOGGER.info("Received request to retrieve closed tasks for the specified dates...");
    return this.lifecycleService.getOccurrences(startTimestamp, endTimestamp, type, true);
  }

  /**
   * Retrieve all tasks for the specified contract.
   */
  @GetMapping("/service/{id}")
  public ResponseEntity<StandardApiResponse<?>> getAllTasksForTargetContract(
      @PathVariable(name = "id") String contract,
      @RequestParam(required = true) String type) {
    LOGGER.info("Received request to retrieve services in progress for a specified contract...");
    return this.lifecycleService.getOccurrences(contract, type);
  }

  /**
   * Retrieves the form template for the specific occurrence type from the
   * knowledge graph. Valid types include:
   * 1) service stage - dispatch: Assign dispatch details to the service order
   * 2) service stage - complete: Completes a specific service order
   * 3) service stage - report: Reports an issue with the service order
   * 4) service stage - cancel: Cancels the upcoming service order
   * 5) archive stage - rescind: Rescind the entire contract
   * 6) archive stage - terminate: Terminates the entire contract
   * 
   * The id represents the specific instance if required, else default to form.
   */
  @GetMapping("/{stage}/{type}/{id}")
  public ResponseEntity<StandardApiResponse<?>> getOccurrenceForm(@PathVariable String stage,
      @PathVariable String type, @PathVariable String id) {
    // Access to this form is prefiltered on the UI and need not be enforced here
    final record OrderType(String logOrderType, LifecycleEventType eventType) {
    }
    OrderType orderTypeParams = switch (stage.toLowerCase() + ";" + type.toLowerCase()) {
      case "service;complete" -> new OrderType("for order completion details", LifecycleEventType.SERVICE_EXECUTION);
      case "service;dispatch" -> new OrderType("for order dispatch", LifecycleEventType.SERVICE_ORDER_DISPATCHED);
      case "service;report" -> new OrderType("to report the order", LifecycleEventType.SERVICE_INCIDENT_REPORT);
      case "service;cancel" -> new OrderType("to cancel the order", LifecycleEventType.SERVICE_CANCELLATION);
      case "archive;rescind" -> new OrderType("to rescind the contract", LifecycleEventType.ARCHIVE_RESCINDMENT);
      case "archive;terminate" -> new OrderType("to terminate the contract", LifecycleEventType.ARCHIVE_TERMINATION);
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    LOGGER.info("Received request to get form template {}...", orderTypeParams.logOrderType);
    String occurrenceId;
    // If the id is form, attached itself to give blank form template
    if (id.equals("form")) {
      occurrenceId = id;
      // Search for previous occurrence to retrieve
    } else {
      try {
        occurrenceId = this.lifecycleService.getPreviousOccurrence(id, orderTypeParams.eventType);
      } catch (NullPointerException e) {
        // Fail silently to give blank form template given the missing previous
        occurrenceId = "form";
      }
    }
    // Provide blank form template
    if (occurrenceId.equals("form")) {
      return this.getService.getForm(orderTypeParams.eventType.getShaclReplacement(), true);
    }
    // If there is a previous occurrence, give a prepopulated form template
    return this.getService.getForm(occurrenceId, orderTypeParams.eventType.getShaclReplacement(), true,
        orderTypeParams.eventType);
  }

  /**
   * Checks for missing field in the request parameters. Throws an error if so.
   */
  private void checkMissingParams(Map<String, Object> params, String field) {
    if (!params.containsKey(field)) {
      LOGGER.error("Missing `{}` field in request parameters!", field);
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY, field));
    }
  }
}