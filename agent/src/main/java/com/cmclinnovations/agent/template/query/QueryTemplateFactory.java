package com.cmclinnovations.agent.template.query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.model.ShaclPropertyBinding;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.ColumnMetaPayload;
import com.cmclinnovations.agent.service.core.AuthenticationService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public abstract class QueryTemplateFactory extends AbstractQueryTemplateFactory {
  private List<ColumnMetaPayload> columns;
  protected Set<Variable> variables;
  private Map<String, Set<String>> arrayVariables;
  private final AuthenticationService authenticationService;

  protected QueryTemplateFactory(AuthenticationService authenticationService) {
    this.authenticationService = authenticationService;
  }

  /**
   * Retrieve the mappings of array variables grouped by groups.
   */
  public Map<String, Set<String>> getArrayVariables() {
    return this.arrayVariables;
  }

  /**
   * Retrieve the column metadata.
   */
  public List<ColumnMetaPayload> getColumns() {
    return this.columns;
  }

  /**
   * Retrieve the sequence of the variables.
   */
  protected void reset() {
    this.columns = new ArrayList<>();
    this.columns.add(new ColumnMetaPayload(QueryResource.ID_KEY, QueryResource.LITERAL_TYPE, ShaclResource.XSD_STRING));
    this.variables = new HashSet<>();
    this.arrayVariables = new HashMap<>();
  }

  /**
   * Appends additional patterns to the query template, and return its stringified
   * version.
   * 
   * @param selectTemplate     Select query template for all users.
   * @param additionalPatterns Additional graph patterns as string.
   */
  protected String appendAdditionalPatterns(SelectQuery selectTemplate, String additionalPatterns) {
    String selectQuery = selectTemplate.getQueryString();
    selectQuery = StringResource.replaceLast(selectQuery, "}\n",
        additionalPatterns + "}\n");
    return selectQuery;
  }

  /**
   * Generates the contents for the SPARQL WHERE clause based on the SHACL
   * restrictions.
   * 
   * @param targetClass            Target class.
   * @param shaclNodeShapeBindings The node shapes queried from SHACL
   *                               restrictions.
   */
  protected SelectQuery genWhereClauseContent(String targetClass, Queue<Queue<SparqlBinding>> shaclNodeShapeBindings) {
    this.reset();
    Map<String, Map<String, ShaclPropertyBinding>> propertyBindingMap = this.parseNodeShapes(shaclNodeShapeBindings);
    SelectQuery selectTemplate = genSelectTemplate(targetClass);
    return this.write(selectTemplate, propertyBindingMap);
  }

  /**
   * Generates a new select template based on the class.
   * 
   * @param targetClass Target class.
   */
  private SelectQuery genSelectTemplate(String targetClass) {
    return QueryResource.getSelectQuery()
        .distinct()
        .where(QueryResource.IRI_VAR.isA(Rdf.iri(targetClass)));
  }

  /**
   * Parse the node shape bindings into a mapping containing their property
   * shapes.
   * 
   * @param shaclNodeShapeBindings Target node shape inputs.
   */
  protected Map<String, Map<String, ShaclPropertyBinding>> parseNodeShapes(
      Queue<Queue<SparqlBinding>> shaclNodeShapeBindings) {
    Map<String, Map<String, ShaclPropertyBinding>> shaclPropertyShapesMap = new HashMap<>();
    Set<String> referencedGroupIdentifiers = new HashSet<>();
    Map<String, Set<String>> groupBranchMappings = new HashMap<>();
    Map<String, ShaclPropertyBinding> groupPropertyMap = new HashMap<>();
    Map<String, ShaclPropertyBinding> indivPropertyMap = new HashMap<>();
    Set<String> userRoles = this.authenticationService.getUserRoles();

    while (!shaclNodeShapeBindings.isEmpty()) {
      Queue<SparqlBinding> shaclPropertyShapeBindings = shaclNodeShapeBindings.poll();
      while (!shaclPropertyShapeBindings.isEmpty()) {
        SparqlBinding binding = shaclPropertyShapeBindings.poll();
        String property = binding.getFieldValue(ShaclResource.NAME_PROPERTY);
        String shGroup = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
        String branch = binding.getFieldValue(ShaclResource.BRANCH_VAR);
        String permittedRoles = binding.getFieldValue(ShaclResource.ROLE_PROPERTY);

        // Any nested id properties in sh:node should be ignored
        if (shGroup != null && property.equals(QueryResource.ID_KEY)) {
          continue;
        }

        // Skip this iteration if the results show an empty branch
        if (property == null) {
          continue;
        }

        String mappingKey = ShaclResource.getMappingKey(property, shGroup, branch);

        // If authentication is enabled along with associated roles BUT the user is
        // unauthorised
        if (this.authenticationService.isAuthenticationEnabled() && permittedRoles != null
            && this.authenticationService.isUnauthorised(userRoles, permittedRoles)) {
          // Remove any existing bindings if they exist
          if (indivPropertyMap.containsKey(mappingKey)) {
            indivPropertyMap.remove(mappingKey);
          }
          // Skip this iteration if permission is not given
          continue;
        }

        ShaclPropertyBinding propertyBinding;
        // For existing bindings
        if (indivPropertyMap.containsKey(mappingKey)) {
          // Retrieve existing mappings to append the predicate
          propertyBinding = indivPropertyMap.get(mappingKey);
          propertyBinding.appendPred(binding);
        } else {
          // For new bindings
          propertyBinding = new ShaclPropertyBinding(binding);

          // Add a new group reference IF it isnt an array
          if (!propertyBinding.getGroup().isEmpty()) {
            // Group references are stored as group + branch name
            String groupRefKey = ShaclResource.getMappingKey(propertyBinding.getGroup(), propertyBinding.getBranch());
            // Create a new group in the group branch mappings; only useful for getting
            // filter options with SHACL property groups; use white spaces for variable
            groupBranchMappings.putIfAbsent(shGroup, new HashSet<>());
            referencedGroupIdentifiers.add(groupRefKey);

            // Prepend group sequence to the individual property
            ShaclPropertyBinding groupBinding = indivPropertyMap.get(groupRefKey);
            propertyBinding.prependSequence(groupBinding.getSequence());
          }

          // Store the group references under the group branch mappings; only useful for
          // getting filter options with SHACL property groups
          if (groupBranchMappings.keySet().contains(property)) {
            groupBranchMappings.get(property).add(mappingKey);
          }
        }
        // Store the new/updated in the mappings
        indivPropertyMap.put(mappingKey, propertyBinding);
      }
    }

    // Move the group bindings over to the right mappings
    referencedGroupIdentifiers.forEach(groupIdentifier -> {
      groupPropertyMap.put(groupIdentifier, indivPropertyMap.remove(groupIdentifier));
    });
    // Remove the unused groups in the indiv property maps; useful mainly for
    // filters
    groupBranchMappings.values().forEach((groupBranchMapping) -> groupBranchMapping
        .forEach(indivPropertyMap::remove));
    // Store results
    shaclPropertyShapesMap.put(ShaclResource.GROUP_PROPERTY, groupPropertyMap);
    shaclPropertyShapesMap.put(ShaclResource.PROPERTY_PROPERTY, indivPropertyMap);
    return shaclPropertyShapesMap;
  }

  /**
   * Appends the graph patterns parsed from the sorted shape mappings to the
   * SELECT template.
   * 
   * @param selectTemplate   SELECT query template to build the query
   * @param propertyShapeMap The sorted mappings for the SHACL property shapes.
   */
  protected SelectQuery write(SelectQuery selectTemplate,
      Map<String, Map<String, ShaclPropertyBinding>> propertyShapeMap) {
    Map<String, List<GraphPattern>> accumulatedStatementsByGroup = new HashMap<>();
    Map<String, List<GraphPattern>> branchStatementMap = new HashMap<>();
    Set<ColumnMetaPayload> uniqueColumns = new LinkedHashSet<>();
    Map<String, List<Integer>> varSequence = new HashMap<>();

    // Iterate over each property to add either directly or to the associated group
    // or branch
    propertyShapeMap.get(ShaclResource.PROPERTY_PROPERTY).values().forEach(propBinding -> {
      String group = propBinding.getGroup();
      boolean hasGroupPatterns = propertyShapeMap.get(ShaclResource.GROUP_PROPERTY).containsKey(group)
          && propertyShapeMap.get(ShaclResource.GROUP_PROPERTY).get(group).hasPaths();
      List<GraphPattern> content = propBinding.write(false, hasGroupPatterns);
      if (propBinding.isOptional()) {
        GraphPatternNotTriples optionalPattern = GraphPatterns.optional(
            content.toArray(new GraphPattern[0]));
        // After wrapping all patterns, reset them and add to the content again
        content = new ArrayList<>();
        content.add(optionalPattern);
      }
      // For groupless properties, append them directly if there is no branch
      if (group.isEmpty()) {
        if (propBinding.getBranch().isEmpty()) {
          selectTemplate.where(content.toArray(new GraphPattern[0]));
        } else {
          // Store them in a separate branch mappings if a branch is involved
          branchStatementMap.computeIfAbsent(propBinding.getBranch(), k -> new ArrayList<>()).addAll(content);
        }
        if (propBinding.isArray()) {
          // Store individual array variables as well
          this.arrayVariables.computeIfAbsent(propBinding.getName().getVarName(), k -> new HashSet<>())
              .add(propBinding.getName().getVarName());
        }
      } else {
        this.variables.remove(QueryResource.genVariable(group));
        // If there is an associated group, store the content to the associated group in
        // the temp mappings; Note that a group may have multiple fields, so each
        // content should be appended to the previous batch
        String mappingKey = ShaclResource.getMappingKey(group, propBinding.getBranch());
        accumulatedStatementsByGroup.computeIfAbsent(mappingKey, k -> new ArrayList<>()).addAll(content);
        // Store array variables in their groups
        this.arrayVariables.computeIfAbsent(mappingKey, k -> new HashSet<>()).add(propBinding.getName().getVarName());
      }
      // Store the variable for individual properties only
      this.variables.add(propBinding.getName());
      varSequence.put(propBinding.getName().getVarName(), propBinding.getSequence());
      uniqueColumns.add(propBinding.getColumnMeta());
    });

    // Handle group query parsing
    Map<String, GraphPattern> arrayStatementsMap = new HashMap<>();
    Map<String, Boolean> arrayGroupOptionalityMap = new HashMap<>(); // this tracks if all array insides are optional
    propertyShapeMap.get(ShaclResource.GROUP_PROPERTY).forEach((key, propBinding) -> {
      List<GraphPattern> content = propBinding.write(true, false);
      content.addAll(accumulatedStatementsByGroup.get(key));

      // For arrays, attach them to a second map for further parsing
      if (propBinding.isArray()) {
        String propKey = propBinding.getBranch().isEmpty() ? ShaclResource.PROPERTY_PROPERTY
            : propBinding.getBranch();
        GraphPatternNotTriples graphPatterns = GraphPatterns.and(content.toArray(new GraphPattern[0]));
        GraphPatternNotTriples currentArrayPatterns = arrayStatementsMap.containsKey(propKey)
            ? GraphPatterns.union(arrayStatementsMap.get(propKey), graphPatterns)
            : graphPatterns;
        arrayStatementsMap.put(propKey, currentArrayPatterns);
        // Initial entry takes its own optionality. Subsequent entries use logical AND.
        arrayGroupOptionalityMap.put(propKey,
            arrayGroupOptionalityMap.getOrDefault(propKey, true) && propBinding.isOptional());
        return; // End loop early for arrays
      }
      // Optional clauses only for groups
      if (propBinding.isOptional()) {
        GraphPatternNotTriples optionalPattern = GraphPatterns.optional(
            content.toArray(new GraphPattern[0]));
        // After wrapping all patterns, reset them and add to the content again
        content = new ArrayList<>();
        content.add(optionalPattern);
      }
      // For simple groups, directly attach to the query block or branch
      if (propBinding.getBranch().isEmpty()) {
        selectTemplate.where(GraphPatterns.and(content.toArray(new GraphPattern[0])));
      } else {
        // Store them in a separate branch mappings if a branch is involved
        branchStatementMap.computeIfAbsent(propBinding.getBranch(), k -> new ArrayList<>()).addAll(content);
      }
      // Remove non-array variables; This method is only accessed for non-arrays as
      // arrays will have ended the loop earlier
      this.arrayVariables.remove(key);
    });
    // Handle array parsing
    arrayStatementsMap.forEach((key, contents) -> {
      // If all arrays in this group were optional, wrap the whole union/and block in
      // OPTIONAL
      GraphPattern finalPattern = arrayGroupOptionalityMap.getOrDefault(key, false)
          ? GraphPatterns.optional(contents)
          : contents;
      if (key.equals(ShaclResource.PROPERTY_PROPERTY)) {
        selectTemplate.where(finalPattern);
      } else {
        branchStatementMap.computeIfAbsent(key, k -> new ArrayList<>()).add(finalPattern);
      }
    });

    // Handle branch parsing - Use OPTIONAL
    if (!branchStatementMap.isEmpty()) {
      branchStatementMap.values().forEach(branchContent -> {
        GraphPatternNotTriples branchPattern = GraphPatterns.and(branchContent.toArray(new GraphPattern[0]));
        selectTemplate.where(GraphPatterns.optional(branchPattern));
      });
    }
    this.columns.addAll(uniqueColumns.stream()
        .sorted(ColumnMetaPayload.lexComparator(varSequence))
        .toList());
    return selectTemplate;
  }
}
