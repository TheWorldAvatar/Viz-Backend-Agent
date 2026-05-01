package com.cmclinnovations.agent.service.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.InvoiceLine;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class BillingService {
  private final AddService addService;
  private final UpdateService updateService;
  private final LifecycleQueryService lifecycleQueryService;
  private final LifecycleTaskService lifecycleTaskService;

  static final Logger LOGGER = LogManager.getLogger(BillingService.class);

  /**
   * Constructs a new service with the following dependencies.
   */
  public BillingService(AddService addService, UpdateService updateService, LifecycleQueryService lifecycleQueryService,
      LifecycleTaskService lifecycleTaskService) {
    this.addService = addService;
    this.updateService = updateService;
    this.lifecycleQueryService = lifecycleQueryService;
    this.lifecycleTaskService = lifecycleTaskService;
  }

  /**
   * Generates a customer account instance along with the custom customer details.
   * 
   * @param replacements The request parameters containing replacements for the
   *                     JSON-LD.
   */
  public ResponseEntity<StandardApiResponse<?>> genCustomerAccountInstance(Map<String, Object> replacements) {
    // Instantiate the customer details
    ResponseEntity<StandardApiResponse<?>> response = this.addCustomInstance(replacements);
    if (response.getStatusCode() != HttpStatus.OK) {
      LOGGER.error("Error encountered while creating a new customer! Read error logs for more details.");
      return response;
    }
    // Create new account for the customer
    Map<String, Object> accountParams = new HashMap<>();
    return this.addBillingSpecificInstance(replacements.get(QueryResource.ID_KEY), response.getBody().data().id(),
        BillingResource.CUSTOMER_ACCOUNT_RESOURCE, accountParams);
  }

  /**
   * Updates the account flag.
   * 
   * @param id The identifier for the target customer account instance.
   */
  public ResponseEntity<StandardApiResponse<?>> updateAccountFlag(String id) {
    String flag = this.lifecycleQueryService.getInstance(FileService.ACCOUNT_FLAG_QUERY_RESOURCE, id)
        .getFieldValue(BillingResource.FLAG_KEY);
    boolean isFlag = Boolean.parseBoolean(flag);
    LOGGER.info("Flag for customer account is currently: {}", isFlag);
    String query = BillingResource.getBalanceUpdateQuery(id, isFlag);
    return this.updateService.update(query);
  }

  /**
   * Assigns pricing plans to the target customer account.
   * 
   * @param instance Request parameters containing the pricing plan details and
   *                 account ID.
   */
  public ResponseEntity<StandardApiResponse<?>> assignPricingPlansToAccount(Map<String, Object> instance) {
    // Get account ID from the request to get the account specific agreement
    // Earlier setup to remove it from replacing any JSON-LD fields
    String accountId = TypeCastUtils.castToObject(instance.remove(QueryResource.ACCOUNT_ID_KEY), String.class);
    // Instantiate the pricing plan details
    ResponseEntity<StandardApiResponse<?>> response = this.addCustomInstance(instance);
    if (response.getStatusCode() != HttpStatus.OK) {
      LOGGER.error("Error encountered while creating a new pricing plan! Read error logs for more details.");
      return response;
    }
    // Get the account specific agreement IRI to link to the pricing plan
    SparqlBinding accountSpecificAgreementInstance = this.lifecycleQueryService
        .getInstance(FileService.ACCOUNT_AGREEMENT_QUERY_RESOURCE, accountId);
    Map<String, Object> accountParams = new HashMap<>();
    accountParams.put(LifecycleResource.CONTRACT_KEY,
        accountSpecificAgreementInstance.getFieldValue(QueryResource.IRI_KEY));
    return this.addBillingSpecificInstance(instance.get(QueryResource.ID_KEY), response.getBody().data().id(),
        BillingResource.CUSTOMER_ACCOUNT_PRICING_RESOURCE, accountParams);
  }

  /**
   * Assigns a pricing plan to the target contract.
   * 
   * @param instance Request parameters containing the IRI for pricing model and
   *                 the contract ID.
   */
  public ResponseEntity<StandardApiResponse<?>> assignPricingPlanToContract(Map<String, Object> instance) {
    String contractId = TypeCastUtils.castToObject(instance.get(QueryResource.ID_KEY), String.class);
    String pricingModelIri = TypeCastUtils.castToObject(instance.get(BillingResource.PRICING_KEY), String.class);
    Queue<SparqlBinding> invalidCounter = this.lifecycleQueryService.getInstances(
        FileService.VERIFY_PRICING_QUERY_RESOURCE, pricingModelIri, contractId);
    if (!invalidCounter.isEmpty()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_PRICING_KEY));
    }
    SparqlBinding contract = this.lifecycleQueryService.getInstance(FileService.CONTRACT_QUERY_RESOURCE, contractId);
    instance.put(LifecycleResource.CONTRACT_KEY, contract.getFieldValue(QueryResource.IRI_KEY));
    return this.addService.instantiate(BillingResource.CONTRACT_PRICING_RESOURCE, contractId, instance, null, null,
        TrackActionType.IGNORED);
  }

  /**
   * Updates the pricing plan for the target contract.
   * 
   * @param instance Request parameters containing the IRI for pricing model and
   *                 the contract ID.
   */
  public ResponseEntity<StandardApiResponse<?>> updatePricingPlanToContract(Map<String, Object> instance) {
    String contractId = TypeCastUtils.castToObject(instance.get(QueryResource.ID_KEY), String.class);
    SparqlBinding contract = this.lifecycleQueryService.getInstance(FileService.CONTRACT_QUERY_RESOURCE, contractId);
    instance.put(LifecycleResource.CONTRACT_KEY, contract.getFieldValue(QueryResource.IRI_KEY));
    List<Map<String, String>> pricingModels = TypeCastUtils
        .castToListObject(instance.get(BillingResource.PRICING_KEY), String.class)
        .stream()
        .filter(p -> Objects.nonNull(p) && !p.isBlank())
        .map(p -> Map.of(BillingResource.PRICING_MODEL_KEY, p))
        .collect(Collectors.toList());
    instance.put(BillingResource.PRICING_KEY, pricingModels);
    return this.updateService.update(contractId, BillingResource.CONTRACT_MULTI_PRICING_RESOURCE, null, instance,
        TrackActionType.IGNORED);
  }

  /**
   * Generates an account invoice based on the custom requirements, as well as
   * defaults for involved tasks and financial record.
   * 
   * @param instance Request parameters containing the parameters to generate the
   *                 invoice.
   */
  public ResponseEntity<StandardApiResponse<?>> genAccountInvoice(Map<String, Object> instance) {
    // Generate the custom invoice resource first
    ResponseEntity<StandardApiResponse<?>> response = this.addService.instantiate(
        BillingResource.INVOICE_RESOURCE, instance, TrackActionType.CREATION);
    // If successful, generate the agent compliant requirements
    if (response.getStatusCode() == HttpStatus.OK) {
      // Retain iri of invoice following custom requirements
      String invoiceIri = response.getBody().data().id();
      instance.put(QueryResource.IRI_KEY, invoiceIri);

      // Get and set financial record associated with the account field
      String accountId = TypeCastUtils.castToObject(instance.get(QueryResource.ACCOUNT_ID_KEY), String.class);
      SparqlBinding accountFinancialRecordInstance = this.lifecycleQueryService
          .getInstance(FileService.ACCOUNT_FINANCIAL_RECORD_QUERY_RESOURCE, accountId);
      instance.put(BillingResource.CUSTOMER_ACCOUNT_RESOURCE,
          accountFinancialRecordInstance.getFieldValue(QueryResource.IRI_KEY));

      // Retrieve all task invoices as an array
      List<String> taskIds = TypeCastUtils.castToListObject(instance.get(LifecycleResource.TASK_RESOURCE),
          String.class);
      List<Map<String, String>> taskInvoiceIris = new ArrayList<>();
      taskIds.forEach(taskId -> {
        Map<String, String> currentResults = new HashMap<>();
        SparqlBinding taskInvoiceInstance = this.lifecycleQueryService
            .getInstance(FileService.TASK_INVOICE_QUERY_RESOURCE, taskId);
        currentResults.put(BillingResource.INVOICE_RESOURCE, taskInvoiceInstance.getFieldValue(QueryResource.IRI_KEY));
        taskInvoiceIris.add(currentResults);
      });
      instance.put(LifecycleResource.TASK_RESOURCE, taskInvoiceIris);

      response = this.addService.instantiate(
          BillingResource.CUSTOMER_ACCOUNT_INVOICE_RESOURCE, instance, TrackActionType.IGNORED);
    }
    return response;
  }

  /**
   * Checks if a valid (non-expired) pricing model has been assigned to the
   * specified contract based on the target date.
   * 
   * @param id   Contract ID.
   * @param date Target date that the pricing model should be valid on.
   */
  public boolean getHasValidContractPricingModel(String id, String date) {
    Queue<SparqlBinding> instance = this.lifecycleQueryService.getInstances(FileService.CONTRACT_PRICING_QUERY_RESOURCE,
        id, date);
    return instance.size() == 1;
  }

  /**
   * Retrieves the service charges for the specific task.
   * 
   * @param id Target task ID.
   */
  public Map<String, Object> getServiceCharges(String id) {
    Queue<SparqlBinding> serviceChargesInstances = this.lifecycleQueryService.getInstances(
        FileService.ACCOUNT_BILL_QUERY_RESOURCE, id);
    Map<String, Object> serviceCharges = new HashMap<>();
    while (!serviceChargesInstances.isEmpty()) {
      SparqlBinding currentCharge = serviceChargesInstances.poll();
      serviceCharges.putIfAbsent(BillingResource.AMOUNT_KEY, currentCharge.getFieldValue(BillingResource.AMOUNT_KEY));
      if (currentCharge.containsField(BillingResource.CHARGE_KEY)
          || currentCharge.containsField(BillingResource.DISCOUNT_KEY)) {
        String chargeType = currentCharge.containsField(BillingResource.CHARGE_KEY) ? BillingResource.CHARGE_KEY
            : BillingResource.DISCOUNT_KEY;
        // List in map should be updated in place - DO NOT fix type casting, as the code
        // creates a copy that will cause it NOT to be updated in place
        List<InvoiceLine> chargesLines = (List<InvoiceLine>) serviceCharges.computeIfAbsent(chargeType,
            k -> new ArrayList<>());
        InvoiceLine line = new InvoiceLine(currentCharge.getFieldValue(chargeType),
            currentCharge.getFieldValue(ShaclResource.DESCRIPTION_PROPERTY));
        chargesLines.add(line);
      }
    }
    return serviceCharges;
  }

  /**
   * Retrieve all billable tasks for a target account.
   * 
   * @param entityType Target resource ID.
   * @param pagination Pagination state to filter results.
   * @param filters    Filters provided in the request parameters.
   */
  public ResponseEntity<StandardApiResponse<?>> getBillableOccurrences(String entityType, PaginationState pagination,
      Map<String, String> filters) {
    return this.lifecycleTaskService.getOccurrences(null, null, entityType,
        LifecycleEventType.SERVICE_ACCRUAL, pagination, filters);
  }

  /**
   * Retrieve all billable tasks related occurrences to a target account.
   * 
   * @param allRequestParams All parameters sent through the request.
   */
  public List<String> getBillableFilters(Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String field = allRequestParams.remove(StringResource.FIELD_REQUEST_PARAM);
    String search = allRequestParams.getOrDefault(StringResource.SEARCH_REQUEST_PARAM, "");
    allRequestParams.remove(StringResource.SEARCH_REQUEST_PARAM);
    return this.lifecycleTaskService.getFilterOptions(type, field,
        search, null, null, LifecycleEventType.SERVICE_ACCRUAL, allRequestParams);
  }

  /**
   * Reusable code to add custom instance.
   * 
   * @param replacements The replacement fields for the JSON-LD.
   */
  private ResponseEntity<StandardApiResponse<?>> addCustomInstance(Map<String, Object> replacements) {
    // Instantiate the customer details based on the custom resource ID first
    String type = TypeCastUtils.castToObject(replacements.remove(StringResource.TYPE_REQUEST_PARAM), String.class);
    return this.addService.instantiate(type, replacements, TrackActionType.CREATION);
  }

  /**
   * Reusable code to add billing specific instance.
   * 
   * @param idObj         The required id value as an object.
   * @param iri           The require IRI value.
   * @param resource      The resource identifier for instantiation.
   * @param accountParams The replacement fields for the JSON-LD.
   */
  private ResponseEntity<StandardApiResponse<?>> addBillingSpecificInstance(Object idObj, String iri, String resource,
      Map<String, Object> accountParams) {
    String id = TypeCastUtils.castToObject(idObj, String.class);
    accountParams.put(QueryResource.ID_KEY, id);
    accountParams.put(QueryResource.IRI_KEY, iri);
    return this.addService.instantiate(resource, accountParams, TrackActionType.IGNORED);
  }
}