package com.cmclinnovations.agent.service.core;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.jena.graph.Triple;
import org.apache.jena.rdf.model.Model;
import org.apache.jena.rdf.model.ModelFactory;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFDataMgr;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.component.ShaclRuleProcesser;
import com.cmclinnovations.agent.component.repository.KGRepository;
import com.cmclinnovations.agent.exception.InvalidRouteException;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;
import com.cmclinnovations.stack.clients.blazegraph.BlazegraphClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import uk.ac.cam.cares.jps.base.query.RemoteStoreClient;

@Service
public class KGService {
  @Value("${NAMESPACE}")
  String namespace;

  private final KGRepository kgRepository;
  private final RestClient client;
  private final ObjectMapper objectMapper;
  private final FileService fileService;
  private final LoggingService loggingService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final ShaclRuleProcesser shaclRuleProcesser;

  private static final Logger LOGGER = LogManager.getLogger(KGService.class);

  /**
   * Constructs a new service.
   * 
   * @param fileService           File service for accessing file resources.
   * @param loggingService        Service for logging statements.
   * @param responseEntityBuilder A component to build the response entity.
   * @param shaclRuleProcesser    A component to process SHACL rules.
   */
  public KGService(FileService fileService, LoggingService loggingService, KGRepository kgRepository,
      ResponseEntityBuilder responseEntityBuilder, ShaclRuleProcesser shaclRuleProcesser) {
    this.client = RestClient.create();
    this.objectMapper = new ObjectMapper();
    this.fileService = fileService;
    this.loggingService = loggingService;
    this.kgRepository = kgRepository;
    this.responseEntityBuilder = responseEntityBuilder;
    this.shaclRuleProcesser = shaclRuleProcesser;
  }

  /**
   * Add the target triples in JSON-LD format into the KG.
   * 
   * @param contents the contents to add
   */
  public ResponseEntity<String> add(String contents) {
    return this.client.post()
        .uri(BlazegraphClient.getInstance().getRemoteStoreClient(this.namespace).getQueryEndpoint())
        .accept(QueryResource.LD_JSON_MEDIA_TYPE)
        .contentType(QueryResource.LD_JSON_MEDIA_TYPE)
        .body(contents)
        .retrieve()
        .toEntity(String.class);
  }

  /**
   * Deletes the target instance and its associated properties from the KG.
   * 
   * @param query    The DELETE query for execution.
   * @param targetId The target instance IRI.
   */
  public ResponseEntity<StandardApiResponse<?>> delete(String query, String targetId) {
    LOGGER.debug("Deleting instances...");
    int statusCode = this.executeUpdate(query);
    if (statusCode == 200) {
      LOGGER.info("Instance has been successfully deleted!");
      return this.responseEntityBuilder.success(null,
          LocalisationTranslator.getMessage(LocalisationResource.SUCCESS_DELETE_KEY), true, null);
    }
    throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_DELETE_KEY));
  }

  /**
   * A method that executes a query at the specified endpoint to retrieve the
   * SPARQL results. This method does not cache the results, as it is intended to
   * retrieve dynamic data.
   * 
   * @param query    the query for execution.
   * @param endpoint the endpoint for execution.
   * 
   * @return the query results.
   */
  public Queue<SparqlBinding> query(String query, String endpoint) {
    List<SparqlBinding> results = this.kgRepository.query(query, endpoint);
    return TypeCastUtils.castListToQueue(results);
  }

  /**
   * A method that executes a federated query across available endpoints to
   * retrieve SPARQL results. This method does not cache the results, as it is
   * intended to retrieve dynamic data.
   * 
   * @param query        the query for execution.
   * @param endpointType the type of endpoint. Options include Mixed, Blazegraph,
   *                     and Ontop.
   * 
   * @return the query results.
   */
  public Queue<SparqlBinding> query(String query, SparqlEndpointType endpointType) {
    List<String> endpoints = this.getEndpoints(endpointType);
    List<SparqlBinding> results = this.kgRepository.query(query, endpoints);
    return TypeCastUtils.castListToQueue(results);
  }

  /**
   * Gets all available SPARQL endpoints (of the specified type) containing data.
   * 
   * @param endpointType The required endpoint type. Can be either mixed,
   *                     blazegraph, or ontop.
   * @return List of endpoints
   */
  public List<String> getEndpoints(SparqlEndpointType endpointType) {
    return this.kgRepository.getEndpoints(endpointType).stream()
        .map(binding -> binding.getFieldValue("endpoint"))
        .toList();
  }

  /**
   * Executes the query at the target endpoint to retrieve JSON LD results.
   * 
   * @param query the query for execution.
   * 
   * @return the query results as JSON array.
   */
  public ArrayNode queryJsonLd(String query, String endpoint) {
    this.loggingService.logQuery(query, LOGGER);
    String results = this.client.post()
        // JSON LD queries are used only for generating the form template, and thus,
        // will always be executed on the blazegraph namespace (storing the SHACL
        // restrictions)
        .uri(endpoint)
        .accept(QueryResource.LD_JSON_MEDIA_TYPE)
        .contentType(QueryResource.SPARQL_MEDIA_TYPE)
        .body(query)
        .retrieve()
        .body(String.class);
    try {
      return this.objectMapper.readValue(results, ArrayNode.class);
    } catch (JsonProcessingException e) {
      LOGGER.error(e);
      throw new IllegalArgumentException(e);
    }
  }

  /**
   * Retrieve the parameters defined by the user in SHACL to generate the SPARQL
   * query required.
   * 
   * @param shaclReplacement The replacement value of the SHACL query target
   * @param requireLabel     Indicates if labels should be returned for all the
   *                         fields that are IRIs.
   */
  public Queue<Queue<SparqlBinding>> getSparqlQueryConstructionParameters(String shaclReplacement,
      boolean requireLabel) {
    List<List<SparqlBinding>> results = this.kgRepository.execParamsConstructorQuery(shaclReplacement, requireLabel);
    return results.stream()
        .map(innerList -> new ArrayDeque<>(innerList))
        .collect(Collectors.toCollection(ArrayDeque::new));
  }

  /**
   * Retrieve optional parameters of a resource based on its corresponding SHACL.
   * 
   * @param resourceID The target resource identifier for the instance.
   */
  public Set<String> getSparqlOptionalParameters(String resourceId) {
    switch (resourceId) {
      case LifecycleResource.SCHEDULE_RESOURCE, LifecycleResource.LIFECYCLE_RESOURCE,
          LifecycleResource.FIXED_DATE_SCHEDULE_RESOURCE:
        // these are special resources where all properties are mandatory
        return Collections.emptySet();
      default:
        String target;
        try {
          target = this.fileService.getTargetIri(resourceId).getQueryString();
        } catch (InvalidRouteException e) {
          // specific handling for lifecycle event types
          LifecycleEventType eventType = LifecycleEventType.fromId(resourceId);
          if (eventType != null) {
            target = eventType.getShaclReplacement();
          } else {
            throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_DELETE_KEY));
          }
        }
        List<String> endpoints = this.getEndpoints(SparqlEndpointType.BLAZEGRAPH);
        List<SparqlBinding> results = this.kgRepository.execOptionalParamQuery(target, endpoints);
        return results.stream()
            .map(x -> x.getFieldValue("name"))
            .collect(Collectors.toSet());
    }

  }

  /**
   * Retrieves the SHACL rules associated with the target resource.
   * 
   * @param resourceID             The target resource identifier for the
   *                               instance.
   * @param isSparqlConstructRules Extract only SPARQL CONSTRUCT rules if true,
   *                               else, retrieve all other rules.
   */
  public Model getShaclRules(String resourceId, boolean isSparqlConstructRules) {
    LOGGER.debug("Retrieving SHACL rules for resource: {}", resourceId);
    String target;
    try {
      target = this.fileService.getTargetIri(resourceId).getQueryString();
    } catch (InvalidRouteException e) {
      LifecycleEventType eventType = LifecycleEventType.fromId(resourceId);
      if (eventType != null) {
        target = eventType.getShaclReplacement();
      } else {
        // If no target is specified, return an empty model
        LOGGER.warn("No target resource specified for SHACL rules retrieval. Returning an empty model.");
        return ModelFactory.createDefaultModel();
      }
    }
    String query = this.fileService.getContentsWithReplacement(FileService.SHACL_RULE_QUERY_RESOURCE, target,
        isSparqlConstructRules ? ";a <http://www.w3.org/ns/shacl#SPARQLRule>."
            : ".MINUS{?ruleShape a <http://www.w3.org/ns/shacl#SPARQLRule>}");
    List<String> endpoints = this.getEndpoints(SparqlEndpointType.BLAZEGRAPH);
    Model model = ModelFactory.createDefaultModel();
    for (String endpoint : endpoints) {
      LOGGER.debug("Querying at the endpoint {}...", endpoint);
      String results = this.client.post()
          .uri(endpoint)
          .accept(QueryResource.TTL_MEDIA_TYPE)
          .contentType(QueryResource.SPARQL_MEDIA_TYPE)
          .body(query)
          .retrieve()
          .body(String.class);
      Model resultModel = this.readStringModel(results, Lang.TURTLE);
      model.add(resultModel);
    }
    return model;
  }

  /**
   * Executes the SHACL SPARQL construct rules on all available endpoints.
   * 
   * @param rules       The target SHACL rules.
   * @param instanceIri The instance IRI string.
   */
  public void execShaclRules(Model rules, String instanceIri) {
    LOGGER.info("Executing SHACL SPARQL construct rules directly in the knowledge graph...");
    Queue<String> constructQueries = this.shaclRuleProcesser.getConstructQueries(rules);
    while (!constructQueries.isEmpty()) {
      String currentQuery = constructQueries.poll();
      // Execute a SELECT query to retrieve all possible variables and their values in
      // the WHERE clause
      String queryForExecution = this.shaclRuleProcesser.genSelectQuery(currentQuery, instanceIri);
      List<SparqlBinding> results = this.query(queryForExecution, SparqlEndpointType.MIXED).stream().collect(Collectors.toList());
      List<Triple> tripleList = this.shaclRuleProcesser.genConstructTriples(currentQuery);
      // Generate the delete where query templates
      String deleteWhereQuery = this.shaclRuleProcesser.genDeleteWhereQuery(tripleList, results);
      // Using the results of the SELECT query as replacements to the CONSTRUCT
      // clause, generate the INSERT DATA query
      String insertDataQuery = this.shaclRuleProcesser.genInsertDataQuery(tripleList, results);
      // Execute updates after the queries are generated to prevent incomplete query
      this.executeUpdate(deleteWhereQuery);
      this.executeUpdate(insertDataQuery);
    }
  }

  /**
   * Reads a string input as a Jena Model.
   * 
   * @param input   The string input to be converted.
   * @param rdfType The type of RDF data (e.g., Turtle, RDF/XML, JSON-LD).
   */
  public Model readStringModel(String input, Lang rdfType) {
    Model dataModel = ModelFactory.createDefaultModel();
    RDFDataMgr.read(dataModel, new ByteArrayInputStream(input.getBytes()), rdfType);
    return dataModel;
  }

  /**
   * Combine the array values in SparlBinding objects.
   * 
   * @param firstQueue The first target queue.
   * @param arrayVars  Mappings between each array group and their individual
   *                   fields.
   */
  public Queue<SparqlBinding> combineBindingQueue(Queue<SparqlBinding> firstQueue, Map<String, Set<String>> arrayVars) {
    if (firstQueue.isEmpty()) {
      return firstQueue;
    }
    Queue<SparqlBinding> result = new ArrayDeque<>();
    // Group them by the IRI key
    Map<String, List<SparqlBinding>> groupedBindings = firstQueue.stream()
        .collect(Collectors.groupingBy(binding -> {
          String id = binding.containsField(QueryResource.IRI_KEY)
              ? binding.getFieldValue(QueryResource.IRI_KEY)
              : binding.getFieldValue(QueryResource.ID_KEY);
          // If this is a lifecycle event occurrence, group them by date and id
          if (binding.containsField(QueryResource.genVariable(LifecycleResource.EVENT_ID_KEY).getVarName())) {
            return id + binding.getFieldValue(LifecycleResource.DATE_KEY);
          }
          return id;
        }));
    // For the same IRI, combine them using the add field array method
    groupedBindings.values().forEach(groupedBinding -> {
      if (groupedBinding.isEmpty()) {
        return;
      }
      SparqlBinding firstBinding = groupedBinding.get(0);
      if (groupedBinding.size() > 1 && !arrayVars.isEmpty()) {
        for (int i = 1; i < groupedBinding.size(); i++) {
          firstBinding.addFieldArray(groupedBinding.get(i), arrayVars);
        }
      }
      result.offer(firstBinding);
    });
    return result;
  }

  /**
   * Executes the update query at the target endpoint.
   * 
   * @param query the query for execution.
   * 
   * @return the status code.
   */
  public int executeUpdate(String query) {
    this.loggingService.logQuery(query, LOGGER);
    RemoteStoreClient kgClient = BlazegraphClient.getInstance().getRemoteStoreClient(this.namespace);
    // Execute the request
    try (CloseableHttpResponse response = kgClient.executeUpdateByPost(query)) {
      return response.getStatusLine().getStatusCode();
    } catch (IOException e) {
      LOGGER.error(e);
    }
    return 500;
  }
}