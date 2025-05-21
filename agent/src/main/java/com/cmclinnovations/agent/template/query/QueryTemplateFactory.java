package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import com.cmclinnovations.agent.model.ShaclPropertyBinding;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public abstract class QueryTemplateFactory extends AbstractQueryTemplateFactory {
  private boolean hasEmptyBranches;
  private List<String> sortedVars;
  protected Set<String> variables;
  protected Map<String, List<Integer>> varSequence;

  /**
   * Retrieve the sequence of the variables.
   */
  public List<String> getSequence() {
    return this.sortedVars;
  }

  /**
   * Set the sequence of the variables.
   */
  protected void setSequence(List<String> sequence) {
    this.sortedVars = sequence;
  }

  /**
   * Retrieve the sequence of the variables.
   */
  protected void reset() {
    this.hasEmptyBranches = false;
    this.sortedVars = new ArrayList<>();
    this.variables = new HashSet<>();
    this.varSequence = new HashMap<>();
  }

  /**
   * Generates two federated queries with a replaceable endpoint [endpoint]. The
   * first query is for mixed endpoints; the second query is for non-ontop
   * endpoints.
   * 
   * @param selectVariables The variables to be selected in a SPARQL SELECT query.
   * @param whereClause     The contents for the SPARQL query's WHERE clause.
   * @param targetClass     Target class to reach.
   */
  protected Queue<String> genFederatedQuery(String selectVariables, String whereClause, String targetClass) {
    Queue<String> results = new ArrayDeque<>();
    String iriClass = StringResource.parseIriForQuery(targetClass);
    // For mixed endpoints with Ontop which does not support property paths
    results.offer(
        "SELECT DISTINCT " + selectVariables + " WHERE {?iri a " + iriClass + ShaclResource.FULL_STOP
            + whereClause.replace(StringResource.REPLACEMENT_PLACEHOLDER, "")
            + "}");
    // For SPARQL endpoints
    results.offer(
        "SELECT DISTINCT " + selectVariables + " WHERE {?iri a/rdfs:subClassOf* " + iriClass + ShaclResource.FULL_STOP
            + whereClause.replace(StringResource.REPLACEMENT_PLACEHOLDER, "/rdfs:subClassOf*") + "}");
    return results;
  }

  /**
   * Generates the contents for the SPARQL WHERE clause based on the SHACL
   * restrictions.
   * 
   * @param shaclNodeShapeBindings The node shapes queried from SHACL
   *                               restrictions.
   */
  protected String genWhereClauseContent(Queue<Queue<SparqlBinding>> shaclNodeShapeBindings) {
    this.reset();
    Map<String, Map<String, ShaclPropertyBinding>> propertyBindingMap = this.parseNodeShapes(shaclNodeShapeBindings);
    return this.write(propertyBindingMap);
  }

  /**
   * Parse the node shape bindings into a mapping containing their property
   * shapes.
   * 
   * @param shaclNodeShapeBindings Target node shape inputs.
   */
  private Map<String, Map<String, ShaclPropertyBinding>> parseNodeShapes(
      Queue<Queue<SparqlBinding>> shaclNodeShapeBindings) {
    Map<String, Map<String, ShaclPropertyBinding>> shaclPropertyShapesMap = new HashMap<>();
    Set<String> referencedGroupIdentifiers = new HashSet<>();
    Map<String, ShaclPropertyBinding> groupPropertyMap = new HashMap<>();
    Map<String, ShaclPropertyBinding> indivPropertyMap = new HashMap<>();

    while (!shaclNodeShapeBindings.isEmpty()) {
      Queue<SparqlBinding> shaclPropertyShapeBindings = shaclNodeShapeBindings.poll();
      while (!shaclPropertyShapeBindings.isEmpty()) {
        SparqlBinding binding = shaclPropertyShapeBindings.poll();
        String property = binding.getFieldValue(ShaclResource.NAME_PROPERTY);
        String shGroup = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
        String branch = binding.getFieldValue(ShaclResource.BRANCH_VAR);

        // Any nested id properties in sh:node should be ignored
        if (binding.getFieldValue(ShaclResource.NODE_GROUP_VAR) != null && property.equals("id")) {
          continue;
        }

        // Skip this iteration if the results show an empty branch
        if (property == null) {
          this.hasEmptyBranches = true;
          continue;
        }

        String mappingKey = ShaclResource.getMappingKey(property, shGroup, branch);

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
            referencedGroupIdentifiers.add(groupRefKey);
          }

          // Parse ordering only for label query, as we require the heading order in csv
          // Order field will not exist for non-label query
          if (binding.containsField(ShaclResource.ORDER_PROPERTY)) {
            int order = Integer.parseInt(binding.getFieldValue(ShaclResource.ORDER_PROPERTY));
            List<Integer> orders = new ArrayList<>();
            if (shGroup != null) {
              orders = this.varSequence.get(shGroup);
            }
            orders.add(order);
            this.varSequence.put(property, orders);
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

    // Store results
    shaclPropertyShapesMap.put(ShaclResource.GROUP_PROPERTY, groupPropertyMap);
    shaclPropertyShapesMap.put(ShaclResource.PROPERTY_PROPERTY, indivPropertyMap);
    return shaclPropertyShapesMap;
  }

  /**
   * Writes the sorted shape mappings into a SPARQL WHERE query block.
   * 
   * @param propertyShapeMap The mappings for SHACL property shape that has been
   *                         sorted.
   */
  private String write(Map<String, Map<String, ShaclPropertyBinding>> propertyShapeMap) {
    StringBuilder queryBlock = new StringBuilder();
    Map<String, StringBuilder> accumulatedStatementsByGroup = new HashMap<>();
    Map<String, StringBuilder> branchStatementMap = new HashMap<>();
    // Iterate over each property to add either directly or to the associated group
    // or branch
    propertyShapeMap.get(ShaclResource.PROPERTY_PROPERTY).values().forEach(propBinding -> {
      String group = propBinding.getGroup();
      String content = propBinding.write(false);
      if (propBinding.isOptional()) {
        content = StringResource.genOptionalClause(content);
      }
      // For groupless properties, append them directly if there is no branch
      if (group.isEmpty()) {
        if (propBinding.getBranch().isEmpty()) {
          queryBlock.append(content);
        } else {
          // Store them in a separate branch mappings if a branch is involved
          branchStatementMap.computeIfAbsent(propBinding.getBranch(), k -> new StringBuilder()).append(content);
        }
      } else {
        this.varSequence.remove(group);
        // If there is an associated group, store the content to the associated group in
        // the temp mappings; Note that a group may have multiple fields, so each
        // content should be appended to the previous batch
        String mappingKey = ShaclResource.getMappingKey("", group, propBinding.getBranch());
        accumulatedStatementsByGroup.computeIfAbsent(mappingKey, k -> new StringBuilder()).append(content);
      }
      // Store the variable for individual properties only
      this.variables.add(ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(propBinding.getName()));
    });

    // Handle group query parsing
    Map<String, StringBuilder> arrayStatementsMap = new HashMap<>();
    propertyShapeMap.get(ShaclResource.GROUP_PROPERTY).forEach((key, propBinding) -> {
      String content = propBinding.write(true) + accumulatedStatementsByGroup.get(key);

      // For arrays, attach them to a second map for further parsing
      if (propBinding.isArray()) {
        String propKey = propBinding.getBranch().isEmpty() ? ShaclResource.PROPERTY_PROPERTY
            : propBinding.getBranch();
        StringBuilder tempArrayBuilder = arrayStatementsMap.computeIfAbsent(propKey, k -> new StringBuilder());
        tempArrayBuilder.append(tempArrayBuilder.isEmpty() ? "" : ShaclResource.UNION_OPERATOR)
            .append(content);
        return; // End loop early for arrays
      }
      // Optional clauses only for groups
      if (propBinding.isOptional()) {
        content = StringResource.genOptionalClause(content);
      }
      // For simple groups, directly attach to the query block or branch
      if (propBinding.getBranch().isEmpty()) {
        queryBlock.append(StringResource.genGroupGraphPattern(content));
      } else {
        // Store them in a separate branch mappings if a branch is involved
        branchStatementMap.computeIfAbsent(propBinding.getBranch(), k -> new StringBuilder()).append(content);
      }
    });
    // Handle array parsing
    arrayStatementsMap.forEach((key, builder) -> {
      String content = StringResource.genGroupGraphPattern(builder.toString());
      if (key.equals(ShaclResource.PROPERTY_PROPERTY)) {
        queryBlock.append(content);
      } else {
        branchStatementMap.computeIfAbsent(key, k -> new StringBuilder()).append(content);
      }
    });

    // Handle branch parsing
    if (!branchStatementMap.isEmpty()) {
      StringBuilder tempBranchBuilder = new StringBuilder();
      branchStatementMap.values().forEach(queryLine -> {
        tempBranchBuilder.append(tempBranchBuilder.isEmpty() ? "" : ShaclResource.UNION_OPERATOR)
            .append(queryLine);
      });
      String content = StringResource.genGroupGraphPattern(tempBranchBuilder.toString());
      // Wrap the branches in an optional clause IF there is an empty branch
      if (this.hasEmptyBranches) {
        content = StringResource.genOptionalClause(content);
      }
      queryBlock.append(content);
    }
    return queryBlock.toString();
  }
}
