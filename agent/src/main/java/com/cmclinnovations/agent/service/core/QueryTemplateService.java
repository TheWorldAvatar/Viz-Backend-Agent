package com.cmclinnovations.agent.service.core;

import java.nio.file.FileSystemNotFoundException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.pagination.SortDirective;
import com.cmclinnovations.agent.template.FormTemplateFactory;
import com.cmclinnovations.agent.template.query.DeleteQueryTemplateFactory;
import com.cmclinnovations.agent.template.query.GetQueryTemplateFactory;
import com.cmclinnovations.agent.template.query.SearchQueryTemplateFactory;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
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
    this.formTemplateFactory = new FormTemplateFactory(this.authenticationService, jsonLdService);
    this.deleteQueryTemplateFactory = new DeleteQueryTemplateFactory(jsonLdService);
    this.getQueryTemplateFactory = new GetQueryTemplateFactory(this.authenticationService);
    this.searchQueryTemplateFactory = new SearchQueryTemplateFactory(this.authenticationService);
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
  public String genDeleteQuery(String resourceID, String targetId) {
    LOGGER.debug("Generating the DELETE query...");
    // Retrieve the instantiation JSON schema
    ObjectNode addJsonSchema = this.getJsonLDResource(resourceID).deepCopy();
    return this.deleteQueryTemplateFactory
        .write(new QueryTemplateFactoryParameters(addJsonSchema, targetId));
  }

  /**
   * Retrieves the target IRI in `application-form.yml`.
   * 
   * @param resourceID The target resource identifier.
   */
  public String getIri(String resourceID) {
    return this.fileService.getTargetIri(resourceID).getQueryString();
  }

  /**
   * Retrieves the query template to get all IDs associated with instances. A
   * placeholder class is used and MUST be replaced to get the right ids.
   * 
   * @param nodeShapeReplacement The statement to target the node shape.
   * @param addQueryStatements   Additional query statements to be added.
   * @param pagination           Optional state containing the current page and
   *                             limit.
   */
  public String getAllIdsQueryTemplate(String nodeShapeReplacement, String addQueryStatements,
      PaginationState pagination, boolean requireId) {
    // If pagination is not given, no limits and offset should be set
    SelectQuery query = QueryResource.getSelectQuery(true, pagination.getLimit())
        .where(QueryResource.IRI_VAR.isA(Rdf.iri(
            nodeShapeReplacement.substring(1, nodeShapeReplacement.length() - 1)))
            .andHas(QueryResource.DC_TERM_ID, QueryResource.ID_VAR))
        .offset(pagination.getOffset());
    if (requireId) {
      query.select(QueryResource.ID_VAR);
    }
    Queue<SortDirective> sortDirectives = new ArrayDeque<>(pagination.getSortDirectives());
    boolean hasNoIdToSort = sortDirectives.stream()
        .allMatch(directive -> !directive.field().getVarName().equals(QueryResource.ID_KEY));
    while (!sortDirectives.isEmpty()) {
      SortDirective directive = sortDirectives.poll();
      if (!directive.field().getVarName().equals(QueryResource.ID_KEY)) {
        query.select(directive.field());
      }
      query.orderBy(directive.order());
    }
    // ID is an index and has to be included even if not specified by the user
    if (hasNoIdToSort) {
      query.orderBy(QueryResource.ID_VAR);
    }
    return query
        .getQueryString()
        .replace("?id .", "?id ." + addQueryStatements);
  }

  /**
   * Retrieves the required SHACL path query.
   * 
   * @param replacement  The replacement string for [target].
   * @param requireLabel Indicates if labels should be returned for all the
   *                     fields that are IRIs.
   */
  public String getShaclQuery(String replacement, boolean requireLabel) {
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
        Rdf.iri(conceptClass).getQueryString());
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
      resourceID = this.fileService.getTargetIri(resourceID).getQueryString();
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
    return this.formTemplateFactory.genTemplate(shaclFormInputs, defaultVals);
  }

  /**
   * Generates a SELECT SPARQL query to retrieve instances from the inputs.
   * 
   * @param queryVarsAndPaths  The query construction requirements.
   * @param targetIds          An optional field with the specific IDs to target.
   * @param sortDirectives     Sort directives to order the results.
   * @param parentField        Optional parent field.
   * @param addQueryStatements Additional query statements to be added
   * @param addVars            Optional additional variables to be included in the
   *                           query, along with their order sequence
   */
  public String genGetQuery(Queue<Queue<SparqlBinding>> queryVarsAndPaths, Queue<List<String>> targetIds,
      Queue<SortDirective> sortDirectives, ParentField parentField, String addQueryStatements,
      Map<Variable, List<Integer>> addVars) {
    LOGGER.debug("Generating the SELECT query to get instances...");
    String orderByClause = this.genOrderByClause(sortDirectives);
    return this.getQueryTemplateFactory
        .write(
            new QueryTemplateFactoryParameters(queryVarsAndPaths, targetIds, parentField, orderByClause,
                addQueryStatements, addVars));
  }

  /**
   * Generates a WHERE SPARQL query to retrieve instances from the inputs.
   * 
   * @param queryVarsAndPaths The query construction requirements.
   */
  public String genWhereClause(Queue<Queue<SparqlBinding>> queryVarsAndPaths) {
    LOGGER.debug("Generating the SELECT query to get instances...");
    return this.getQueryTemplateFactory.genWhereClause(queryVarsAndPaths);
  }

  /**
   * Generates a SELECT SPARQL query for searching instances from the inputs.
   * 
   * @param queryVarsAndPaths The query construction requirements.
   * @param criterias         All the available search criteria inputs.
   */
  public String genSearchQuery(Queue<Queue<SparqlBinding>> queryVarsAndPaths, Map<String, String> criterias) {
    LOGGER.debug("Generating the SELECT query to search for specific instances...");
    return this.searchQueryTemplateFactory
        .write(new QueryTemplateFactoryParameters(queryVarsAndPaths, criterias));
  }

  /**
   * Retrieve the sequence of the fields.
   */
  public List<Variable> getFieldSequence() {
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
    // Retrieve the default lifecycle resources if available
    String filePath = LifecycleResource.getLifecycleResourceFilePath(resourceID);
    try {
      // Attempt to retrieve any custom JSON-LD if they exist
      String fileName = this.fileService.getTargetFileName(resourceID);
      // Overwrite the custom JSON-LD for non-lifecycle and lifecycle resources
      filePath = FileService.SPRING_FILE_PATH_PREFIX + FileService.JSON_LD_DIR + fileName + ".jsonld";
    } catch (FileSystemNotFoundException e) {
      // Non-lifecycle resources will always have a custom JSON-LD, else, its an
      // invalid file that should throw an error
      if (filePath == null) {
        throw e;
      }
      // No error will be thrown for lifecycle resources which has default resources
    }

    // Retrieve the instantiation JSON schema
    JsonNode contents = this.fileService.getJsonContents(filePath);
    if (!contents.isObject()) {
      throw new IllegalArgumentException("Invalid JSON-LD format! Please ensure the file starts with an JSON object.");
    }
    return contents;
  }

  /**
   * Generate order by clause from the sort directives.
   * 
   * @param sortDirectives Sort directives to order the results.
   */
  private String genOrderByClause(Queue<SortDirective> sortDirectives) {
    boolean hasNoIdToSort = sortDirectives.stream()
        .allMatch(directive -> !directive.field().getVarName().equals(QueryResource.ID_KEY));
    StringBuilder orderByBuilder = new StringBuilder();
    while (!sortDirectives.isEmpty()) {
      SortDirective directive = sortDirectives.poll();
      String orderConditionField = directive.order().getQueryString();
      // Overwrite last modified field to use original for sorting
      if (directive.field().getVarName()
          .equals(StringResource.ORIGINAL_PREFIX + LifecycleResource.LAST_MODIFIED_KEY)) {
        orderConditionField = orderConditionField.replace(
            StringResource.ORIGINAL_PREFIX + LifecycleResource.LAST_MODIFIED_KEY,
            LifecycleResource.LAST_MODIFIED_KEY);
      }
      orderByBuilder.append(ShaclResource.WHITE_SPACE).append(orderConditionField);
    }
    // ID is an index and has to be included even if not specified by the user
    if (hasNoIdToSort) {
      orderByBuilder.append(ShaclResource.WHITE_SPACE).append(QueryResource.ID_VAR.getQueryString());
    }
    if (orderByBuilder.isEmpty()) {
      return "";
    }
    return "ORDER BY " + orderByBuilder.toString();
  }
}