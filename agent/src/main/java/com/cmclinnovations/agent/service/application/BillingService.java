package com.cmclinnovations.agent.service.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.InvoiceLine;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.UpdateService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class BillingService {
  private final AddService addService;
  private final UpdateService updateService;
  final DateTimeService dateTimeService;
  public final LifecycleQueryService lifecycleQueryService;

  static final Logger LOGGER = LogManager.getLogger(BillingService.class);

  /**
   * Constructs a new service with the following dependencies.
   */
  public BillingService(AddService addService, UpdateService updateService, DateTimeService dateTimeService,
      LifecycleQueryService lifecycleQueryService) {
    this.addService = addService;
    this.updateService = updateService;
    this.dateTimeService = dateTimeService;
    this.lifecycleQueryService = lifecycleQueryService;
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
   * Assigns a pricing plan to the target contract. Has a side effect of creating
   * a transaction record for the contract.
   * 
   * @param instance Request parameters containing the IRI for pricing model and
   *                 the contract ID.
   */
  public ResponseEntity<StandardApiResponse<?>> assignPricingPlanToContract(Map<String, Object> instance) {
    // Query for the contract IRI from the contract ID
    String contractId = TypeCastUtils.castToObject(instance.get(QueryResource.ID_KEY), String.class);
    SparqlBinding contract = this.lifecycleQueryService.getInstance(FileService.CONTRACT_QUERY_RESOURCE, contractId);
    // Query for the account IRI from the pricing model's IRI
    String pricingModel = TypeCastUtils.castToObject(instance.get(QueryResource.PRICING_MODEL_VAR.getVarName()),
        String.class);
    SparqlBinding accountInstance = this.lifecycleQueryService
        .getInstance(FileService.ACCOUNT_PRICING_QUERY_RESOURCE, pricingModel);
    instance.put(QueryResource.ACCOUNT_ID_KEY, accountInstance.getFieldValue(QueryResource.IRI_KEY));
    instance.put(LifecycleResource.CONTRACT_KEY, contract.getFieldValue(QueryResource.IRI_KEY));
    return this.updateService.update(contractId, BillingResource.TRANSACTION_RECORD_RESOURCE, null, instance, TrackActionType.IGNORED);
  }

  /**
   * Checks if a pricing model has been assigned to the specified contract.
   * 
   * @param id Contract ID.
   */
  public boolean getHasContractPricingModel(String id) {
    Queue<SparqlBinding> instance = this.lifecycleQueryService.getInstances(FileService.CONTRACT_PRICING_QUERY_RESOURCE,
        id);
    return instance.size() == 1;
  }

  /**
   * Creates an instance for the invoice and individual transaction with the
   * required details.
   * 
   * @param resourceId Resource should either be generic or nonbillable
   *                   transaction.
   * @param instance   Request parameters containing the invoice parameters.
   */
  public ResponseEntity<StandardApiResponse<?>> genInvoiceInstance(String resourceId, Map<String, Object> instance) {
    return this.addService.instantiate(resourceId, instance, TrackActionType.CREATION);
  }

  /**
   * Retrieves the bill for the specific task.
   * 
   * @param id Target task ID.
   */
  public Map<String, Object> getBill(String id) {
    Queue<SparqlBinding> billItemsInstances = this.lifecycleQueryService.getInstances(
        FileService.ACCOUNT_BILL_QUERY_RESOURCE, id);
    Map<String, Object> billItems = new HashMap<>();
    while (!billItemsInstances.isEmpty()) {
      SparqlBinding currentBillItem = billItemsInstances.poll();
      billItems.putIfAbsent(BillingResource.PRICE_KEY, currentBillItem.getFieldValue(BillingResource.PRICE_KEY));
      billItems.putIfAbsent(BillingResource.AMOUNT_KEY, currentBillItem.getFieldValue(BillingResource.AMOUNT_KEY));
      if (currentBillItem.containsField(BillingResource.CHARGE_KEY)
          || currentBillItem.containsField(BillingResource.DISCOUNT_KEY)) {
        String chargeType = currentBillItem.containsField(BillingResource.CHARGE_KEY) ? BillingResource.CHARGE_KEY
            : BillingResource.DISCOUNT_KEY;
        // List in map should be updated in place, and type cast may create a copy that
        // overwrites this behavior
        List<InvoiceLine> chargesLines = (List<InvoiceLine>) billItems.computeIfAbsent(chargeType,
            k -> new ArrayList<>());
        InvoiceLine line = new InvoiceLine(currentBillItem.getFieldValue(chargeType),
            currentBillItem.getFieldValue(ShaclResource.DESCRIPTION_PROPERTY));
        chargesLines.add(line);
      }
    }
    return billItems;
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