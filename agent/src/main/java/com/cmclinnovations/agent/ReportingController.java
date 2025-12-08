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
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.application.BillingService;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@RestController
@RequestMapping("/report")
public class ReportingController {
  private final ConcurrencyService concurrencyService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final GetService getService;
  private final UpdateService updateService;
  private final BillingService billingService;
  private final LifecycleTaskService lifecycleTaskService;

  private static final Logger LOGGER = LogManager.getLogger(ReportingController.class);

  public ReportingController(ConcurrencyService concurrencyService, ResponseEntityBuilder responseEntityBuilder,
      GetService getService, UpdateService updateService, BillingService billingService,
      LifecycleTaskService lifecycleTaskService) {
    this.concurrencyService = concurrencyService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.billingService = billingService;
    this.updateService = updateService;
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
    boolean isClosed = true;
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleTaskService.getOccurrenceCount(type, startTimestamp, endTimestamp, isClosed,
          allRequestParams);
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
      return this.lifecycleTaskService.getOccurrences(startTimestamp, endTimestamp, type, true,
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
          search, startTimestamp, endTimestamp, true, allRequestParams);
      return this.responseEntityBuilder.success(options);
    });
  }

  /**
   * Retrieves the form template for the pricing model for the target task if
   * available.
   */
  @GetMapping("/price/{id}")
  public ResponseEntity<StandardApiResponse<?>> getPricingForm(@PathVariable String id) {
    LOGGER.info("Received request to get the form template for pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      return this.getService.getForm(id, BillingResource.PAYMENT_OBLIGATION, false, null);
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
   * Updates the pricing model for the specified contract.
   */
  @PutMapping("/price")
  public ResponseEntity<StandardApiResponse<?>> updatePricing(@RequestBody Map<String, Object> instance) {
    LOGGER.info("Received request to update pricing model...");
    return this.concurrencyService.executeInWriteLock(BillingResource.PAYMENT_OBLIGATION, () -> {
      String targetId = instance.get(QueryResource.ID_KEY).toString();
      Map<String, Object> currentEntity = (Map<String, Object>) this.getService
          .getInstance(targetId, BillingResource.PAYMENT_OBLIGATION, false)
          .getBody().data().items().get(0);
      // Iterate through the fields to cast from SparqlResponseField to actual value
      currentEntity.forEach((key, field) -> {
        // Do not add ID or pricing model variable
        if (!key.equals(QueryResource.ID_KEY) && !key.equals(QueryResource.PRICING_MODEL_VAR.getVarName())) {
          String value = TypeCastUtils.castToObject(field, SparqlResponseField.class).value();
          instance.put(key, value);
        }
      });
      return this.updateService.update(targetId,
          BillingResource.PAYMENT_OBLIGATION, LocalisationResource.SUCCESS_UPDATE_KEY, instance);
    });
  }
}