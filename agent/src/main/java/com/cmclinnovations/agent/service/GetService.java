package com.cmclinnovations.agent.service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
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
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.pagination.SortDirective;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;
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
   * Retrieve the get queries that will be executed.
   * 
   * @param shaclReplacement The replacement value of the SHACL query target
   * @param requireLabel     Indicates if labels should be returned
   */
  public String getQuery(String shaclReplacement, boolean requireLabel) {
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService
        .getSparqlQueryConstructionParameters(shaclReplacement, requireLabel);
    return this.queryTemplateService.genGetQuery(nestedVariablesAndPropertyPaths, new ArrayDeque<>(),
        new ArrayDeque<>(), null, "", new HashMap<>());
  }

  /**
   * Retrieve the matching instances of the search criterias.
   * 
   * @param resourceID The target resource identifier for the instance class.
   * @param criterias  All the available search criteria inputs.
   */
  public ResponseEntity<StandardApiResponse<?>> getMatchingInstances(String resourceID, Map<String, String> criterias) {
    LOGGER.debug("Retrieving the form template for {} ...", resourceID);
    String iri = this.queryTemplateService.getIri(resourceID);
    Queue<Queue<SparqlBinding>> nestedVariablesAndPropertyPaths = this.kgService
        .getSparqlQueryConstructionParameters(iri, false);
    String searchQuery = this.queryTemplateService.genSearchQuery(nestedVariablesAndPropertyPaths, criterias);
    // Query for direct instances
    Queue<SparqlBinding> results = this.kgService.query(searchQuery, SparqlEndpointType.MIXED);
    LOGGER.info(SUCCESSFUL_REQUEST_MSG);
    return this.responseEntityBuilder.success(
        results.stream()
            .map(binding -> binding.getFieldValue(QueryResource.IRI_KEY))
            .toList());
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
   * Retrieve only the specific instance based on the query. The query must have
   * iri as its variable.
   * 
   * @param query Query for execution.
   */
  public SparqlBinding getInstance(String query) {
    LOGGER.debug("Retrieving an instance...");
    Queue<SparqlBinding> results = this.getInstances(query);
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
   * Retrieve the metadata (IRI, label, and description) of the concept associated
   * with the target resource. This will return their current or sub-classes.
   * 
   * @param conceptClass The target class details to retrieved.
   */
  public ResponseEntity<StandardApiResponse<?>> getConceptMetadata(String conceptClass) {
    LOGGER.debug("Retrieving the instances for {} ...", conceptClass);
    String query = this.queryTemplateService.getConceptQuery(conceptClass);
    Queue<SparqlBinding> results = this.getInstances(query);
    List<Map<String, Object>> resultItems;
    if (results.isEmpty()) {
      LOGGER.info(
          "Request has been completed successfully with no results!");
      resultItems = new ArrayList<>();
    } else {
      LOGGER.info(SUCCESSFUL_REQUEST_MSG);
      SparqlBinding rootClassBinding = results.stream()
          .filter(
              binding -> binding.getFieldValue("type").equals(conceptClass) && binding.getFieldValue("parent") != null)
          .findFirst()
          .orElse(null);
      if (rootClassBinding != null) {
        Set<String> rootParentClasses = Arrays.stream(rootClassBinding.getFieldValue("parent").split("\\s"))
            .collect(Collectors.toSet());
        rootParentClasses.add(conceptClass);
        resultItems = results.stream()
            .map(binding -> {
              Map<String, Object> bindMappings = binding.get();
              SparqlResponseField parentField = TypeCastUtils.castToObject(bindMappings.get("parent"),
                  SparqlResponseField.class);
              String parentValue = parentField.value();
              if (parentValue != null) {
                String[] currentParentClasses = parentValue.split("\\s+");
                List<SparqlResponseField> remainingClasses = Arrays.stream(currentParentClasses)
                    .filter(s -> !rootParentClasses.contains(s))
                    .map(
                        s -> new SparqlResponseField(parentField.type(), s, parentField.dataType(), parentField.lang()))
                    .toList();
                if (remainingClasses.isEmpty()) {
                  bindMappings.remove("parent");
                } else {
                  // Typically, there are only one parent class
                  bindMappings.put("parent", remainingClasses.get(0));
                }
              }
              return bindMappings;
            }).toList();

      } else {
        resultItems = results.stream().map(SparqlBinding::get).toList();
      }
    }
    return this.responseEntityBuilder.success(null, resultItems);
  }

  /**
   * Retrieves the number of instances belonging to the resource.
   * 
   * @param resourceID Target resource identifier for the instance class.
   * @param filters    Mappings between filter fields and their values.
   */
  public int getCount(String resourceID, Map<String, String> filters) {
    return this.getCount(resourceID, "", "", filters, null);
  }

  /**
   * Retrieves the number of instances belonging to the resource.
   * 
   * @param resourceID    Target resource identifier for the instance class.
   * @param addStatements Additional query statements to be added if any.
   * @param addSortedBy   Additional sort_by parameters to be added if any.
   * @param filters       Mappings between filter fields and their values.
   * @param isContract    Indicates if it is a contract or task otherwise.
   */
  public int getCount(String resourceID, String addStatements, String addSortedBy, Map<String, String> filters,
      Boolean isContract) {
    Queue<List<String>> ids = this.getAllIds(resourceID, addStatements,
        new PaginationState(0, null, "-id" + addSortedBy, isContract, filters));
    return ids.size();
  }

  /**
   * Retrieve all IDs associated with the target replacement.
   * 
   * @param resourceID    Target resource identifier for the instance class.
   * @param addStatements Additional query statements to be added if any.
   * @param pagination    Optional state containing the current page and limit.
   */
  public Queue<List<String>> getAllIds(String resourceID, String addStatements, PaginationState pagination) {
    LOGGER.info("Retrieving all ids...");
    String iri = this.queryTemplateService.getIri(resourceID);
    addStatements += this.getQueryStatementsForTargetFields(iri, pagination.getSortedFields(),
        pagination.getFilters());
    String allInstancesQuery = this.queryTemplateService.getAllIdsQueryTemplate(iri, addStatements,
        pagination, true);
    return this.kgService.query(allInstancesQuery, SparqlEndpointType.MIXED).stream()
        .map(binding -> {
          String eventIdVar = QueryResource.EVENT_ID_VAR.getVarName();
          if (binding.containsField(eventIdVar)) {
            return List.of(binding.getFieldValue(QueryResource.ID_KEY), binding.getFieldValue(eventIdVar));
          }
          return List.of(binding.getFieldValue(QueryResource.ID_KEY));
        })
        .collect(Collectors.toCollection(ArrayDeque::new));
  }

  /**
   * Retrieve all filter options associated with the resource and the target
   * field.
   * 
   * @param resourceID    Target resource identifier for the instance class.
   * @param field         The field of interest.
   * @param addStatements Additional query statements to be added if any.
   * @param search        String subset to narrow filter scope.
   * @param filters       Optional additional filters.
   */
  public List<String> getAllFilterOptions(String resourceID, String field, String addStatements,
      String search, Map<String, Set<String>> filters) {
    LOGGER.info("Retrieving all filter options...");
    String iri = this.queryTemplateService.getIri(resourceID);
    addStatements += this.getQueryStatementsForTargetFields(iri, new HashSet<>(Set.of(field)), filters);
    if (search != null && !search.isBlank()) {
      addStatements += "FILTER(CONTAINS(LCASE(" + QueryResource.genVariable(field).getQueryString() + ") ,\""
          + search.toLowerCase()
          + "\"))";
    }
    String allInstancesQuery = this.queryTemplateService.getAllIdsQueryTemplate(iri, addStatements,
        new PaginationState(0, 21, "+" + field, new HashMap<>()), false);
    return this.kgService.query(allInstancesQuery, SparqlEndpointType.MIXED).stream()
        .map(binding -> binding.getFieldValue(field))
        .toList();
  }

  /**
   * Retrieve all the target instances and their information using pre-queried
   * IDs. This is an overloaded method that does not depend on the additional
   * query statements in the ID query template.
   * 
   * @param resourceID         The target resource identifier for the instance
   *                           class.
   * @param requireLabel       Indicates if labels should be returned for all
   *                           the fields that are IRIs.
   * @param ids                List of IDs to retrieve.
   * @param sortDirectives     Sort directives to order the results.
   * @param addQueryStatements Additional query statements to be added.
   * @param addVars            Optional additional variables to be included in
   *                           the query, along with their order sequence.
   */
  public Queue<SparqlBinding> getInstances(String resourceID, boolean requireLabel, Queue<List<String>> ids,
      Queue<SortDirective> sortDirectives, String addQueryStatements, Map<Variable, List<Integer>> addVars) {
    LOGGER.debug("Retrieving all instances of {} ...", resourceID);
    String iri = this.queryTemplateService.getIri(resourceID);
    if (ids.isEmpty()) {
      return new ArrayDeque<>();
    }
    return this.execGetInstances(iri, ids, sortDirectives, requireLabel, null, addQueryStatements, addVars);
  }

  /**
   * Retrieve all the target instances and their information. This method can also
   * retrieve instances associated with a specific parent instance if declared.
   * 
   * @param resourceID   The target resource identifier for the instance
   *                     class.
   * @param requireLabel Indicates if labels should be returned for all
   *                     the fields that are IRIs.
   * @param parentField  Optional parent field.
   * @param pagination   Optional pagination state to filter results.
   */
  public Queue<SparqlBinding> getInstances(String resourceID, boolean requireLabel, ParentField parentField,
      PaginationState pagination) {
    LOGGER.debug("Retrieving all instances of {} ...", resourceID);
    String iri = this.queryTemplateService.getIri(resourceID);
    Queue<List<String>> ids = this.getAllIds(resourceID, "", pagination);
    if (ids.isEmpty()) {
      return new ArrayDeque<>();
    }
    return this.execGetInstances(iri, ids, pagination.getSortDirectives(), requireLabel, parentField, "",
        new HashMap<>());
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
    String iri = this.queryTemplateService.getIri(resourceID);
    Queue<SparqlBinding> instances = this.execGetInstances(iri, new ArrayDeque<>(List.of(Arrays.asList(targetId))),
        new ArrayDeque<>(), requireLabel, null, "", new HashMap<>());
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
    GraphPattern lifecycleEventPattern = GraphPatterns.tp(QueryResource.IRI_VAR,
        QueryResource.FIBO_FND_REL_REL_EXEMPLIFIES,
        Rdf.iri(eventType.getEvent()));
    Queue<SparqlBinding> instances = this.execGetInstances(eventType.getShaclReplacement(),
        new ArrayDeque<>(List.of(Arrays.asList(targetId))), new ArrayDeque<>(), false, null,
        lifecycleEventPattern.getQueryString(), new HashMap<>());
    return this.getSingleInstanceResponse(instances);
  }

  /**
   * Gets the query statements associated with the fields of interest.
   * 
   * @param shaclReplacement The replacement value of the SHACL query target.
   * @param sortedFields     Set of fields for sorting that should be included.
   * @param filters          Filters with name and values.
   */
  public String getQueryStatementsForTargetFields(String shaclReplacement, Set<String> sortedFields,
      Map<String, Set<String>> filters) {
    StringBuilder queryBuilder = new StringBuilder();
    Map<String, String> filteredStatementMappings = this.getStatementMappingsForTargetFields(shaclReplacement,
        sortedFields, filters);
    filteredStatementMappings.forEach((key, value) -> {
      if (key.equals(StringResource.SORT_KEY)) {
        queryBuilder.append(value);
      } else {
        Set<String> filterValues = filters.get(key);
        QueryResource.genFilterStatements(value, key, filterValues, queryBuilder);
      }
    });
    if (filters.containsKey(QueryResource.ID_KEY)) {
      QueryResource.genFilterStatements("", QueryResource.ID_KEY, filters.get(QueryResource.ID_KEY), queryBuilder);
    }
    String eventIdVar = QueryResource.EVENT_ID_VAR.getVarName();
    if (filters.containsKey(eventIdVar)) {
      QueryResource.genFilterStatements("?event_id dc-terms:identifier ?ori_event_id.",
          StringResource.ORIGINAL_PREFIX + eventIdVar, filters.get(eventIdVar), queryBuilder);
    }
    return queryBuilder.toString();
  }

  /**
   * Gets the query statements associated with the fields of interest.
   * 
   * @param shaclReplacement The replacement value of the SHACL query target.
   * @param sortedFields     Set of fields for sorting that should be included.
   * @param filters          Filters with name and values.
   */
  public Map<String, String> getStatementMappingsForTargetFields(String shaclReplacement, Set<String> sortedFields,
      Map<String, Set<String>> filters) {
    // First query for all the available query construction params associated with
    // the target replacement
    ArrayDeque<Queue<SparqlBinding>> results = (ArrayDeque<Queue<SparqlBinding>>) this.kgService
        .getSparqlQueryConstructionParameters(shaclReplacement, true);
    // Next, parse and get the query statements for the fields of interest that
    // requires sorting or filtering
    Set<String> filterFields = new HashSet<>(filters.keySet());
    Map<String, ArrayDeque<Queue<SparqlBinding>>> filterQueryPartMappings = new HashMap<>();
    Map<String, ArrayDeque<Queue<SparqlBinding>>> sortedQueryPartMappings = new HashMap<>();
    // Stores the node group name to the corresponding field for easier access
    Map<String, String> groupFieldMappings = new HashMap<>();
    while (!results.isEmpty()) {
      // Retrieve the last list of parts first, which are not mixed with node groups
      // Queue sorts them by node groups first if available, and we want to reverse
      // this process instead
      Queue<SparqlBinding> currentQueryVarsAndPaths = results.pollLast();
      Map<String, Queue<SparqlBinding>> tempFilterPartsMapping = new HashMap<>();
      Map<String, Queue<SparqlBinding>> tempSortedPartsMapping = new HashMap<>();
      // Iterate over the list to find bindings matching the field
      while (!currentQueryVarsAndPaths.isEmpty()) {
        SparqlBinding binding = currentQueryVarsAndPaths.poll();
        String fieldName = binding.getFieldValue(ShaclResource.NAME_PROPERTY);
        fieldName = QueryResource.genVariable(fieldName).getVarName();
        // If the field name is a group with a corresponding field, find its original
        // child field name
        String fieldKey = groupFieldMappings.getOrDefault(fieldName, fieldName);
        // Filters are prioritised over sorted fields to prevent duplicating query
        // statements
        if (filterFields.contains(fieldName)) {
          Queue<SparqlBinding> currentQueue = Optional.ofNullable(
              tempFilterPartsMapping.putIfAbsent(fieldKey, new ArrayDeque<>()))
              .orElseGet(() -> tempFilterPartsMapping.get(fieldKey));
          currentQueue.offer(binding);
          // Add node groups to ensure they are parsed in later iteration
          if (binding.containsField(ShaclResource.NODE_GROUP_VAR)) {
            String group = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
            group = QueryResource.genVariable(group).getVarName();
            groupFieldMappings.put(group, fieldName);
            filterFields.add(group);
          }
        } else if (sortedFields.contains(fieldName)) {
          Queue<SparqlBinding> currentQueue = Optional.ofNullable(
              tempSortedPartsMapping.putIfAbsent(fieldKey, new ArrayDeque<>()))
              .orElseGet(() -> tempSortedPartsMapping.get(fieldKey));
          currentQueue.offer(binding);
          // Add node groups to ensure they are parsed in later iteration
          if (binding.containsField(ShaclResource.NODE_GROUP_VAR)) {
            String group = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
            group = QueryResource.genVariable(group).getVarName();
            groupFieldMappings.put(group, fieldName);
            sortedFields.add(group);
          }
        }
      }
      // After iteration at the nested queue level, add them to the respective
      // mappings
      tempFilterPartsMapping.forEach((key, value) -> {
        ArrayDeque<Queue<SparqlBinding>> currentQueryParts = Optional.ofNullable(
            filterQueryPartMappings.putIfAbsent(key, new ArrayDeque<>()))
            .orElseGet(() -> filterQueryPartMappings.get(key));
        currentQueryParts.offerFirst(value);
      });
      tempSortedPartsMapping.forEach((key, value) -> {
        ArrayDeque<Queue<SparqlBinding>> currentQueryParts = Optional.ofNullable(
            sortedQueryPartMappings.putIfAbsent(key, new ArrayDeque<>()))
            .orElseGet(() -> sortedQueryPartMappings.get(key));
        currentQueryParts.offerFirst(value);
      });
    }
    // Generate statements for each mappings as individual parts
    StringBuilder sortStatementBuilder = new StringBuilder();
    sortedQueryPartMappings.forEach((key, queryParts) -> {
      sortStatementBuilder.append(QueryResource.optional(this.queryTemplateService.genWhereClause(queryParts)));
    });
    Map<String, String> outputMappings = new HashMap<>();
    if (!sortStatementBuilder.isEmpty()) {
      outputMappings.put(StringResource.SORT_KEY, sortStatementBuilder.toString());
    }
    filterQueryPartMappings.forEach((key, queryParts) -> {
      // Generate the query statements for this filter
      String clause = this.queryTemplateService.genWhereClause(queryParts)
          .replaceAll("\\s*OPTIONAL\\s*\\{(.*)\\}", "$1");
      outputMappings.put(key, clause);
    });
    return outputMappings;
  }

  /**
   * Retrieve only the specific instance and its information. This overloaded
   * method will retrieve the replacement value required from the resource ID.
   * 
   * @param nodeShapeReplacement The statement to target the node shape.
   * @param targetIds            An optional field with specific IDs to target.
   * @param sortDirectives       Sort directives to order the results.
   * @param requireLabel         Indicates if labels should be returned for all
   *                             the fields that are IRIs.
   * @param parentField          Optional parent field.
   * @param addQueryStatements   Additional query statements to be added
   * @param addVars              Optional additional variables to be included in
   *                             the query, along with their order sequence
   */
  private Queue<SparqlBinding> execGetInstances(String nodeShapeReplacement, Queue<List<String>> targetIds,
      Queue<SortDirective> sortDirectives, boolean requireLabel, ParentField parentField, String addQueryStatements,
      Map<Variable, List<Integer>> addVars) {
    if (requireLabel) {
      // Parent related parameters should be disabled
      parentField = null;
    }
    Queue<Queue<SparqlBinding>> queryVarsAndPaths = this.kgService.getSparqlQueryConstructionParameters(
        nodeShapeReplacement, requireLabel);
    String getQuery = this.queryTemplateService.genGetQuery(queryVarsAndPaths, targetIds, sortDirectives,
        parentField, addQueryStatements, addVars);
    LOGGER.debug("Querying the knowledge graph for the instances...");
    List<Variable> varSequence = this.queryTemplateService.getFieldSequence();
    // Query for direct instances
    Queue<SparqlBinding> instances = this.kgService.query(getQuery, SparqlEndpointType.MIXED);
    instances = this.kgService.combineBindingQueue(instances, this.queryTemplateService.getArrayVariables());
    // If there is a variable sequence available, add the sequence to each binding,
    if (!varSequence.isEmpty()) {
      instances.forEach(instance -> instance.addSequence(varSequence));
    }
    return instances;
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
   * Retrieve the form template for the target entity instance.
   * 
   * @param targetId      The target instance identifier OR replacement value.
   * @param resourceID    The target resource identifier for the instance class.
   * @param isReplacement Indicates if the resource ID is a replacement value
   *                      rather than a resource.
   * @param eventType     Optional event type for lifecycle related queries.
   */
  public ResponseEntity<StandardApiResponse<?>> getForm(String targetId, String resourceID,
      boolean isReplacement, LifecycleEventType eventType) {
    LOGGER.debug("Retrieving the form template for {} ...", resourceID);
    Map<String, Object> currentEntity = new HashMap<>();
    ResponseEntity<StandardApiResponse<?>> currentEntityResponse;
    if (eventType == null) {
      LOGGER.debug("Retrieving target instance of {}...", resourceID);
      currentEntityResponse = this.getInstance(targetId, resourceID, false);
    } else {
      LOGGER.debug("Retrieving target event occurrence of {}...", eventType);
      currentEntityResponse = this.getInstance(targetId, eventType);
    }
    if (currentEntityResponse.getStatusCode() == HttpStatus.OK) {
      currentEntity = (Map<String, Object>) currentEntityResponse.getBody().data().items().get(0);
    }
    return this.getForm(resourceID, isReplacement, currentEntity);
  }

  /**
   * Retrieve a blank form template for the resource.
   * 
   * @param resourceID    The target resource identifier for the instance class OR
   *                      replacement value.
   * @param isReplacement Indicates if the resource ID is a replacement value
   *                      rather than a resource.
   */
  public ResponseEntity<StandardApiResponse<?>> getForm(String resourceID, boolean isReplacement) {
    return this.getForm(resourceID, isReplacement, new HashMap<>());
  }

  /**
   * Retrieve the form template for the target entity and its information.
   * 
   * @param resourceID    The target resource identifier for the instance class OR
   *                      replacement value.
   * @param isReplacement Indicates if the resource ID is a replacement value
   *                      rather than a resource.
   * @param currentEntity Current default entity if available.
   */
  private ResponseEntity<StandardApiResponse<?>> getForm(String resourceID, boolean isReplacement,
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
    LOGGER.error(StringResource.INVALID_SHACL_ERROR_MSG);
    throw new IllegalStateException(StringResource.INVALID_SHACL_ERROR_MSG);
  }
}