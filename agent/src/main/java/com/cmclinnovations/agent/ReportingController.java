package com.cmclinnovations.agent;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.SelectOption;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.application.BillingService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.StringResource;

@RestController
@RequestMapping("/report")
public class ReportingController {
  private final ConcurrencyService concurrencyService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final GetService getService;
  private final BillingService billingService;

  private static final Logger LOGGER = LogManager.getLogger(ReportingController.class);

  public ReportingController(ConcurrencyService concurrencyService, ResponseEntityBuilder responseEntityBuilder,
      GetService getService, BillingService billingService) {
    this.concurrencyService = concurrencyService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.billingService = billingService;
    this.getService = getService;
  }

  /**
   * Retrieves the accounts as dropdown options including name and id.
   */
  @GetMapping("/account")
  public ResponseEntity<StandardApiResponse<?>> getAccounts(@RequestParam String type, @RequestParam String search) {
    LOGGER.info("Received request to get the customer accounts...");
    return this.concurrencyService.executeInOptimisticReadLock(BillingResource.CUSTOMER_ACCOUNT_RESOURCE, () -> {
      List<SelectOption> options = this.getService.getAllFilterOptions(type, search);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Verifies if the pricing model has been assigned to the contract.
   */
  @GetMapping("/contract/pricing/{id}")
  public ResponseEntity<StandardApiResponse<?>> checkHasContractPricingModel(@PathVariable String id) {
    LOGGER.info("Received request to get the customer accounts...");
    return this.concurrencyService.executeInOptimisticReadLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      boolean hasContractPricingModel = this.billingService.getHasContractPricingModel(id);
      return this.responseEntityBuilder.success(id, Boolean.toString(hasContractPricingModel));
    });
  }

  /**
   * Retrieves the form template for the pricing model for the target task if
   * available.
   */
  @GetMapping("/contract/pricing/form/{id}")
  public ResponseEntity<StandardApiResponse<?>> getPricingForm(@PathVariable String id) {
    LOGGER.info("Received request to get the form template for pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      return this.getService.getForm(id, BillingResource.CONTRACT_PRICING_RESOURCE, false, null);
    });
  }

  /**
   * Retrieves the bill for the target task.
   */
  @GetMapping("/service/charge/{id}")
  public ResponseEntity<StandardApiResponse<?>> getServiceCharges(@PathVariable String id) {
    LOGGER.info("Received request to get the service charges for a task...");
    return this.concurrencyService.executeInWriteLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.responseEntityBuilder.success(
          List.of(this.billingService.getServiceCharges(id)));
    });
  }

  /**
   * Retrieves the count of all the billable tasks associated with the target
   * account.
   */
  @GetMapping("/account/tasks/count")
  public ResponseEntity<StandardApiResponse<?>> getBillableTasksCount(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to get the number of billable tasks for an account...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.billingService.getBillableCount(type, allRequestParams);
    });
  }

  /**
   * Retrieves all the billable tasks associated with the target account.
   */
  @GetMapping("/account/tasks")
  public ResponseEntity<StandardApiResponse<?>> getBillableTasks(@RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to get all the billable tasks for an account...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.billingService.getBillableOccurrences(type,
          // Target account field will be included directly in the filter parameters
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieves the filter options for the billable tasks associated with the
   * target account.
   */
  @GetMapping("/account/tasks/filter")
  public ResponseEntity<StandardApiResponse<?>> getBillableTaskFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for billable tasks...");
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.responseEntityBuilder.success(this.billingService.getBillableFilters(allRequestParams));
    });
  }

  /**
   * Creates a customer instance, along with a new customer account.
   */
  @PostMapping("/account")
  public ResponseEntity<StandardApiResponse<?>> createCustomerAccount(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to create a new customer account...");
    return this.concurrencyService.executeInWriteLock(BillingResource.CUSTOMER_ACCOUNT_RESOURCE, () -> {
      return this.billingService.genCustomerAccountInstance(instance);
    });
  }

  /**
   * Assigns pricing models to a customer account.
   */
  @PostMapping("/account/price")
  public ResponseEntity<StandardApiResponse<?>> assignPricingPlansToAccount(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to assign pricing model to account...");
    return this.concurrencyService.executeInWriteLock(BillingResource.CUSTOMER_ACCOUNT_RESOURCE, () -> {
      return this.billingService.assignPricingPlansToAccount(instance);
    });
  }

  /**
   * Generates an invoice for the customer account.
   */
  @PostMapping("/account/invoice")
  public ResponseEntity<StandardApiResponse<?>> createInvoice(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to create a new invoice for a customer...");
    return this.concurrencyService.executeInWriteLock(BillingResource.CUSTOMER_ACCOUNT_RESOURCE, () -> {
      return this.billingService.genAccountInvoice(instance);
    });
  }

  /**
   * Updates the pricing model for the specified contract.
   */
  @PutMapping("/contract/pricing")
  public ResponseEntity<StandardApiResponse<?>> assignPricingToContract(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to update pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      return this.billingService.assignPricingPlanToContract(instance);
    });
  }
}