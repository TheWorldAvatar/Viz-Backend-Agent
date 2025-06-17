package com.cmclinnovations.agent.service.core;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

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
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class QueryTemplateService {
  private final AuthenticationService authenticationService;
  private final FileService fileService;
  private final FormTemplateFactory formTemplateFactory;
  private final DeleteQueryTemplateFactory deleteQueryTemplateFactory;
  private final GetQueryTemplateFactory getQueryTemplateFactory;
  private final SearchQueryTemplateFactory searchQueryTemplateFactory;

  private static final Logger LOGGER = LogManager.getLogger(QueryTemplateService.class);

  /**
   * Constructs a new service.
   * 
   * 
   * @param authenticationService Service to retrieve user roles and
   *                              authentication information.
   * @param fileService           File service for accessing file resources.
   * @param jsonLdService         A service for interactions with JSON LD.
   */
  public QueryTemplateService(AuthenticationService authenticationService, FileService fileService,
      JsonLdService jsonLdService) {
    this.authenticationService = authenticationService;
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
   */
  public ObjectNode getJsonLdTemplate(String resourceID) {
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
   * Retrieves the SHACL path query for a target IRI in `application-form.yml`.
   * 
   * @param resourceID   The target resource identifier.
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   */
  public String getShaclQuery(String resourceID, boolean requireLabel) {
    String iri = this.fileService.getTargetIri(resourceID);
    return this.getShaclQuery(requireLabel, iri);
  }

  /**
   * Retrieves the required SHACL path query.
   * 
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   * @param replacement  The replacement string for [target].
   */
  public String getShaclQuery(boolean requireLabel, String replacement) {
    LOGGER.debug("Retrieving the required SHACL query...");
    String queryPath = FileService.SHACL_PATH_QUERY_RESOURCE;
    if (requireLabel) {
      // Only use the label query if required due to the associated slower query
      // performance
      queryPath = FileService.SHACL_PATH_LABEL_QUERY_RESOURCE;
    }
    return this.fileService.getContentsWithReplacement(queryPath, replacement);
  }

  /**
   * Retrieves the conceptual query for the target class.
   * 
   * @param conceptClass The target class details to retrieved.
   */
  public String getConceptQuery(String conceptClass) {
    return this.fileService.getContentsWithReplacement(FileService.INSTANCE_QUERY_RESOURCE,
        StringResource.parseIriForQuery(conceptClass));
  }

  /**
   * Retrieves the query to extract the SHACL constraints for the form template.
   * 
   * @param resourceID    The target resource identifier.
   * @param isReplacement Indicates if the resource ID is a replacement value
   *                      rather than a resource.
   */
  public String getFormQuery(String resourceID, boolean isReplacement) {
    if (!isReplacement) {
      resourceID = this.fileService.getTargetIri(resourceID);
    }
    return this.fileService.getContentsWithReplacement(FileService.FORM_QUERY_RESOURCE, resourceID);
  }

  /**
   * Generates the form template as a JSON object.
   * 
   * @param shaclFormInputs the form inputs queried from the SHACL restrictions.
   * @param defaultVals     the default values for the form.
   */
  public Map<String, Object> genFormTemplate(ArrayNode shaclFormInputs,
      Map<String, Object> defaultVals) {
    LOGGER.debug("Generating the form template from the found SHACL restrictions...");
    Set<String> roles = this.authenticationService.getRoles();
    return this.formTemplateFactory.genTemplate(roles, shaclFormInputs, defaultVals);
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
   * Retrieve the mappings of array variables grouped by groups.
   */
  public Map<String, Set<String>> getArrayVariables() {
    return this.getQueryTemplateFactory.getArrayVariables();
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