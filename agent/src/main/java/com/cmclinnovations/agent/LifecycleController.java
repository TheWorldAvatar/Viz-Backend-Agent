package com.cmclinnovations.agent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.function.ContractOperation;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.LifecycleReportService;
import com.cmclinnovations.agent.service.application.LifecycleService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;
import com.fasterxml.jackson.databind.JsonNode;

@RestController
@RequestMapping("/contracts")
public class LifecycleController {
  private final ConcurrencyService concurrencyService;
  private final AddService addService;
  private final GetService getService;
  private final UpdateService updateService;
  private final DateTimeService dateTimeService;
  private final LifecycleService lifecycleService;
  private final LifecycleReportService lifecycleReportService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(LifecycleController.class);

  public LifecycleController(ConcurrencyService concurrencyService, AddService addService, GetService getService,
      UpdateService updateService,
      DateTimeService dateTimeService, LifecycleService lifecycleService, LifecycleReportService lifecycleReportService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.concurrencyService = concurrencyService;
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
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.execGenContractLifecycle(params);
    });
  }

  private ResponseEntity<StandardApiResponse<?>> execGenContractLifecycle(Map<String, Object> params) {
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    // Add current date into parameters
    params.put(LifecycleResource.DATE_KEY, this.dateTimeService.getCurrentDate());
    params.put(LifecycleResource.CURRENT_DATE_KEY, this.dateTimeService.getCurrentDateTime());
    params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.EVENT_PENDING_STATUS);
    LOGGER.info("Received request to generate a new lifecycle for contract <{}>...", contractId);
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        LifecycleResource.LIFECYCLE_RESOURCE,
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
    return this.concurrencyService.executeInWriteLock(LifecycleResource.SCHEDULE_RESOURCE, () -> {
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
    });
  }

  /**
   * Create a new draft contract based on the values of an existing contract
   */
  @PostMapping("/draft/copy")
  public ResponseEntity<StandardApiResponse<?>> copyContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, QueryResource.ID_KEY);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> contractIds = TypeCastUtils.castToListObject(params.get(QueryResource.ID_KEY), String.class);
      String entityType = TypeCastUtils.castToObject(params.get("type"), String.class);
      Integer reqCopies = TypeCastUtils.castToObject(params.get(LifecycleResource.SCHEDULE_RECURRENCE_KEY),
          Integer.class);
      ContractOperation operation = (contractId) -> {
        for (int i = 0; i < reqCopies; i++) {
          this.cloneDraftContract(contractId, entityType);
        }
        return null;
      };
      return this.executeIterativeContractOperation(contractIds, operation,
          "Error encountered while copying contract for {}! Read error logs for more details",
          LocalisationResource.ERROR_COPY_DRAFT_PARTIAL_KEY,
          LocalisationResource.SUCCESS_CONTRACT_DRAFT_COPY_KEY);
    });
  }

  private void cloneDraftContract(String contractId, String entityType) {
    // Retrieve current contract details for the target instance
    StandardApiResponse<?> response = this.getService.getInstance(contractId, entityType, false).getBody();
    // Generate new contract details from existing contract
    Map<String, Object> contractDetails = ((Map<String, Object>) response.data().items().get(0))
        .entrySet().stream()
        .filter((entry) -> entry.getKey() != QueryResource.ID_KEY && entry.getKey() != QueryResource.IRI_KEY)
        .collect(Collectors.toMap(
            Map.Entry::getKey,
            entry -> {
              List<SparqlResponseField> values = TypeCastUtils.castToListObject(entry.getValue(),
                  SparqlResponseField.class);
              if (values.size() == 1) {
                return values.get(0).value();
              }
              return values.stream().map(value -> value.value()).toList();
            }));
    response = this.addService.instantiate(entityType, contractDetails).getBody();
    // Generate the params to be sent to the draft route
    Map<String, Object> draftDetails = new HashMap<>();
    // New ID should be added as a side effect of instantiate
    draftDetails.put(QueryResource.ID_KEY, contractDetails.get(QueryResource.ID_KEY));
    draftDetails.put(LifecycleResource.CONTRACT_KEY, response.data().id());
    draftDetails.put(LifecycleResource.SCHEDULE_RECURRENCE_KEY, "P1D");
    draftDetails.put(LifecycleResource.SCHEDULE_START_DATE_KEY, this.dateTimeService.getCurrentDate());
    draftDetails.put(LifecycleResource.SCHEDULE_END_DATE_KEY, this.dateTimeService.getCurrentDate());
    draftDetails.put("time slot start", "00:00");
    draftDetails.put("time slot end", "23:59");
    draftDetails.put(this.dateTimeService.getCurrentDayOfWeek(), true);
    this.execGenContractLifecycle(draftDetails);
  }

  /**
   * Signal the commencement of the services for the specified contract.
   */
  @PostMapping("/service/commence")
  public ResponseEntity<StandardApiResponse<?>> commenceContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to commence the services for a contract...");
    List<String> contractIds = TypeCastUtils.castToListObject(params.get(LifecycleResource.CONTRACT_KEY), String.class);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      ContractOperation operation = (contractId) -> {
        params.put(LifecycleResource.CONTRACT_KEY, contractId);
        return this.commenceContract(contractId, params);
      };
      if (contractIds.size() == 1) {
        return operation.apply(contractIds.get(0));
      }
      return this.executeIterativeContractOperation(contractIds, operation,
          "Error encountered while commencing contract for {}! Read error logs for more details",
          LocalisationResource.ERROR_APPROVE_PARTIAL_KEY,
          LocalisationResource.SUCCESS_CONTRACT_APPROVED_KEY);
    });
  }

  private ResponseEntity<StandardApiResponse<?>> commenceContract(String contractId, Map<String, Object> params) {
    // Verify if the contract has already been approved
    String contractStatus = this.lifecycleService.getContractStatus(contractId).getBody().data().message();
    // If approved, do not allow duplicate approval
    if (!contractStatus.equals("Pending")) {
      LOGGER.warn("Contract for {} has already been approved! Skipping this iteration...", contractId);
      return this.responseEntityBuilder.success(contractId,
          LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_DUPLICATE_APPROVAL_KEY));
    }
    boolean hasError = this.lifecycleService.genOrderReceivedOccurrences(contractId);
    if (hasError) {
      LOGGER.warn(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY));
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY),
          HttpStatus.INTERNAL_SERVER_ERROR);
    } else {
      LOGGER.info("All orders has been successfully received!");
      JsonNode report = this.lifecycleReportService.genReportInstance(contractId);
      try {
        this.addService.instantiateJsonLd(report, "unknown", LocalisationResource.SUCCESS_ADD_REPORT_KEY);
      } catch (IllegalStateException e) {
        LOGGER.warn("Something went wrong with instantiating a report for {}!", contractId);
        throw e;
      }
      try {
        this.lifecycleService.addOccurrenceParams(params, LifecycleEventType.APPROVED);
        ResponseEntity<StandardApiResponse<?>> response = this.addService
            .instantiate(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE, params);
        if (response.getStatusCode() == HttpStatus.OK) {
          LOGGER.info("Contract {} has been approved for service execution!", contractId);
          return this.responseEntityBuilder
              .success(response.getBody().data().id(),
                  LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_CONTRACT_APPROVED_KEY));
        }
      } catch (IllegalStateException e) {
        LOGGER.warn("Something went wrong with instantiating the approve event for {}!", contractId);
        throw e;
      }
      return null;
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
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
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
    });
  }

  /**
   * Performs a service action for a specific service action. Valid types include:
   * 1) report: Reports any unfulfilled service delivery
   * 2) cancel: Cancel any upcoming service
   */
  @PostMapping("/service/{type}")
  public ResponseEntity<StandardApiResponse<?>> performServiceAction(@PathVariable String type,
      @RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
      String successMsgId = "";
      String resourceId = "";
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
          resourceId = LifecycleResource.CANCEL_RESOURCE;
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
          resourceId = LifecycleResource.REPORT_RESOURCE;
          break;
        default:
          throw new IllegalArgumentException(
              LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_ROUTE_KEY, type));
      }
      // Executes common code only for cancel or report route
      ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
          resourceId, params);
      if (response.getStatusCode() == HttpStatus.OK) {
        LOGGER.info(successMsgId);
        return this.responseEntityBuilder.success(response.getBody().data().id(),
            LocalisationTranslator.getMessage(successMsgId, type));
      }
      return response;
    });
  }

  /**
   * Continues the task on the next working day by generating the same details on
   * new occurrences.
   */
  @PostMapping("/service/continue")
  public ResponseEntity<StandardApiResponse<?>> continueTask(@RequestBody Map<String, Object> params) {
    String taskId = params.get(QueryResource.ID_KEY).toString();
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.continueTaskOnNextWorkingDay(taskId, contractId);
    });
  }

  /**
   * Rescind the ongoing contract specified.
   */
  @PostMapping("/archive/rescind")
  public ResponseEntity<StandardApiResponse<?>> rescindContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to rescind the contract...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
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
    });
  }

  /**
   * Terminate the ongoing contract specified.
   */
  @PostMapping("/archive/terminate")
  public ResponseEntity<StandardApiResponse<?>> terminateContract(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    LOGGER.info("Received request to terminate the contract...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
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
    });
  }

  /**
   * Reset the draft contract's status to pending.
   */
  @PutMapping("/draft/reset")
  public ResponseEntity<StandardApiResponse<?>> resetDraftContractStatus(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to reset status of draft contract...");
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    List<String> contractIds = TypeCastUtils.castToListObject(params.get(LifecycleResource.CONTRACT_KEY), String.class);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      ContractOperation operation = (contractId) -> {
        // Verify if the contract has already been approved
        String contractStatus = this.lifecycleService.getContractStatus(contractId).getBody().data().message();
        // If approved, do not allow modifications
        if (!contractStatus.equals("Pending")) {
          LOGGER.warn("Contract {} has already been approved and will not be reset!", contractId);
          return null;
        } else {
          return this.lifecycleService.updateContractStatus(contractId);
        }
      };
      return this.executeIterativeContractOperation(contractIds, operation,
          "Error encountered while resetting contract for {}! Read error logs for more details",
          LocalisationResource.ERROR_RESET_PARTIAL_KEY,
          LocalisationResource.SUCCESS_CONTRACT_DRAFT_RESET_KEY);
    });
  }

  /**
   * Update the draft contract's lifecycle details in the knowledge graph.
   */
  @PutMapping("/draft")
  public ResponseEntity<StandardApiResponse<?>> updateDraftContract(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update draft contract...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      String targetId = params.get(QueryResource.ID_KEY).toString();
      // Verify if the contract has already been approved
      String contractStatus = this.lifecycleService.getContractStatus(targetId).getBody().data().message();
      // If approved, do not allow modifications
      if (!contractStatus.equals("Pending")) {
        return this.responseEntityBuilder.error(
            LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_APPROVED_NO_ACTION_KEY),
            HttpStatus.CONFLICT);
      }
      // Add current date into parameters
      params.put(LifecycleResource.DATE_KEY, this.dateTimeService.getCurrentDate());
      params.put(LifecycleResource.CURRENT_DATE_KEY, this.dateTimeService.getCurrentDateTime());
      params.put(LifecycleResource.EVENT_STATUS_KEY, LifecycleResource.EVENT_AMENDED_STATUS);
      ResponseEntity<StandardApiResponse<?>> scheduleResponse = this.updateContractSchedule(params);
      if (scheduleResponse.getStatusCode() == HttpStatus.OK) {
        return this.updateService.update(
            targetId, LifecycleResource.LIFECYCLE_RESOURCE, LocalisationResource.SUCCESS_CONTRACT_DRAFT_UPDATE_KEY,
            params);
      }
      return scheduleResponse;
    });
  }

  /**
   * Update the draft schedule details in the knowledge graph.
   */
  @PutMapping("/schedule")
  public ResponseEntity<StandardApiResponse<?>> updateContractSchedule(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update a draft schedule...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.SCHEDULE_RESOURCE, () -> {
      this.lifecycleService.addStageInstanceToParams(params, LifecycleEventType.SERVICE_EXECUTION);
      String targetId = params.get(QueryResource.ID_KEY).toString();
      return this.updateService.update(targetId, LifecycleResource.SCHEDULE_RESOURCE,
          LocalisationResource.SUCCESS_SCHEDULE_DRAFT_UPDATE_KEY, params);
    });
  }

  /**
   * Retrieve the status of the contract
   */
  @GetMapping("/status/{id}")
  public ResponseEntity<StandardApiResponse<?>> getContractStatus(@PathVariable String id) {
    LOGGER.info("Received request to retrieve the status for the contract: {}...", id);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleService.getContractStatus(id);
    });
  }

  /**
   * Retrieve the schedule details for the specified contract to populate the form
   * template
   */
  @GetMapping("/schedule/{id}")
  public ResponseEntity<Map<String, Object>> getSchedule(@PathVariable String id) {
    LOGGER.info("Received request to retrieve the schedule for the contract: {}...", id);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.SCHEDULE_RESOURCE, () -> {
      return this.lifecycleService.getSchedule(id);
    });
  }

  /**
   * Retrieve the number of contracts in the target stage:
   * 1) draft - awaiting approval
   * 2) service - active and in progress
   * 3) archive - expired
   */
  @GetMapping("/{stage}/count")
  public ResponseEntity<StandardApiResponse<?>> getContractCount(
      @PathVariable String stage,
      @RequestParam Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    LifecycleEventType eventType = switch (stage.toLowerCase()) {
      case "draft" -> {
        LOGGER.info("Received request to retrieve number of draft contracts...");
        yield LifecycleEventType.APPROVED;
      }
      case "service" -> {
        LOGGER.info("Received request to retrieve number of contracts in progress...");
        yield LifecycleEventType.SERVICE_EXECUTION;
      }
      case "archive" -> {
        LOGGER.info("Received request to retrieve number of archived contracts...");
        yield LifecycleEventType.ARCHIVE_COMPLETION;
      }
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleService.getContractCount(type, eventType, allRequestParams);
    });
  }

  /**
   * Retrieve all contracts in the target stage:
   * 1) draft - awaiting approval
   * 2) service - active and in progress
   * 3) archive - expired
   */
  @GetMapping("/{stage}")
  public ResponseEntity<StandardApiResponse<?>> getContracts(
      @PathVariable String stage,
      @RequestParam Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    boolean label = allRequestParams.getOrDefault(StringResource.LABEL_REQUEST_PARAM, "no").equals("yes");
    allRequestParams.remove(StringResource.LABEL_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    LifecycleEventType eventType = switch (stage.toLowerCase()) {
      case "draft" -> {
        LOGGER.info("Received request to retrieve draft contracts...");
        yield LifecycleEventType.APPROVED;
      }
      case "service" -> {
        LOGGER.info("Received request to retrieve contracts in progress...");
        yield LifecycleEventType.SERVICE_EXECUTION;
      }
      case "archive" -> {
        LOGGER.info("Received request to retrieve archived contracts...");
        yield LifecycleEventType.ARCHIVE_COMPLETION;
      }
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleService.getContracts(type, label, eventType,
          new PaginationState(page, limit, sortBy, allRequestParams));
    });
  }

  /**
   * Retrieve the number of outstanding tasks.
   */
  @GetMapping("/service/outstanding/count")
  public ResponseEntity<StandardApiResponse<?>> getOutstandingTaskCount(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve number of outstanding tasks...");
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrenceCount(null, null, false, allRequestParams);
    });
  }

  /**
   * Retrieve all outstanding tasks.
   */
  @GetMapping("/service/outstanding")
  public ResponseEntity<StandardApiResponse<?>> getAllOutstandingTasks(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve outstanding tasks...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrences(null, null, type, false,

          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, allRequestParams));
    });
  }

  /**
   * Retrieve the number of tasks at the following event stage:
   * 
   * 1) scheduled - upcoming tasks on the specified date(s)
   * 2) closed - completed or problematic tasks on the specified date(s)
   */
  @GetMapping("/service/{task}/count")
  public ResponseEntity<StandardApiResponse<?>> getScheduledOrClosedTaskCount(
      @PathVariable(name = "task") String taskType,
      @RequestParam Map<String, String> allRequestParams) {
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    boolean isClosed = switch (taskType.toLowerCase()) {
      case "scheduled" -> {
        LOGGER.info("Received request to retrieve number of scheduled contract task...");
        yield false;
      }
      case "closed" -> {
        LOGGER.info("Received request to retrieve number of closed contract task...");
        yield true;
      }
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrenceCount(startTimestamp, endTimestamp, isClosed, allRequestParams);
    });
  }

  /**
   * Retrieve all scheduled tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/service/scheduled")
  public ResponseEntity<StandardApiResponse<?>> getAllScheduledTasks(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve scheduled tasks for the specified dates...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrences(startTimestamp, endTimestamp, type, false,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, allRequestParams));
    });
  }

  /**
   * Retrieve all closed tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/service/closed")
  public ResponseEntity<StandardApiResponse<?>> getAllClosedTasks(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve closed tasks for the specified dates...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrences(startTimestamp, endTimestamp, type, true,
          new PaginationState(page, limit, sortBy, allRequestParams));
    });
  }

  /**
   * Retrieve all tasks for the specified contract.
   */
  @GetMapping("/service/{id}")
  public ResponseEntity<StandardApiResponse<?>> getAllTasksForTargetContract(
      @PathVariable(name = "id") String contract,
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve services in progress for a specified contract...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.get(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleService.getOccurrences(contract, type,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, allRequestParams));
    });
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
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
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
    });
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

  /**
   * Executes an operation across all contract IDs.
   * 
   * @param contractIds             List of target all contract IDs.
   * @param singleContractOperation Operation on each contract.
   * @param partialErrorWarning     Warning message for each operation failure.
   * @param partialErrorKey         Localisation message key for operation failure
   *                                on any contract.
   * @param successMessageKey       Localisation message key for successful
   *                                operations on all contracts.
   */
  private ResponseEntity<StandardApiResponse<?>> executeIterativeContractOperation(
      List<String> contractIds,
      ContractOperation singleContractOperation,
      String partialErrorWarning,
      String partialErrorKey,
      String successMessageKey) {
    boolean hasError = false;
    for (String contractId : contractIds) {
      try {
        singleContractOperation.apply(contractId);
      } catch (IllegalArgumentException e) {
        LOGGER.error(partialErrorWarning, contractId);
        hasError = true;
      }
    }
    if (hasError) {
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(partialErrorKey),
          HttpStatus.INTERNAL_SERVER_ERROR);
    }

    return this.responseEntityBuilder
        .success("contract", LocalisationTranslator.getMessage(successMessageKey));
  }
}