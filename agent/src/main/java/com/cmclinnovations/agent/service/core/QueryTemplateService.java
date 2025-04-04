package com.cmclinnovations.agent.service.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.template.FormTemplateFactory;
import com.cmclinnovations.agent.template.query.DeleteQueryTemplateFactory;
import com.cmclinnovations.agent.template.query.GetQueryTemplateFactory;
import com.cmclinnovations.agent.template.query.SearchQueryTemplateFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class QueryTemplateService {
  private final FileService fileService;
  private final FormTemplateFactory formTemplateFactory;
  private final DeleteQueryTemplateFactory deleteQueryTemplateFactory;
  private final GetQueryTemplateFactory getQueryTemplateFactory;
  private final SearchQueryTemplateFactory searchQueryTemplateFactory;

  private static final Logger LOGGER = LogManager.getLogger(QueryTemplateService.class);

  /**
   * Constructs a new service.
   * 
   * @param fileService   File service for accessing file resources.
   * @param jsonLdService A service for interactions with JSON LD.
   */
  public QueryTemplateService(FileService fileService, JsonLdService jsonLdService) {
    this.formTemplateFactory = new FormTemplateFactory();
    this.deleteQueryTemplateFactory = new DeleteQueryTemplateFactory(jsonLdService);
    this.getQueryTemplateFactory = new GetQueryTemplateFactory();
    this.searchQueryTemplateFactory = new SearchQueryTemplateFactory();
    this.fileService = fileService;
  }

  /**
   * Get JSON-LD template from the target resource.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   */
  public ObjectNode getJsonLdTemplate(String resourceID, String targetId) {
    LOGGER.debug("Retrieving the JSON-LD template...");
    return this.getJsonLDResource(resourceID).deepCopy();
  }

  /**
   * Generates a DELETE SPARQL query from the inputs.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   */
  public Queue<String> genDeleteQuery(String resourceID, String targetId) {
    LOGGER.debug("Generating the DELETE query...");
    // Retrieve the instantiation JSON schema
    ObjectNode addJsonSchema = this.getJsonLDResource(resourceID).deepCopy();
    String instanceIri = addJsonSchema.path(ShaclResource.ID_KEY).asText();
    Queue<String> query = this.deleteQueryTemplateFactory
        .write(new QueryTemplateFactoryParameters(addJsonSchema, targetId));
    query.offer(instanceIri);
    return query;
  }

  /**
   * Generates the form template as a JSON object.
   * 
   * @param shaclFormInputs the form inputs queried from the SHACL restrictions.
   * @param defaultVals     the default values for the form.
   */
  public Map<String, Object> genFormTemplate(ArrayNode shaclFormInputs, Map<String, Object> defaultVals) {
    LOGGER.debug("Generating the form template from the found SHACL restrictions...");
    return this.formTemplateFactory.genTemplate(shaclFormInputs, defaultVals);
  }

  /**
   * Generates a SELECT SPARQL query to retrieve instances from the inputs.
   * 
   * @param queryVarsAndPaths The query construction requirements.
   */
  public Queue<String> genGetQuery(Queue<Queue<SparqlBinding>> queryVarsAndPaths) {
    return this.genGetQuery(queryVarsAndPaths, "", null, "", new HashMap<>());
  }

  /**
   * Generates a SELECT SPARQL query to retrieve instances from the inputs.
   * 
   * @param queryVarsAndPaths  The query construction requirements.
   * @param targetId           An optional field to target at a specific instance.
   * @param parentField        Optional parent field.
   * @param addQueryStatements Additional query statements to be added
   * @param addVars            Optional additional variables to be included in the
   *                           query, along with their order sequence
   */
  public Queue<String> genGetQuery(Queue<Queue<SparqlBinding>> queryVarsAndPaths, String targetId,
      ParentField parentField, String addQueryStatements, Map<String, List<Integer>> addVars) {
    LOGGER.debug("Generating the SELECT query to get instances...");
    return this.getQueryTemplateFactory
        .write(
            new QueryTemplateFactoryParameters(queryVarsAndPaths, targetId, parentField, addQueryStatements, addVars));
  }

  /**
   * Generates a SELECT SPARQL query for searching instances from the inputs.
   * 
   * @param queryVarsAndPaths The query construction requirements.
   * @param criterias         All the available search criteria inputs.
   */
  public Queue<String> genSearchQuery(Queue<Queue<SparqlBinding>> queryVarsAndPaths, Map<String, String> criterias) {
    LOGGER.debug("Generating the SELECT query to search for specific instances...");
    return this.searchQueryTemplateFactory
        .write(new QueryTemplateFactoryParameters(queryVarsAndPaths, criterias));
  }

  /**
   * Retrieve the sequence of the fields.
   */
  public List<String> getFieldSequence() {
    return this.getQueryTemplateFactory.getSequence();
  }

  /**
   * Retrieve the JSON LD resource based on the resource ID.
   * 
   * @param resourceID The target resource identifier for the instance.
   */
  private JsonNode getJsonLDResource(String resourceID) {
    String filePath = LifecycleResource.getLifecycleResourceFilePath(resourceID);
    // Default to the file name in application-service if it is not a lifecycle
    // route
    if (filePath == null) {
      String fileName = this.fileService.getTargetFileName(resourceID);
      filePath = FileService.SPRING_FILE_PATH_PREFIX + FileService.JSON_LD_DIR + fileName + ".jsonld";
    }
    // Retrieve the instantiation JSON schema
    JsonNode contents = this.fileService.getJsonContents(filePath);
    if (!contents.isObject()) {
      throw new IllegalArgumentException("Invalid JSON-LD format! Please ensure the file starts with an JSON object.");
    }
    return contents;
  }
}