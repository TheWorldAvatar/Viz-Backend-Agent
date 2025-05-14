package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlQueryLine;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public abstract class QueryTemplateFactory extends AbstractQueryTemplateFactory {
  private List<String> sortedVars;
  protected Set<String> variables;
  protected Map<String, List<Integer>> varSequence;
  private static final String ID_PATTERN_1 = "<([^>]+)>/\\^<\\1>";
  private static final String ID_PATTERN_2 = "\\^<([^>]+)>/<\\1>";
  private static final String NAME_VAR = "name";
  private static final String INSTANCE_CLASS_VAR = "instance_clazz";
  private static final String ORDER_VAR = "order";
  private static final String BRANCH_VAR = "branch";
  private static final String IS_OPTIONAL_VAR = "isoptional";
  private static final String IS_CLASS_VAR = "isclass";
  private static final String SUBJECT_VAR = "subject";
  private static final String PATH_PREFIX = "_proppath";
  private static final String MULTIPATH_VAR = "multipath";
  private static final String MULTI_NAME_PATH_VAR = "name_multipath";
  private static final String RDF_TYPE = "rdf:type";
  private static final String REPLACEMENT_PLACEHOLDER = "[replace]";

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
            + whereClause.replace(REPLACEMENT_PLACEHOLDER, "")
            + "}");
    // For SPARQL endpoints
    results.offer(
        "SELECT DISTINCT " + selectVariables + " WHERE {?iri a/rdfs:subClassOf* " + iriClass + ShaclResource.FULL_STOP
            + whereClause.replace(REPLACEMENT_PLACEHOLDER, "/rdfs:subClassOf*") + "}");
    return results;
  }

  /**
   * Sort and categorise the bindings into the default and optional queues for
   * processing.
   * 
   * @param nestedBindings   The bindings queried from SHACL restrictions that
   *                         should be queried in template.
   * @param queryLineOutputs Mappings storing the parsed query lines for query
   *                         construction.
   */
  protected void sortBindings(Queue<Queue<SparqlBinding>> nestedBindings, Map<String, String> queryLineOutputs) {
    this.reset();
    Map<String, SparqlQueryLine> queryLineMappings = new HashMap<>();
    Map<String, SparqlQueryLine> groupQueryLineMappings = new HashMap<>();
    while (!nestedBindings.isEmpty()) {
      Queue<SparqlBinding> bindings = nestedBindings.poll();
      Queue<String> nodeGroups = new ArrayDeque<>();
      while (!bindings.isEmpty()) {
        SparqlBinding binding = bindings.poll();
        String multiPartPredicate = this.getPredicate(binding, MULTIPATH_VAR);
        String multiPartLabelPredicate = this.getPredicate(binding, MULTI_NAME_PATH_VAR);
        this.genQueryLine(binding, multiPartPredicate, multiPartLabelPredicate, nodeGroups, queryLineMappings);
      }
      // If there are any node group, these should be removed from the mappings
      // and place into a dedicated group mapping
      while (!nodeGroups.isEmpty()) {
        String nodeGroup = nodeGroups.poll();
        if (!groupQueryLineMappings.containsKey(nodeGroup)) {
          groupQueryLineMappings.put(nodeGroup, queryLineMappings.get(nodeGroup));
          queryLineMappings.remove(nodeGroup);
          this.varSequence.remove(nodeGroup);
        }
      }
    }
    this.parseQueryLines(queryLineMappings, groupQueryLineMappings, queryLineOutputs);
  }

  /**
   * Gets the predicate associated with the input variable. Returns an empty
   * string if not found.
   * 
   * @param binding              An individual binding queried from SHACL
   *                             restrictions that should be queried in template.
   * @param propertyPathVariable The current property path part variable name.
   */
  private String getPredicate(SparqlBinding binding, String propertyPathVariable) {
    if (binding.containsField(propertyPathVariable)) {
      String predPath = binding.getFieldValue(propertyPathVariable);
      // Do not process any paths without the http protocol as it is likely to be a
      // blank node
      if (predPath.startsWith("http")) {
        String parsedPredPath = StringResource.parseIriForQuery(predPath);
        // Check if there are path prefixes in the SHACL restrictions
        // Each clause should be separated as we may use other path prefixes in future
        if (binding.containsField(propertyPathVariable + PATH_PREFIX)) {
          // For inverse paths, simply append a ^ before the parsed IRI
          if (binding.getFieldValue(propertyPathVariable + PATH_PREFIX)
              .equals(ShaclResource.SHACL_PREFIX + "inversePath")) {
            return "^" + parsedPredPath;
          }
        }
        // If no path prefixes are available, simply return the <predicate>
        return parsedPredPath;
      }
    }
    return "";
  }

  /**
   * Generates a query line from the input binding and store it within the target
   * mappings.
   * 
   * @param binding                 An individual binding queried from SHACL
   *                                restrictions that should be queried in
   *                                template.
   * @param multiPartPredicate      The current predicate part value for the main
   *                                property.
   * @param multiPartLabelPredicate The current predicate part value to reach the
   *                                label of the property.
   * @param nodeGroups              Stores any node group property.
   * @param queryLineMappings       The target mappings storing the generated
   *                                query lines.
   */
  private void genQueryLine(SparqlBinding binding, String multiPartPredicate, String multiPartLabelPredicate,
      Queue<String> nodeGroups, Map<String, SparqlQueryLine> queryLineMappings) {
    String shNodeGroupName = binding.getFieldValue(StringResource.NODE_GROUP_VAR);
    String propertyName = binding.getFieldValue(NAME_VAR);
    // Any nested id properties in sh:node should be ignored
    if (shNodeGroupName != null && propertyName.equals("id")) {
      return;
    }

    boolean isClassVar = Boolean.parseBoolean(binding.getFieldValue(IS_CLASS_VAR));
    String instanceClass = binding.getFieldValue(INSTANCE_CLASS_VAR);
    String branchName = binding.getFieldValue(BRANCH_VAR);
    String mappingKey = ShaclResource.getMappingKey(propertyName, shNodeGroupName);
    mappingKey = ShaclResource.getMappingKey(mappingKey, branchName);
    // For existing mappings,
    if (queryLineMappings.containsKey(mappingKey)) {
      SparqlQueryLine currentQueryLine = queryLineMappings.get(mappingKey);
      // Update the mapping with the extended predicates
      queryLineMappings.put(mappingKey,
          new SparqlQueryLine(currentQueryLine.property(), instanceClass, currentQueryLine.nestedClass(), currentQueryLine.subject(),
              parsePredicate(currentQueryLine.predicate(), multiPartPredicate),
              parsePredicate(currentQueryLine.labelPredicate(), multiPartLabelPredicate),
              currentQueryLine.subjectFilter(), currentQueryLine.branch(), currentQueryLine.isOptional(), isClassVar));
    } else {
      // When initialising a new query line
      String subjectVar = binding.containsField(SUBJECT_VAR) ? binding.getFieldValue(SUBJECT_VAR) : "";
      String nestedClass = binding.containsField(StringResource.NESTED_CLASS_VAR)
          ? binding.getFieldValue(StringResource.NESTED_CLASS_VAR)
          : "";
      boolean isOptional = Boolean.parseBoolean(binding.getFieldValue(IS_OPTIONAL_VAR));
      // Parse ordering only for label query, as we require the heading order in csv
      // Order field will not exist for non-label query
      if (binding.containsField(ORDER_VAR)) {
        int order = Integer.parseInt(binding.getFieldValue(ORDER_VAR));
        List<Integer> orders = new ArrayList<>();
        if (shNodeGroupName != null) {
          orders = this.varSequence.get(shNodeGroupName);
        }
        orders.add(order);
        this.varSequence.put(propertyName, orders);
      }
      String fieldSubject = LifecycleResource.IRI_KEY;
      if (shNodeGroupName != null) {
        fieldSubject = shNodeGroupName;
        // Append branch name for groups as well if required
        nodeGroups.offer(ShaclResource.getMappingKey(shNodeGroupName, branchName));
      }
      queryLineMappings.put(mappingKey,
          new SparqlQueryLine(propertyName, instanceClass, nestedClass, fieldSubject, multiPartPredicate,
              multiPartLabelPredicate, subjectVar, branchName, isOptional, isClassVar));
    }
  }

  /**
   * Parses the predicate to concatenante the current and next predicate in a
   * SPARQL compliant format.
   * 
   * @param currentPredicate Current predicate in the existing mapping
   * @param nextPredicate    Next predicate for appending.
   */
  private String parsePredicate(String currentPredicate, String nextPredicate) {
    if (nextPredicate.isEmpty()) {
      return currentPredicate;
    }
    if (currentPredicate.isEmpty()) {
      return nextPredicate;
    } else {
      return currentPredicate + "/" + nextPredicate;
    }
  }

  /**
   * Parses the triple query line into the mappings at the class level.
   * 
   * @param queryLineMappings      The input unparsed query lines
   * @param groupQueryLineMappings The unparsed query lines for node groups.
   * @param queryLineOutputs       Mappings storing the parsed query lines for
   *                               query
   *                               construction.
   */
  private void parseQueryLines(Map<String, SparqlQueryLine> queryLineMappings,
      Map<String, SparqlQueryLine> groupQueryLineMappings, Map<String, String> queryLineOutputs) {
    Map<String, String> branchQueryLines = new HashMap<>();
    // Store group lines into two separate maps to indicate which ones require an
    // OPTIONAL wrapper
    Map<String, String> groupQueryLines = new HashMap<>();
    Map<String, String> groupOptionalQueryLines = new HashMap<>();
    groupQueryLineMappings.forEach((key, queryLine) -> {
      StringBuilder currentLine = new StringBuilder();
      StringResource.appendTriple(currentLine,
          ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.subject()),
          queryLine.predicate(),
          ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.property()));
      // If there is a sh:node targetClass property available, append the class
      // restriction
      if (!queryLine.nestedClass().isEmpty()) {
        StringResource.appendTriple(currentLine,
            ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.property()),
            RDF_TYPE + REPLACEMENT_PLACEHOLDER,
            StringResource.parseIriForQuery(queryLine.nestedClass()));
      }
      if (queryLine.isOptional()) {
        groupOptionalQueryLines.put(key, currentLine.toString());
      } else {
        groupQueryLines.put(key, currentLine.toString());
      }
    });
    queryLineMappings.values().forEach(queryLine -> {
      // Parse and generate a query line for the current line
      StringBuilder currentLine = new StringBuilder();
      String jointPredicate = parsePredicate(queryLine.predicate(), queryLine.labelPredicate());
      // Add a final rdfs:label if it is a class to retrieve the label
      if (queryLine.isClazz()) {
        jointPredicate = parsePredicate(jointPredicate, ShaclResource.RDFS_LABEL_PREDICATE);
      }
      // If query line is id with a roundabout loop to target itself
      if (queryLine.property().equals("id") && this.verifySelfTargetIdField(jointPredicate)) {
        // Simply bind the iri as the id
        currentLine.append("BIND(?iri AS ?id)");
      } else {
        StringResource.appendTriple(currentLine,
            ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.subject()), jointPredicate,
            // Note to add a _ to the property
            ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.property()));
      }
      // If this is an instance, add a statement targeting the exact class
      if (queryLine.instanceClass() != null) {
        // Inverse the label predicate if it exist
        String inverseLabelPred = !queryLine.labelPredicate().isEmpty() ? "^(" + queryLine.labelPredicate() + ")/" : "";
        StringResource.appendTriple(currentLine,
            ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.property()),
            inverseLabelPred + RDF_TYPE, StringResource.parseIriForQuery(queryLine.instanceClass()));
      }
      String lineOutput;
      // Optional lines should be parsed differently
      if (queryLine.isOptional()) {
        // If the value must conform to a specific subject variable,
        // a filter needs to be added directly to the same optional clause
        if (!queryLine.subjectFilter().isEmpty()) {
          currentLine.append("FILTER(STR(?")
              .append(StringResource.parseQueryVariable(queryLine.property()))
              .append(") = \"")
              .append(queryLine.subjectFilter())
              .append("\")");
        }
        lineOutput = StringResource.genOptionalClause(currentLine.toString());
      } else {
        // Non-optional lines does not require special effects
        lineOutput = currentLine.toString();
      }

      // Branches should be added as a separate bunch
      if (queryLine.branch() != null) {
        // WIP: Branching has not yet been tested with groupings robustly
        branchQueryLines.compute(queryLine.branch(), (k, previousLine) -> {
          // Append previous line only if there are previous lines, else, it will be an
          // empty string added
          String prevLine = previousLine == null ? "" : previousLine;
          // For non-iri subjects, append the associated group line once
          if (!queryLine.subject().equals(LifecycleResource.IRI_KEY)) {
            String mappingKey = ShaclResource.getMappingKey(queryLine.subject(), queryLine.branch());
            String groupedOutput = groupQueryLines.getOrDefault(mappingKey, "") + lineOutput;
            groupQueryLines.remove(mappingKey); // Remove to prevent side effects
            return prevLine + groupedOutput;
          }
          return prevLine + lineOutput;
        });
        // If the lines belong to a specific group, add them together
      } else if (groupQueryLines.containsKey(queryLine.subject())) {
        groupQueryLines.put(queryLine.subject(), groupQueryLines.get(queryLine.subject()) + lineOutput);
      } else if (groupOptionalQueryLines.containsKey(queryLine.subject())) {
        groupOptionalQueryLines.put(queryLine.subject(), groupOptionalQueryLines.get(queryLine.subject()) + lineOutput);
      } else {
        // Non-branch query lines will be added directly
        queryLineOutputs.put(queryLine.property(), lineOutput);
      }
      // Always append current property for both branches and individual fields
      // Grouped fields are not part of the query line parsing
      this.variables.add(ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(queryLine.property()));
    });
    // If there are non-branch group query lines, add them to the query lines
    if (!groupQueryLines.isEmpty()) {
      queryLineOutputs.putAll(groupQueryLines);
    }
    if (!groupOptionalQueryLines.isEmpty()) {
      // For optional groups, wrap them in an optional statement
      groupOptionalQueryLines.entrySet().stream()
          .forEach(entry -> {
            String optionalStatement = "OPTIONAL{" + entry.getValue() + "}";
            queryLineOutputs.put(entry.getKey(), optionalStatement);
          });
    }
    // Add branch query block if there are any branches
    if (!branchQueryLines.isEmpty()) {
      StringBuilder branchBlock = new StringBuilder();
      branchQueryLines.values().forEach(branch -> {
        // Add a UNION if there is a previous branch
        if (!branchBlock.isEmpty()) {
          branchBlock.append(" UNION ");
        }
        // If there is only one branch, it should be an optional clause instead
        if (branchQueryLines.size() == 1) {
          branchBlock.append(StringResource.genOptionalClause(branch));
        } else {
          branchBlock.append("{").append(branch).append("}");
        }
      });
      queryLineOutputs.put(ShaclResource.BRANCH_KEY, branchBlock.toString());
    }
  }

  /**
   * Verifies if the ID field is targeting the IRI.
   * 
   * @param predicate The predicate string of the ID field.
   */
  private boolean verifySelfTargetIdField(String predicate) {
    // Compile the potential patterns to match
    Pattern pattern1 = Pattern.compile(ID_PATTERN_1);
    Pattern pattern2 = Pattern.compile(ID_PATTERN_2);

    // Create matchers for both patterns
    Matcher matcher1 = pattern1.matcher(predicate);
    Matcher matcher2 = pattern2.matcher(predicate);

    // Return true if input matches either pattern 1 or pattern 2
    return matcher1.matches() || matcher2.matches();
  }
}
