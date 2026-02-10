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
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.application.BillingService;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;
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
  private final LifecycleTaskService lifecycleTaskService;

  private static final Logger LOGGER = LogManager.getLogger(ReportingController.class);

  private final boolean IS_CLOSED = true;
  private final boolean IS_BILLING = true;

  public ReportingController(ConcurrencyService concurrencyService, ResponseEntityBuilder responseEntityBuilder,
      GetService getService, BillingService billingService, LifecycleTaskService lifecycleTaskService) {
    this.concurrencyService = concurrencyService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.billingService = billingService;
    this.getService = getService;
    this.lifecycleTaskService = lifecycleTaskService;
  }

  /**
   * Retrieve the count of all closed tasks for the specified date range in UNIX
   * timestamp.
   */
  @GetMapping("/bill/count")
  public ResponseEntity<StandardApiResponse<?>> getClosedTaskCount(
      @RequestParam Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    LOGGER.info("Received request to retrieve number of scheduled contract task...");
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleTaskService.getOccurrenceCount(type, startTimestamp, endTimestamp, this.IS_CLOSED,
          this.IS_BILLING, allRequestParams);
    });
  }

  /**
   * Retrieve all closed tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/bill")
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
      return this.lifecycleTaskService.getOccurrences(startTimestamp, endTimestamp, type, this.IS_CLOSED,
          this.IS_BILLING,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieve the filter options for the completed or problematic tasks on the
   * specified date(s).
   */
  @GetMapping("/bill/filter")
  public ResponseEntity<StandardApiResponse<?>> getClosedTaskFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for contracts in progress...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String field = allRequestParams.remove(StringResource.FIELD_REQUEST_PARAM);
    String search = allRequestParams.getOrDefault(StringResource.SEARCH_REQUEST_PARAM, "");
    allRequestParams.remove(StringResource.SEARCH_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleTaskService.getFilterOptions(type, field,
          search, startTimestamp, endTimestamp, this.IS_CLOSED, this.IS_BILLING, allRequestParams);
      return this.responseEntityBuilder.success(options);
    });
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
  @GetMapping("/transaction/contract/{id}")
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
  @GetMapping("/transaction/model/{id}")
  public ResponseEntity<StandardApiResponse<?>> getPricingForm(@PathVariable String id) {
    LOGGER.info("Received request to get the form template for pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      return this.getService.getForm(id, BillingResource.TRANSACTION_RECORD_RESOURCE, false, null);
    });
  }

  /**
   * Retrieves the form template for a transaction invoice.
   * If the invoice already exists, the form will be pre-filled with the invoice details.
   */
  @GetMapping("/transaction/invoice/form/{id}")
  public ResponseEntity<StandardApiResponse<?>> getTransactionInvoiceFormTemplate(@PathVariable String id) {
    LOGGER.info("Received request to get the form template for a transaction invoice...");
    return this.concurrencyService.executeInOptimisticReadLock(BillingResource.TRANSACTION_BILL_RESOURCE, () -> {
      return this.getService.getForm(id, BillingResource.TRANSACTION_BILL_RESOURCE, false, null);
    });
  }

  /**
   * Retrieves the bill for the target task.
   */
  @GetMapping("/transaction/invoice/{id}")
  public ResponseEntity<StandardApiResponse<?>> getBill(@PathVariable String id) {
    LOGGER.info("Received request to get the bill for a task...");
    return this.concurrencyService.executeInWriteLock(BillingResource.TRANSACTION_BILL_RESOURCE, () -> {
      return this.responseEntityBuilder.success(
          List.of(this.billingService.getBill(id)));
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
   * Updates the pricing model and create a transaction record for the specified
   * contract.
   */
  @PutMapping("/transaction/model")
  public ResponseEntity<StandardApiResponse<?>> assignPricingToContract(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to update pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      return this.billingService.assignPricingPlanToContract(instance);
    });
  }

  /**
   * Creates an invoice instance along with a transaction record.
   */
  @PutMapping("/transaction/invoice")
  public ResponseEntity<StandardApiResponse<?>> updateInvoice(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to update an existing invoice and transaction...");
    return this.concurrencyService.executeInWriteLock(BillingResource.TRANSACTION_BILL_RESOURCE, () -> {
      return this.billingService.updateInvoiceInstance(BillingResource.TRANSACTION_BILL_RESOURCE, instance);
    });
  }

  /**
   * Creates an invoice instance along with a transaction record for non-billable
   * transactions.
   */
  @PostMapping("/transaction/nonbillable")
  public ResponseEntity<StandardApiResponse<?>> createNonBillableInvoice(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to create a non-billable invoice and transaction...");
    return this.concurrencyService.executeInWriteLock(BillingResource.TRANSACTION_BILL_RESOURCE, () -> {
      return this.billingService.updateInvoiceInstance(BillingResource.TRANSACTION_NONBILLABLE_BILL_RESOURCE, instance);
    });
  }
}