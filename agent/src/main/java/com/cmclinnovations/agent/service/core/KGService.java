package com.cmclinnovations.agent.service.core;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.federated.FedXFactory;
import org.eclipse.rdf4j.federated.repository.FedXRepository;
import org.eclipse.rdf4j.federated.repository.FedXRepositoryConnection;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.query.resultio.text.csv.SPARQLResultsCSVWriter;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.stack.clients.blazegraph.BlazegraphClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import uk.ac.cam.cares.jps.base.query.RemoteStoreClient;

@Service
public class KGService {
  @Value("${NAMESPACE}")
  String namespace;

  private final RestClient client;
  private final ObjectMapper objectMapper;
  private final FileService fileService;
  private final LoggingService loggingService;

  private static final String DEFAULT_NAMESPACE = "kb";
  private static final String JSON_MEDIA_TYPE = "application/json";
  private static final String LD_JSON_MEDIA_TYPE = "application/ld+json";
  private static final String SPARQL_MEDIA_TYPE = "application/sparql-query";

  public static final String INVALID_SHACL_ERROR_MSG = "Invalid knowledge model! SHACL restrictions have not been defined/instantiated in the knowledge graph.";

  private static final String RDF_LIST_PATH_PREFIX = "/rdf:rest";
  private static final String SUB_SHAPE_PATH = "sh:node/sh:property";

  private static final Logger LOGGER = LogManager.getLogger(KGService.class);

  /**
   * Constructs a new service.
   * 
   * @param fileService    File service for accessing file resources.
   * @param loggingService Service for logging statements.
   */
  public KGService(FileService fileService, LoggingService loggingService) {
    this.client = RestClient.create();
    this.objectMapper = new ObjectMapper();
    this.fileService = fileService;
    this.loggingService = loggingService;
  }

  /**
   * Add the target triples in JSON-LD format into the KG.
   * 
   * @param contents the contents to add
   */
  public ResponseEntity<String> add(String contents) {
    return this.client.post()
        .uri(BlazegraphClient.getInstance().getRemoteStoreClient(this.namespace).getQueryEndpoint())
        .accept(MediaType.valueOf(LD_JSON_MEDIA_TYPE))
        .contentType(MediaType.valueOf(LD_JSON_MEDIA_TYPE))
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
  public ResponseEntity<String> delete(String query, String targetId) {
    LOGGER.debug("Deleting instances...");
    int statusCode = this.executeUpdate(query);
    if (statusCode == 200) {
      LOGGER.info("Instance has been successfully deleted!");
      return new ResponseEntity<>(
          "Instance has been successfully deleted!",
          HttpStatus.OK);
    } else {
      return new ResponseEntity<>(
          "Error deleting instances from the KG. Please read the logs for more information!",
          HttpStatus.INTERNAL_SERVER_ERROR);
    }
  }

  /**
   * Executes the query at the default endpoint `kb` to retrieve the
   * original-format results.
   * 
   * @param query the query for execution.
   * 
   * @return the query results.
   */
  public Queue<SparqlBinding> query(String query) {
    return this.query(query,
        BlazegraphClient.getInstance().getRemoteStoreClient(DEFAULT_NAMESPACE).getQueryEndpoint());
  }

  /**
   * A method that executes a query at the specified endpoint to retrieve the
   * original-format results.
   * 
   * @param query    the query for execution.
   * @param endpoint the endpoint for execution.
   * 
   * @return the query results.
   */
  public Queue<SparqlBinding> query(String query, String endpoint) {
    this.loggingService.logQuery(query, LOGGER);
    String results = this.client.post()
        .uri(endpoint)
        .accept(MediaType.valueOf(JSON_MEDIA_TYPE))
        .contentType(MediaType.valueOf(SPARQL_MEDIA_TYPE))
        .body(query)
        .retrieve()
        .body(String.class);
    // Returns an array
    JsonNode jsonResults;
    try {
      jsonResults = this.objectMapper.readValue(results, ObjectNode.class).path("results").path("bindings");
    } catch (JsonProcessingException e) {
      LOGGER.error(e);
      throw new IllegalArgumentException(e);
    }
    if (jsonResults.isArray()) {
      return this.parseResults((ArrayNode) jsonResults);
    }
    return new ArrayDeque<>();
  }

  /**
   * A method that executes a federated query across available endpoints to
   * retrieve results in JSONArray.
   * 
   * @param query        the query for execution.
   * @param endpointType the type of endpoint. Options include Mixed, Blazegraph,
   *                     and Ontop.
   * 
   * @return the query results.
   */
  public Queue<SparqlBinding> query(String query, SparqlEndpointType endpointType) {
    List<String> endpoints = this.getEndpoints(endpointType);
    try {
      StringWriter stringWriter = new StringWriter();
      FedXRepository repository = FedXFactory.createSparqlFederation(endpoints);
      try (FedXRepositoryConnection conn = repository.getConnection()) {
        this.loggingService.logQuery(query, LOGGER);
        TupleQuery tq = conn.prepareTupleQuery(query);
        // Extend execution time as required
        tq.setMaxExecutionTime(600);
        SPARQLResultsJSONWriter jsonWriter = new SPARQLResultsJSONWriter(stringWriter);
        tq.evaluate(jsonWriter);
        JsonNode bindings = this.objectMapper.readValue(stringWriter.toString(), ObjectNode.class)
            .path("results")
            .path("bindings");
        if (bindings.isArray()) {
          return this.parseResults((ArrayNode) bindings);
        }
      } catch (RepositoryException e) {
        LOGGER.error(e);
      } catch (JsonProcessingException e) {
        LOGGER.error(e);
        throw new IllegalArgumentException(e);
      }
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return new ArrayDeque<>();
  }

  /**
   * Executes the query at the target endpoint to retrieve results in the CSV
   * format.
   * 
   * @param query        the query for execution.
   * @param endpointType the type of endpoint. Options include Mixed, Blazegraph,
   *                     and Ontop.
   * 
   * @return the query results as CSV rows.
   */
  public String[] queryCSV(String query, SparqlEndpointType endpointType) {
    List<String> endpoints = this.getEndpoints(endpointType);
    try {
      StringWriter stringWriter = new StringWriter();
      FedXRepository repository = FedXFactory.createSparqlFederation(endpoints);
      try (FedXRepositoryConnection conn = repository.getConnection()) {
        TupleQuery tq = conn.prepareTupleQuery(query);
        // Extend execution time as required
        tq.setMaxExecutionTime(600);
        SPARQLResultsCSVWriter csvWriter = new SPARQLResultsCSVWriter(stringWriter);
        tq.evaluate(csvWriter);
        // Results are in csv in one string
        String csvData = stringWriter.toString();
        // Split into rows
        return csvData.split("\\r?\\n");
      } catch (RepositoryException e) {
        LOGGER.error(e);
      }
    } catch (Exception e) {
      LOGGER.error(e);
    }
    return new String[0];
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
        .accept(MediaType.valueOf(LD_JSON_MEDIA_TYPE))
        .contentType(MediaType.valueOf(SPARQL_MEDIA_TYPE))
        .body(query)
        .retrieve()
        .body(String.class);
    ArrayNode parsedResults = null;
    try {
      parsedResults = this.objectMapper.readValue(results, ArrayNode.class);
    } catch (JsonProcessingException e) {
      LOGGER.error(e);
      throw new IllegalArgumentException(e);
    }
    return parsedResults;
  }

  /**
   * Gets all available internal SPARQL endpoints within the stack of the
   * specified type.
   * 
   * @param endpointType The required endpoint type. Can be either mixed,
   *                     blazegraph, or ontop.
   * @return List of endpoints of type `endpointType`
   */
  public List<String> getEndpoints(SparqlEndpointType endpointType) {
    LOGGER.debug("Retrieving available endpoints...");
    String query = this.fileService.getContentsWithReplacement(FileService.ENDPOINT_QUERY_RESOURCE,
        endpointType.getIri());
    Queue<SparqlBinding> results = this.query(query);
    return results.stream()
        .map(binding -> binding.getFieldValue("endpoint"))
        .toList();
  }

  /**
   * Queries for the nested predicates as a queue of responses based on their
   * current nested level.
   * 
   * @param shaclPathQuery The query to retrieve the required predicate paths in
   *                       the SHACL restrictions.
   */
  public Queue<Queue<SparqlBinding>> queryNestedPredicates(String shaclPathQuery) {
    LOGGER.debug("Querying the knowledge graph for predicate paths and variables...");
    // Retrieve all endpoints once
    List<String> endpoints = this.getEndpoints(SparqlEndpointType.BLAZEGRAPH);
    // Initialise a queue to store all values
    Queue<Queue<SparqlBinding>> results = new ArrayDeque<>();
    String replacementShapePath = ""; // Initial replacement string
    boolean continueLoop = true; // Initialise a continue indicator
    // Iterate to get predicates at the hierarchy of shapes enforced via sh:node
    while (continueLoop) {
      boolean isFirstIteration = true;
      boolean hasResults = false;
      String replacementPath = "";
      Queue<SparqlBinding> variablesAndPropertyPaths = new ArrayDeque<>();
      // Iterate through the depth to retrieve all associated predicate paths for this
      // property in this shape
      while (isFirstIteration || hasResults) {
        hasResults = false; // Reset and verify if there are results for this iteration
        // Replace the [subproperty] and [path] with the respective values
        String executableQuery = shaclPathQuery
            .replace(FileService.REPLACEMENT_SHAPE, replacementShapePath)
            .replace(FileService.REPLACEMENT_PATH, replacementPath);
        // SHACL restrictions are only found within one Blazegraph endpoint, and can be
        // queried without using FedX
        for (String endpoint : endpoints) {
          LOGGER.debug("Querying at the endpoint {}...", endpoint);
          Queue<SparqlBinding> queryResults = this.query(executableQuery, endpoint);
          if (!queryResults.isEmpty()) {
            LOGGER.debug("Found data at the endpoint {}...", endpoint);
            variablesAndPropertyPaths.addAll(queryResults);
            // Indicator should be marked if results are found
            hasResults = true;
          }
        }
        if (hasResults) {
          // Extend replacement path based on the current level
          replacementPath = replacementPath.isEmpty() ? RDF_LIST_PATH_PREFIX + "/rdf:first" // first level
              : RDF_LIST_PATH_PREFIX + replacementPath; // for the second level onwards
        }
        if (isFirstIteration) {
          // If there are results in the first iteration of the retrieval for this shape,
          // iterate to check if there are any nested shapes with predicates
          continueLoop = hasResults;
          isFirstIteration = false;
        }
      }
      if (!variablesAndPropertyPaths.isEmpty()) {
        results.offer(variablesAndPropertyPaths);
      }
      // Extend to get the next level of shape if any
      replacementShapePath = replacementShapePath.isEmpty() ? " ?nestedshape." +
          "?nestedshape sh:name ?" + StringResource.NODE_GROUP_VAR + ";sh:node/sh:targetClass ?"
          + StringResource.NESTED_CLASS_VAR + ";" + SUB_SHAPE_PATH
          : "/" + SUB_SHAPE_PATH + replacementShapePath;
    }
    if (results.isEmpty()) {
      LOGGER.error(INVALID_SHACL_ERROR_MSG);
      throw new IllegalStateException(INVALID_SHACL_ERROR_MSG);
    }
    return results;
  }

  /**
   * Combine two queues containing SparlBinding objects. The method also removes
   * duplicates in the combined queue.
   * 
   * @param firstQueue The first target queue.
   * @param secQueue   The second target queue.
   */
  public Queue<SparqlBinding> combineBindingQueue(Queue<SparqlBinding> firstQueue, Queue<SparqlBinding> secQueue) {
    if (firstQueue.isEmpty() && secQueue.isEmpty()) {
      return new ArrayDeque<>();
    }
    Queue<SparqlBinding> result = new ArrayDeque<>();
    // Group them by the IRI key
    Map<String, List<SparqlBinding>> groupedBindings = Stream.concat(firstQueue.stream(), secQueue.stream())
        .distinct()
        .collect(Collectors.groupingBy(binding -> {
          String id = binding.containsField(LifecycleResource.IRI_KEY)
              ? binding.getFieldValue(LifecycleResource.IRI_KEY)
              : binding.getFieldValue("id");
          // If this is a lifecycle event occurrence, group them by date and id
          if (binding.containsField(StringResource.parseQueryVariable(LifecycleResource.EVENT_ID_KEY))) {
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
      if (groupedBinding.size() > 1) {
        for (int i = 1; i < groupedBinding.size(); i++) {
          firstBinding.addFieldArray(groupedBinding.get(i));
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
  private int executeUpdate(String query) {
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

  /**
   * Parses the results into the required data model.
   * 
   * @param results Results retrieved from the knowledge graph.
   */
  private Queue<SparqlBinding> parseResults(ArrayNode results) {
    LOGGER.debug("Parsing the results...");
    return StreamSupport.stream(results.spliterator(), false)
        .filter(JsonNode::isObject) // Ensure they are object node so that we can type cast
        .map(row -> new SparqlBinding((ObjectNode) row))
        .collect(Collectors.toCollection(ArrayDeque::new));
  }
}