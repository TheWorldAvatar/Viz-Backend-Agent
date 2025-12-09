package com.cmclinnovations.agent.service.application;

import java.util.HashMap;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.AddService;
import com.cmclinnovations.agent.service.core.DateTimeService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class BillingService {
  private final AddService addService;
  final DateTimeService dateTimeService;
  public final LifecycleQueryService lifecycleQueryService;

  static final Logger LOGGER = LogManager.getLogger(BillingService.class);

  /**
   * Constructs a new service with the following dependencies.
   */
  public BillingService(AddService addService, DateTimeService dateTimeService,
      LifecycleQueryService lifecycleQueryService) {
    this.addService = addService;
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
    return this.addService.instantiate(BillingResource.TRANSACTION_RECORD_RESOURCE, instance);
  }

  /**
   * Reusable code to add custom instance.
   * 
   * @param replacements The replacement fields for the JSON-LD.
   */
  private ResponseEntity<StandardApiResponse<?>> addCustomInstance(Map<String, Object> replacements) {
    // Instantiate the customer details based on the custom resource ID first
    String type = TypeCastUtils.castToObject(replacements.remove(StringResource.TYPE_REQUEST_PARAM), String.class);
    return this.addService.instantiate(type, replacements);
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
    return this.addService.instantiate(resource, accountParams);
  }
}