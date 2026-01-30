package com.cmclinnovations.agent.component.repository;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.federated.FedXFactory;
import org.eclipse.rdf4j.federated.repository.FedXRepository;
import org.eclipse.rdf4j.federated.repository.FedXRepositoryConnection;
import org.eclipse.rdf4j.query.TupleQuery;
import org.eclipse.rdf4j.query.resultio.sparqljson.SPARQLResultsJSONWriter;
import org.eclipse.rdf4j.repository.RepositoryException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.service.core.LoggingService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.stack.clients.blazegraph.BlazegraphClient;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Component
public class KGRepository {
    @Value("${SHACL_NAMESPACE}")
    String shaclNamespace;

    private final RestClient client;
    private final ObjectMapper objectMapper;
    private final FileService fileService;
    private final LoggingService loggingService;
    private final QueryTemplateService queryTemplateService;

    private static final String DEFAULT_NAMESPACE = "kb";
    private static final String RDF_LIST_PATH_PREFIX = "/rdf:rest";
    private static final String SUB_SHAPE_PATH = "sh:node/sh:property";
    private static final String FILTER_BOUNDED_PROPERTIES = "FILTER(BOUND(?name))";

    private static final Logger LOGGER = LogManager.getLogger(KGRepository.class);

    /**
     * Constructs a repository to access the knowledge graph. This repository serves
     * to separate the cacheable methods from their callers, as SpringBoot enforces
     * this behaviour.
     */
    public KGRepository(FileService fileService, LoggingService loggingService,
            QueryTemplateService queryTemplateService) {
        this.client = RestClient.create();
        this.objectMapper = new ObjectMapper();
        this.fileService = fileService;
        this.loggingService = loggingService;
        this.queryTemplateService = queryTemplateService;
    }

    /**
     * Gets all available SPARQL endpoints (of the specified type) containing data.
     * 
     * @param endpointType The required endpoint type. Can be either mixed,
     *                     blazegraph, or ontop.
     * @return List of endpoints
     */
    @Cacheable(value = "endpoint", key = "#endpointType.getIri()")
    public List<String> getEndpoints(SparqlEndpointType endpointType) {
        LOGGER.info("Cache Miss: retrieving available endpoints...");
        String query = this.fileService.getContentsWithReplacement(FileService.ENDPOINT_QUERY_RESOURCE,
                endpointType.getIri());
        String shaclEndpoint = this.getShaclEndpoint();
        return this.query(query,
                BlazegraphClient.getInstance().getRemoteStoreClient(KGRepository.DEFAULT_NAMESPACE)
                        .getQueryEndpoint())
                .stream().filter(binding -> !binding.getFieldValue("endpoint").equals(shaclEndpoint))
                .map(binding -> binding.getFieldValue("endpoint"))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * Retrieves the SHACL endpoint URL.
     */
    @Cacheable(value = "shaclendpoint")
    public String getShaclEndpoint() {
        return BlazegraphClient.getInstance().getRemoteStoreClient(this.shaclNamespace)
                .getQueryEndpoint();
    }

    /**
     * A method that executes a query at the specified endpoint to retrieve the
     * SPARQL results.
     * 
     * @param query    the query for execution.
     * @param endpoint the endpoint for execution.
     * 
     * @return the query results.
     */
    public List<SparqlBinding> query(String query, String endpoint) {
        this.loggingService.logQuery(query, LOGGER);
        String results = this.client.post()
                .uri(endpoint)
                .accept(QueryResource.JSON_MEDIA_TYPE)
                .contentType(QueryResource.SPARQL_MEDIA_TYPE)
                .body(query)
                .retrieve()
                .body(String.class);
        JsonNode[] sparqlResponse = this.readSparqlResponse(results);
        if (sparqlResponse[0].isArray()) {
            return this.readResultsAsSparqlBinding((ArrayNode) sparqlResponse[0], (ArrayNode) sparqlResponse[1]);
        }
        return new ArrayList<>();
    }

    /**
     * A method that executes a federated query across the endpoints to retrieve
     * results.
     * 
     * @param query     The query for execution.
     * @param endpoints List of endpoints for execution.
     * 
     * @return the query results.
     */
    public List<SparqlBinding> query(String query, List<String> endpoints) {
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
                JsonNode[] sparqlResponse = this.readSparqlResponse(stringWriter.toString());
                if (sparqlResponse[0].isArray()) {
                    return this.readResultsAsSparqlBinding((ArrayNode) sparqlResponse[0],
                            (ArrayNode) sparqlResponse[1]);
                }
            } catch (RepositoryException e) {
                LOGGER.error(e);
            }
        } catch (Exception e) {
            LOGGER.error(e);
        }
        return new ArrayList<>();
    }

    /**
     * Retrieve the parameters defined by the user in SHACL to generate the SPARQL
     * query required.
     * 
     * @param shaclReplacement The replacement value of the SHACL query target
     * @param requireLabel     Indicates if labels should be returned for all the
     *                         fields that are IRIs.
     */
    @Cacheable(value = "shaclQuery", key = "#shaclReplacement.concat('-').concat(#requireLabel)")
    public List<List<SparqlBinding>> execParamsConstructorQuery(String shaclReplacement,
            boolean requireLabel) {
        String query = this.queryTemplateService.getShaclQuery(shaclReplacement, requireLabel);
        return this.queryNestedPredicates(query);
    }

    /**
     * Retrieve the optional parameters defined by the user in SHACL to generate the
     * SPARQL query required.
     *
     * @param shaclReplacement The replacement value of the SHACL query target
     */
    @Cacheable(value = "shaclOptionalQuery", key = "#shaclReplacement.concat('-optional')")
    public List<SparqlBinding> execOptionalParamQuery(String shaclReplacement) {
        String query = this.fileService.getContentsWithReplacement(FileService.SHACL_PROPERTY_OPTIONAL_RESOURCE,
                shaclReplacement);
        return this.query(query, this.getShaclEndpoint());
    }

    /**
     * Reads the SPARQL query response into object nodes.
     * 
     * @param response Response string from the knowledge graph.
     */
    private JsonNode[] readSparqlResponse(String response) {
        try {
            ObjectNode sparqlResponse = this.objectMapper.readValue(response, ObjectNode.class);
            JsonNode bindings = sparqlResponse.path("results")
                    .path("bindings");
            JsonNode variables = sparqlResponse.path("head")
                    .path("vars");
            return new JsonNode[] { bindings, variables };
        } catch (JsonProcessingException e) {
            LOGGER.error(e);
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * Parses the results into the required data model.
     * 
     * @param results   Results retrieved from the knowledge graph.
     * @param variables Expected variables to find.
     */
    private List<SparqlBinding> readResultsAsSparqlBinding(ArrayNode results, ArrayNode variables) {
        LOGGER.debug("Parsing the results...");
        try {
            List<String> variableSet = this.objectMapper.readerForListOf(String.class).readValue(variables);
            return StreamSupport.stream(results.spliterator(), false)
                    .filter(JsonNode::isObject) // Ensure they are object node so that we can type cast
                    .map(row -> new SparqlBinding((ObjectNode) row, variableSet))
                    .collect(Collectors.toCollection(ArrayList::new));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Queries for the nested predicates as a queue of responses based on their
     * current nested level.
     * 
     * @param shaclPathQuery The query to retrieve the required predicate paths in
     *                       the SHACL restrictions.
     */
    private List<List<SparqlBinding>> queryNestedPredicates(String shaclPathQuery) {
        LOGGER.debug("Querying the knowledge graph for predicate paths and variables...");
        String shaclEndpoint = this.getShaclEndpoint();
        // Initialise a queue to store all values
        List<List<SparqlBinding>> results = new ArrayList<>();
        String replacementShapePath = ""; // Initial replacement string
        String replacementFilterPath = ""; // Initial replacement string
        boolean continueLoop = true; // Initialise a continue indicator
        // Iterate to get predicates at the hierarchy of shapes enforced via sh:node
        while (continueLoop) {
            boolean isFirstIteration = true;
            boolean hasResults = false;
            String replacementPath = "";
            List<SparqlBinding> variablesAndPropertyPaths = new ArrayList<>();
            // Iterate through the depth to retrieve all associated predicate paths for this
            // property in this shape
            while (isFirstIteration || hasResults) {
                hasResults = false; // Reset and verify if there are results for this iteration
                // Replace the [subproperty] and [path] with the respective values
                String executableQuery = shaclPathQuery
                        .replace(FileService.REPLACEMENT_SHAPE, replacementShapePath)
                        .replace(FileService.REPLACEMENT_PATH, replacementPath)
                        .replace(FileService.REPLACEMENT_FILTER, replacementFilterPath);
                // SHACL restrictions are only found within one Blazegraph endpoint, and can be
                // queried without using FedX
                LOGGER.debug("Querying at the endpoint {}...", shaclEndpoint);
                List<SparqlBinding> queryResults = this.query(executableQuery, shaclEndpoint);
                if (!queryResults.isEmpty()) {
                    LOGGER.debug("Found data at the endpoint {}...", shaclEndpoint);
                    variablesAndPropertyPaths.addAll(queryResults);
                    // Indicator should be marked if results are found
                    hasResults = true;
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
                    // Filter should be updated to no longer present empty branches after the first
                    // iteration ever
                    replacementFilterPath = FILTER_BOUNDED_PROPERTIES;
                }
            }
            if (!variablesAndPropertyPaths.isEmpty()) {
                results.add(variablesAndPropertyPaths);
            }
            // Extend to get the next level of shape if any
            replacementShapePath = replacementShapePath.isEmpty() ? " ?nestedshape." +
                    "OPTIONAL{?nestedshape twa:role ?nestedrole.}" +
                    "OPTIONAL{?nestedshape sh:node/sh:targetClass ?" + ShaclResource.NESTED_CLASS_VAR +
                    ".}?nestedshape sh:name ?" + ShaclResource.NODE_GROUP_VAR + ";" + SUB_SHAPE_PATH
                    : "/" + SUB_SHAPE_PATH + replacementShapePath;
        }
        if (results.isEmpty()) {
            LOGGER.error(StringResource.INVALID_SHACL_ERROR_MSG);
            throw new IllegalStateException(StringResource.INVALID_SHACL_ERROR_MSG);
        }
        return results;
    }

}
