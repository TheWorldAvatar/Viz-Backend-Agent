package com.cmclinnovations.agent;

import java.text.MessageFormat;
import java.util.HashMap;
import java.util.List;
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
import com.cmclinnovations.agent.exception.InvalidRouteException;
import com.cmclinnovations.agent.model.function.ContractOperation;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.DeleteService;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.LifecycleContractService;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@RestController
@RequestMapping("/contracts")
public class LifecycleController {
  private final ConcurrencyService concurrencyService;
  private final AddService addService;
  private final GetService getService;
  private final DeleteService deleteService;
  private final UpdateService updateService;
  private final DateTimeService dateTimeService;
  private final LifecycleContractService lifecycleContractService;
  private final LifecycleTaskService lifecycleTaskService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(LifecycleController.class);

  public LifecycleController(ConcurrencyService concurrencyService, AddService addService, GetService getService,
      DeleteService deleteService,
      UpdateService updateService, DateTimeService dateTimeService, LifecycleContractService lifecycleContractService,
      LifecycleTaskService lifecycleTaskService, ResponseEntityBuilder responseEntityBuilder) {
    this.concurrencyService = concurrencyService;
    this.addService = addService;
    this.getService = getService;
    this.deleteService = deleteService;
    this.updateService = updateService;
    this.dateTimeService = dateTimeService;
    this.lifecycleContractService = lifecycleContractService;
    this.lifecycleTaskService = lifecycleTaskService;
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
    // Methods will throw an error if they fail and no status checker is required
    // Instantiates the lifecycle first before adding schedule parameters
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        LifecycleResource.LIFECYCLE_RESOURCE, params, "The lifecycle of the contract has been successfully drafted!",
        LocalisationResource.SUCCESS_CONTRACT_DRAFT_KEY, TrackActionType.IGNORED);
    this.genContractSchedule(params);
    // Log out successful message, and return the original response
    LOGGER.info("Contract has been successfully drafted!");
    return response;
  }

  /**
   * Update the draft contract's lifecycle details in the knowledge graph.
   */
  @PutMapping("/draft")
  public ResponseEntity<StandardApiResponse<?>> updateDraftContract(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update draft contract...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.CONTRACT_KEY, () -> {
      String targetId = params.get(QueryResource.ID_KEY).toString();
      // Do not allow modifications if it has been approved
      if (this.lifecycleContractService.guardAgainstApproval(targetId)) {
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
            params, TrackActionType.IGNORED);
      }
      return scheduleResponse;
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
        // Do not allow modifications if it has been approved
        if (this.lifecycleContractService.guardAgainstApproval(contractId)) {
          LOGGER.warn("Contract {} has already been approved and will not be reset!", contractId);
          return null;
        } else {
          return this.lifecycleContractService.updateContractStatus(contractId);
        }
      };
      return this.executeIterativeContractOperation(contractIds, operation,
          "Error encountered while resetting contract for {}! Read error logs for more details",
          LocalisationResource.ERROR_RESET_PARTIAL_KEY,
          LocalisationResource.SUCCESS_CONTRACT_DRAFT_RESET_KEY);
    });
  }

  /**
   * Create an upcoming schedule for the specified contract.
   */
  @PostMapping("/schedule")
  public ResponseEntity<StandardApiResponse<?>> genContractSchedule(@RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.SCHEDULE_RESOURCE, () -> {
      LOGGER.info("Received request to generate the schedule details for contract...");
      return this.instantiateContractSchedule(params);
    });
  }

  /**
   * Update the draft schedule details in the knowledge graph.
   */
  @PutMapping("/schedule")
  public ResponseEntity<StandardApiResponse<?>> updateContractSchedule(@RequestBody Map<String, Object> params) {
    LOGGER.info("Received request to update a draft schedule...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.SCHEDULE_RESOURCE, () -> {
      this.lifecycleContractService.addStageInstanceToParams(params, LifecycleEventType.SERVICE_EXECUTION);
      String targetId = params.get(QueryResource.ID_KEY).toString();
      // delete the existing schedule assuming it is regular schedule
      ResponseEntity<StandardApiResponse<?>> deleteResponse = this.deleteService
          .delete(LifecycleResource.SCHEDULE_RESOURCE, targetId, null);
      if (deleteResponse.getStatusCode().equals(HttpStatus.OK)) {
        deleteResponse = this.deleteService.delete(LifecycleResource.FIXED_DATE_SCHEDULE_RESOURCE, targetId, null);
      }
      if (!deleteResponse.getStatusCode().equals(HttpStatus.OK)) {
        return deleteResponse;
      }
      return this.instantiateContractSchedule(params);
    });
  }

  /**
   * Instantiate a schedule instance in the knowledge graph.
   */
  private ResponseEntity<StandardApiResponse<?>> instantiateContractSchedule(Map<String, Object> params) {
    this.lifecycleContractService.addStageInstanceToParams(params, LifecycleEventType.SERVICE_EXECUTION);
    // use regular schedule or fixed date schedule
    String scheduleResource = params.containsKey(QueryResource.FIXED_DATE_SCHEDULE_KEY)
        ? LifecycleResource.FIXED_DATE_SCHEDULE_RESOURCE
        : LifecycleResource.SCHEDULE_RESOURCE;
    return this.addService.instantiate(scheduleResource,
        params, "Schedule has been successfully drafted for contract!",
        LocalisationResource.SUCCESS_SCHEDULE_DRAFT_KEY, TrackActionType.IGNORED);
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
        Map<String, Object> contractDetails = this.lifecycleContractService.getContractDetails(contractId, entityType);
        Map<String, Object> schedule = this.lifecycleContractService.getContractSchedule(contractId);
        // Include schedule details into contract as some custom contract may require
        // the details
        contractDetails.putAll(schedule);

        this.inferAndSetBranch(contractDetails);

        for (int i = 0; i < reqCopies; i++) {
          // need new copy because there are side effects
          Map<String, Object> contractDetailsCopy = new HashMap<>(contractDetails);
          Map<String, Object> scheduleCopy = new HashMap<>(schedule);
          this.cloneDraftContract(entityType, contractDetailsCopy, scheduleCopy);
        }
        return null;
      };
      return this.executeIterativeContractOperation(contractIds, operation,
          "Error encountered while copying contract for {}! Read error logs for more details",
          LocalisationResource.ERROR_COPY_DRAFT_PARTIAL_KEY,
          LocalisationResource.SUCCESS_CONTRACT_DRAFT_COPY_KEY);
    });
  }

  private void cloneDraftContract(String entityType, Map<String, Object> contractDetails,
      Map<String, Object> draftDetails) {
    // Generate new contract details from existing contract
    StandardApiResponse<?> response = this.addService.instantiate(entityType, contractDetails, TrackActionType.IGNORED)
        .getBody();
    // Generate the params to be sent to the draft route
    // ID should be side effect of instantiate
    draftDetails.put(QueryResource.ID_KEY, contractDetails.get(QueryResource.ID_KEY));
    draftDetails.put(LifecycleResource.CONTRACT_KEY, response.data().id());
    this.execGenContractLifecycle(draftDetails);
  }

  /**
   * Infer the branch name used for this job based on the presence of
   * branch-specific fields and add it to the contract details.
   * 
   * TODO: Future improvement - This method uses hardcoded field checks to
   * determine
   * branch names. Consider implementing a more flexible solution that:
   * 1. Reads branch definitions from SHACL configuration
   * 2. Uses branch-specific field patterns from application-form.json
   * 3. Dynamically maps service types to branch names
   * 
   * @param contractDetails The contract data retrieved from the knowledge graph.
   *                        This map will be modified to include "branch_add" key
   *                        if a branch is detected.
   */
  private void inferAndSetBranch(Map<String, Object> contractDetails) {
    String branchName = null;

    Object wasteCategory = contractDetails.get("waste_category");
    Object bin = contractDetails.get("bin");
    Object truck = contractDetails.get("truck");

    if (wasteCategory != null && !wasteCategory.toString().isEmpty()) {
      branchName = "Waste Collection Service";
    } else if (bin != null && !bin.toString().isEmpty()) {
      branchName = "Bin Handling Service";
    } else if (truck != null && !truck.toString().isEmpty()) {
      branchName = "Vehicle Maintenance and Operations Service";
    } else {
      branchName = "Delivery Service";
    }

    contractDetails.put(QueryResource.ADD_BRANCH_KEY, branchName);
    LOGGER.info("Set branch to: {}", branchName);
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
    // Do not allow duplicate approval
    if (this.lifecycleContractService.guardAgainstApproval(contractId)) {
      LOGGER.warn("Contract for {} has already been approved! Skipping this iteration...", contractId);
      return this.responseEntityBuilder.success(contractId,
          LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_DUPLICATE_APPROVAL_KEY));
    }
    boolean hasError = this.lifecycleTaskService.genOrderReceivedOccurrences(contractId, null);
    if (hasError) {
      LOGGER.warn(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY));
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_ORDERS_PARTIAL_KEY),
          HttpStatus.INTERNAL_SERVER_ERROR);
    } else {
      LOGGER.info("All orders has been successfully received!");
      try {
        ResponseEntity<StandardApiResponse<?>> response = this.lifecycleTaskService.genOccurrence(params,
            LifecycleEventType.APPROVED,
            MessageFormat.format("Contract {0} has been approved for service execution!", contractId),
            LocalisationResource.SUCCESS_CONTRACT_APPROVED_KEY);
        if (response.getStatusCode() == HttpStatus.OK) {
          this.lifecycleContractService.logContractActivity(contractId, TrackActionType.APPROVED);
        }
        return response;
      } catch (IllegalStateException e) {
        LOGGER.warn("Something went wrong with instantiating the approve event for {}!", contractId);
        throw e;
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
      return this.lifecycleTaskService.genDispatchOrDeliveryOccurrence(params, eventType);
    });
  }

  /**
   * Route to perform a service action on a specific service. Valid types include:
   * 1) report: Reports any unfulfilled service delivery
   * 2) cancel: Cancel any upcoming service
   */
  @PostMapping("/service/{type}")
  public ResponseEntity<StandardApiResponse<?>> performServiceAction(@PathVariable String type,
      @RequestBody Map<String, Object> params) {
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
      this.checkMissingParams(params, LifecycleResource.DATE_KEY);
      return this.lifecycleTaskService.performSingleServiceAction(type, params);
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
      return this.lifecycleTaskService.continueTaskOnNextWorkingDay(taskId, contractId);
    });
  }

  /**
   * Rescind or terminate the ongoing contract specified.
   */
  @PostMapping("/archive/{action}")
  public ResponseEntity<StandardApiResponse<?>> rescindOrTerminateContract(@PathVariable String action,
      @RequestBody Map<String, Object> params) {
    final record ServiceActionParams(String logSuccess, String messageSuccess, LifecycleEventType eventType) {
    }
    this.checkMissingParams(params, LifecycleResource.CONTRACT_KEY);
    String contractId = params.get(LifecycleResource.CONTRACT_KEY).toString();
    ServiceActionParams serviceActionParams = switch (action) {
      case "rescind" ->
        new ServiceActionParams("Contract has been successfully rescinded!",
            LocalisationResource.SUCCESS_CONTRACT_RESCIND_KEY, LifecycleEventType.ARCHIVE_RESCINDMENT);
      case "terminate" -> new ServiceActionParams("Contract has been successfully terminated!",
          LocalisationResource.SUCCESS_CONTRACT_TERMINATE_KEY, LifecycleEventType.ARCHIVE_TERMINATION);
      default -> throw new InvalidRouteException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_ROUTE_KEY, action));
    };
    LOGGER.info("Received request to {} the contract...", action);
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
      String entityType = params.remove(StringResource.TYPE_REQUEST_PARAM).toString();
      // get outstanding tasks. these should be reported
      ResponseEntity<StandardApiResponse<?>> reportResponse = this.updateTaskOfTerminatedContract(params,
          contractId, entityType, "report", null, null);
      if (!reportResponse.getStatusCode().equals(HttpStatus.OK)) {
        return reportResponse;
      }
      LOGGER.info("Successfully reported outstanding tasks of {} contract!", action);
      // get scheduled tasks. these should be cancelled
      String tomorrowTimeStamp = this.dateTimeService
          .getTimestampFromDate(this.dateTimeService.getFutureDate(this.dateTimeService.getCurrentDate(), 1));
      // far enough future, about 27 years
      String finalTimeStamp = this.dateTimeService
          .getTimestampFromDate(this.dateTimeService.getFutureDate(this.dateTimeService.getCurrentDate(), 10000));
      ResponseEntity<StandardApiResponse<?>> cancelResponse = this.updateTaskOfTerminatedContract(params,
          contractId, entityType, "cancel", tomorrowTimeStamp, finalTimeStamp);
      if (!cancelResponse.getStatusCode().equals(HttpStatus.OK)) {
        return cancelResponse;
      }
      LOGGER.info("Successfully cancelled scheduled tasks of {} contract!", action);
      // update contract status
      return this.lifecycleTaskService.genOccurrence(params, serviceActionParams.eventType,
          serviceActionParams.logSuccess, serviceActionParams.messageSuccess);
    });
  }

  /**
   * Fetch and update tasks of a terminated contract based on a date range and
   * action.
   */
  private ResponseEntity<StandardApiResponse<?>> updateTaskOfTerminatedContract(Map<String, Object> params,
      String contractId, String entityType, String taskAction, String startTimestamp, String endTimestamp) {

    List<String> occurrenceDates = this.lifecycleTaskService.getOccurrenceDateByContract(
        startTimestamp, endTimestamp, entityType, false, contractId);

    if (occurrenceDates != null && !occurrenceDates.isEmpty()) {
      ResponseEntity<StandardApiResponse<?>> updateResponse = this.lifecycleTaskService
          .updateTaskOfTerminatedContract(params, occurrenceDates, taskAction);

      if (!updateResponse.getStatusCode().equals(HttpStatus.OK)) {
        return updateResponse; // Early exit on failure
      }
    }

    return new ResponseEntity<>(HttpStatus.OK);
  }

  /**
   * Retrieve the status of the contract
   */
  @GetMapping("/status/{id}")
  public ResponseEntity<StandardApiResponse<?>> getContractStatus(@PathVariable String id) {
    LOGGER.info("Received request to retrieve the status for the contract: {}...", id);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleContractService.getContractStatus(id);
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
      return this.lifecycleContractService.getSchedule(id);
    });
  }

  /**
   * Retrieve the number of contracts in the target stage:
   * 1) draft - awaiting approval
   * 2) archive - expired
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
      case "archive" -> {
        LOGGER.info("Received request to retrieve number of archived contracts...");
        yield LifecycleEventType.ARCHIVE_COMPLETION;
      }
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleContractService.getContractCount(type, eventType, allRequestParams);
    });
  }

  /**
   * Retrieve the number of active contracts.
   */
  @GetMapping("/service/count")
  public ResponseEntity<StandardApiResponse<?>> getActiveContractCount(
      @RequestParam Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    LOGGER.info("Received request to retrieve number of contracts in progress...");
    LifecycleEventType eventType = LifecycleEventType.ACTIVE_SERVICE;
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleContractService.getContractCount(type, eventType, allRequestParams);
    });
  }

  /**
   * Retrieve the filter options for the draft contracts.
   */
  @GetMapping("/draft/filter")
  public ResponseEntity<StandardApiResponse<?>> getDraftContractFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for draft contracts...");
    String[] filterOptionParams = this.getFilterOptionParams(allRequestParams);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleContractService.getFilterOptions(filterOptionParams[0],
          filterOptionParams[1], filterOptionParams[2], LifecycleEventType.APPROVED, allRequestParams);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Retrieve the filter options for the active contracts.
   */
  @GetMapping("/service/filter")
  public ResponseEntity<StandardApiResponse<?>> getActiveContractFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for contracts in progress...");
    String[] filterOptionParams = this.getFilterOptionParams(allRequestParams);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleContractService.getFilterOptions(filterOptionParams[0],
          filterOptionParams[1], filterOptionParams[2], LifecycleEventType.ACTIVE_SERVICE, allRequestParams);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Retrieve the filter options for the archived contracts.
   */
  @GetMapping("/archive/filter")
  public ResponseEntity<StandardApiResponse<?>> getArchivedContractFilters(
      @RequestParam Map<String, String> allRequestParams) {
    String[] filterOptionParams = this.getFilterOptionParams(allRequestParams);
    LOGGER.info("Received request to retrieve filter options for archived contracts...");
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleContractService.getFilterOptions(filterOptionParams[0],
          filterOptionParams[1], filterOptionParams[2], LifecycleEventType.ARCHIVE_COMPLETION, allRequestParams);
      return this.responseEntityBuilder.success(options);
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
        yield LifecycleEventType.ACTIVE_SERVICE;
      }
      case "archive" -> {
        LOGGER.info("Received request to retrieve archived contracts...");
        yield LifecycleEventType.ARCHIVE_COMPLETION;
      }
      default -> throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_EVENT_TYPE_KEY));
    };
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      return this.lifecycleContractService.getContracts(type, label, eventType,
          new PaginationState(page, limit, sortBy, true, allRequestParams));
    });
  }

  /**
   * Retrieve the number of outstanding tasks.
   */
  @GetMapping("/service/outstanding/count")
  public ResponseEntity<StandardApiResponse<?>> getOutstandingTaskCount(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve number of outstanding tasks...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleTaskService.getOccurrenceCount(type, null, null, false, false, allRequestParams);
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
      return this.lifecycleTaskService.getOccurrences(null, null, type, false, false,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieve the filter options for outstanding task contracts.
   */
  @GetMapping("/service/outstanding/filter")
  public ResponseEntity<StandardApiResponse<?>> getAllOutstandingTaskFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for contracts in progress...");
    String[] filterOptionParams = this.getFilterOptionParams(allRequestParams);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleTaskService.getFilterOptions(filterOptionParams[0], filterOptionParams[1],
          filterOptionParams[2], null, null, false, false, allRequestParams);
      return this.responseEntityBuilder.success(options);
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
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
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
      return this.lifecycleTaskService.getOccurrenceCount(type, startTimestamp, endTimestamp, isClosed, false,
          allRequestParams);
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
      return this.lifecycleTaskService.getOccurrences(startTimestamp, endTimestamp, type, false, false,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
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
      return this.lifecycleTaskService.getOccurrences(startTimestamp, endTimestamp, type, true, false,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieve the filter options for the following types of task:
   * 
   * 1) scheduled - upcoming tasks on the specified date(s)
   * 2) closed - completed or problematic tasks on the specified date(s)
   */
  @GetMapping("/service/{task}/filter")
  public ResponseEntity<StandardApiResponse<?>> getScheduledOrClosedTaskFilters(
      @PathVariable(name = "task") String taskType,
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for contracts in progress...");
    String[] filterOptionParams = this.getFilterOptionParams(allRequestParams);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleTaskService.getFilterOptions(filterOptionParams[0], filterOptionParams[1],
          filterOptionParams[2], startTimestamp, endTimestamp, taskType.equals("closed"), false, allRequestParams);
      return this.responseEntityBuilder.success(options);
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
      return this.lifecycleTaskService.getOccurrences(contract, type,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieve the details for the specific task.
   */
  @GetMapping("/task/{id}")
  public ResponseEntity<StandardApiResponse<?>> getTask(@PathVariable String id) {
    LOGGER.info("Received request to retrieve outstanding tasks...");
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE,
        () -> this.lifecycleTaskService.getTask(id));

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
          occurrenceId = this.lifecycleTaskService.getPreviousOccurrence(id, orderTypeParams.eventType);
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

  /**
   * Retrieves the default filter option parameters fom the request parameters.
   * Remove the default ones for the route, while only retaining the filter
   * values.
   * 
   * @param allRequestParams All request parameters given to the agent.
   */
  private String[] getFilterOptionParams(Map<String, String> allRequestParams) {
    // Extract non-filter related request parameters directly, and remove them so
    // that the mappings only contain filters
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String field = allRequestParams.remove(StringResource.FIELD_REQUEST_PARAM);
    String search = allRequestParams.getOrDefault(StringResource.SEARCH_REQUEST_PARAM, "");
    allRequestParams.remove(StringResource.SEARCH_REQUEST_PARAM);
    return new String[] { type, field, search };
  }
}