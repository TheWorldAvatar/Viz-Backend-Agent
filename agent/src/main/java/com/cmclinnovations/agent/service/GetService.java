package com.cmclinnovations.agent.service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.fasterxml.jackson.databind.node.ArrayNode;

@Service
public class GetService {
  private final KGService kgService;
  private final QueryTemplateService queryTemplateService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final String SUCCESSFUL_REQUEST_MSG = "Request has been completed successfully!";
  private static final Logger LOGGER = LogManager.getLogger(GetService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param kgService             KG service for performing the query.
   * @param queryTemplateService  Service for generating query templates.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public GetService(KGService kgService, QueryTemplateService queryTemplateService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Retrieve all the target instances and their information from the query input.
   * 
   * @param query Query for execution.
   */
  public Queue<SparqlBinding> getInstances(String query) {
    return this.kgService.query(query, SparqlEndpointType.BLAZEGRAPH);
  }

  /**
   * Retrieve all the target instances and their information. This method can also
   * retrieve instances associated with a specific parent instance if declared.
   * 
   * @param resourceID   The target resource identifier for the instance
   *                     class.
   * @param parentField  Optional parent field containing its id and name.
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   */
  public Queue<SparqlBinding> getInstances(String resourceID, ParentField parentField, String targetId,
      String addQueryStatements, boolean requireLabel, Map<String, List<Integer>> addVars) {
    LOGGER.debug("Retrieving all instances of {} ...", resourceID);
    if (requireLabel) {
      // Parent related parameters should be disabled
      parentField = null;
    }
    String query = this.queryTemplateService.getShaclQuery(resourceID, requireLabel);
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService.queryNestedPredicates(query);
    return this.getInstances(nestedVariablesAndPropertyPaths, targetId, parentField, addQueryStatements, addVars);
  }

  /**
   * Retrieve only the specific instance based on the query. The query must have
   * iri as its variable.
   * 
   * @param query Query for execution.
   */
  public SparqlBinding getInstance(String query) {
    LOGGER.debug("Retrieving an instance...");
    Queue<SparqlBinding> results = this.kgService.query(query, SparqlEndpointType.BLAZEGRAPH);
    if (results.size() > 1) {
      // When there is more than one results, verify if they can be grouped
      // as results might contain an array of different values for the same instance
      String firstId = results.peek().getFieldValue(QueryResource.IRI_KEY);
      boolean isGroup = results.stream().allMatch(binding -> {
        String currentId = binding.getFieldValue(QueryResource.IRI_KEY);
        return currentId != null && currentId.equals(firstId);
      });
      if (!isGroup) {
        LOGGER.error("Detected multiple instances: Data model is invalid!");
        throw new IllegalStateException("Detected multiple instances: Data model is invalid!");
      }
      // Removes the first instance from results as the core instance
      SparqlBinding firstInstance = results.poll();
      // Iterate over each result binding to append arrays if required
      results.stream().forEach(result -> {
        firstInstance.addFieldArray(result, new HashMap<>());
      });
      return firstInstance;
    }
    if (results.size() == 1) {
      return results.poll();
    }
    if (results.isEmpty()) {
      LOGGER.error("No valid instance found!");
      throw new NullPointerException("No valid instance found!");
    }
    LOGGER.error("Data model is invalid!");
    throw new IllegalStateException("Data model is invalid!");
  }

  /**
   * Retrieve only the specific instance and its information. This overloaded
   * method will retrieve the replacement value required from the resource ID.
   * 
   * @param targetId     The target instance IRI.
   * @param resourceID   The target resource identifier for the instance class.
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   */
  public ResponseEntity<StandardApiResponse<?>> getInstance(String targetId, String resourceID, boolean requireLabel) {
    LOGGER.debug("Retrieving an instance of {} ...", resourceID);
    String query = this.queryTemplateService.getShaclQuery(resourceID, requireLabel);
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService.queryNestedPredicates(query);
    Queue<SparqlBinding> instances = this.getInstances(nestedVariablesAndPropertyPaths, targetId, null, "",
        new HashMap<>());
    return this.getSingleInstanceResponse(instances);
  }

  /**
   * Retrieve only the specific instance for an associated lifecycle event.
   * 
   * @param targetId  The target instance IRI.
   * @param eventType The target event type.
   */
  public ResponseEntity<StandardApiResponse<?>> getInstance(String targetId, LifecycleEventType eventType) {
    LOGGER.debug("Retrieving an instance ...");
    String query = this.queryTemplateService.getShaclQuery(false, eventType.getShaclReplacement());
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService.queryNestedPredicates(query);
    GraphPattern lifecycleEventPattern = GraphPatterns.tp(QueryResource.IRI_VAR,
        QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES,
        Rdf.iri(eventType.getEvent()));
    // Query for direct instances
    Queue<SparqlBinding> instances = this.getInstances(nestedVariablesAndPropertyPaths, targetId, null,
        lifecycleEventPattern.getQueryString(), new HashMap<>());
    return this.getSingleInstanceResponse(instances);
  }

  /**
   * Retrieve only the specific instance and its information. This overloaded
   * method will retrieve the replacement value required from the resource ID.
   * 
   * @param queryVarsAndPaths  The query construction requirements.
   * @param targetId           An optional field to target the query at a specific
   *                           instance.
   * @param addQueryStatements Additional query statements to be added
   * @param addVars            Optional additional variables to be included in the
   *                           query, along with their order sequence
   */
  private Queue<SparqlBinding> getInstances(Queue<Queue<SparqlBinding>> queryVarsAndPaths, String targetId,
      ParentField parentField, String addQueryStatements, Map<String, List<Integer>> addVars) {
    Queue<String> getQuery = this.queryTemplateService.genGetQuery(queryVarsAndPaths, targetId,
        parentField, addQueryStatements, addVars);
    LOGGER.debug("Querying the knowledge graph for the instances...");
    List<String> varSequence = this.queryTemplateService.getFieldSequence();
    // Query for direct instances
    Queue<SparqlBinding> instances = this.kgService.query(getQuery.poll(), SparqlEndpointType.MIXED);
    // Query for secondary instances ie instances that are subclasses of parent
    Queue<SparqlBinding> secondaryInstances = this.kgService.query(getQuery.poll(), SparqlEndpointType.BLAZEGRAPH);
    instances = this.kgService.combineBindingQueue(instances, secondaryInstances,
        this.queryTemplateService.getArrayVariables());
    // If there is a variable sequence available, add the sequence to each binding,
    if (!varSequence.isEmpty()) {
      instances.forEach(instance -> instance.addSequence(varSequence));
    }
    return instances;
  }

  /**
   * Retrieve the get queries that will be executed.
   * 
   * @param shaclReplacement The replacement value of the SHACL query target
   * @param targetId         An optional field to target the query at a specific
   *                         instance.
   * @param requireLabel     Indicates if labels should be returned
   */
  public Queue<String> getQuery(String shaclReplacement, String targetId, boolean requireLabel) {
    String query = this.queryTemplateService.getShaclQuery(requireLabel, shaclReplacement);
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService.queryNestedPredicates(query);
    return this.queryTemplateService.genGetQuery(nestedVariablesAndPropertyPaths, targetId,
        null, "", new HashMap<>());
  }

  /**
   * Retrieve the best-fit response based on the results. This method caters to
   * retrieving a single instance.
   * 
   * @param inputs The inputs for the method.
   */
  private ResponseEntity<StandardApiResponse<?>> getSingleInstanceResponse(Queue<SparqlBinding> inputs) {
    if (inputs.size() == 1) {
      return this.responseEntityBuilder.success(null, inputs.poll().get());
    } else if (inputs.isEmpty()) {
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_INSTANCE_KEY), HttpStatus.NOT_FOUND);
    } else {
      return this.responseEntityBuilder.error(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_MULTIPLE_INSTANCE_KEY),
          HttpStatus.CONFLICT);
    }
  }

  /**
   * Retrieve the form template for the target entity and its information.
   * 
   * @param resourceID    The target resource identifier for the instance class.
   * @param isReplacement Indicates if the resource ID is a replacement value
   *                      rather than a resource.
   * @param currentEntity Current default entity if available.
   */
  public ResponseEntity<StandardApiResponse<?>> getForm(String resourceID, boolean isReplacement,
      Map<String, Object> currentEntity) {
    LOGGER.debug("Retrieving the form template for {} ...", resourceID);
    String query = this.queryTemplateService.getFormQuery(resourceID, isReplacement);
    // SHACL restrictions for generating the form template always stored on a
    // blazegraph namespace
    List<String> endpoints = this.kgService.getEndpoints(SparqlEndpointType.BLAZEGRAPH);
    for (String endpoint : endpoints) {
      LOGGER.debug("Querying at the endpoint {}...", endpoint);
      // Execute the query on the current endpoint and get the result
      ArrayNode formTemplateInputs = this.kgService.queryJsonLd(query, endpoint);
      if (!formTemplateInputs.isEmpty()) {
        Map<String, Object> results = this.queryTemplateService.genFormTemplate(formTemplateInputs, currentEntity);
        LOGGER.info(SUCCESSFUL_REQUEST_MSG);
        return this.responseEntityBuilder.success(null, results);
      }
    }
    LOGGER.error(KGService.INVALID_SHACL_ERROR_MSG);
    throw new IllegalStateException(KGService.INVALID_SHACL_ERROR_MSG);
  }

  /**
   * Retrieve the metadata (IRI, label, and description) of the concept associated
   * with the target resource. This will return their current or sub-classes.
   * 
   * @param conceptClass The target class details to retrieved.
   */
  public ResponseEntity<StandardApiResponse<?>> getConceptMetadata(String conceptClass) {
    LOGGER.debug("Retrieving the instances for {} ...", conceptClass);
    String query = this.queryTemplateService.getConceptQuery(conceptClass);
    // Note that all concept metadata will never be stored in Ontop and will require
    // the special property paths
    Queue<SparqlBinding> results = this.kgService.query(query, SparqlEndpointType.BLAZEGRAPH);
    if (results.isEmpty()) {
      LOGGER.info(
          "Request has been completed successfully with no results!");
    } else {
      LOGGER.info(SUCCESSFUL_REQUEST_MSG);
    }
    return this.responseEntityBuilder.success(null,
        results.stream().map(SparqlBinding::get).toList());
  }

  /**
   * Retrieve the matching instances of the search criterias.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param criterias  All the available search criteria inputs.
   */
  public ResponseEntity<StandardApiResponse<?>> getMatchingInstances(String resourceID, Map<String, String> criterias) {
    LOGGER.debug("Retrieving the form template for {} ...", resourceID);
    String query = this.queryTemplateService.getShaclQuery(resourceID, false);
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService.queryNestedPredicates(query);
    Queue<String> searchQuery = this.queryTemplateService.genSearchQuery(nestedVariablesAndPropertyPaths, criterias);
    // Query for direct instances
    Queue<SparqlBinding> results = this.kgService.query(searchQuery.poll(), SparqlEndpointType.MIXED);
    // Query for secondary instances ie instances that are subclasses of parent
    Queue<SparqlBinding> secondaryInstances = this.kgService.query(searchQuery.poll(), SparqlEndpointType.BLAZEGRAPH);
    results.addAll(secondaryInstances);
    LOGGER.info(SUCCESSFUL_REQUEST_MSG);
    return this.responseEntityBuilder.success(
        results.stream()
            .map(binding -> binding.getFieldValue(QueryResource.IRI_KEY))
            .toList());
  }
}