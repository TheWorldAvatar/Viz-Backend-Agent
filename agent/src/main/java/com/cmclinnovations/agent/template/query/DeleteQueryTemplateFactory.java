package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatternNotTriples;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfSubject;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class DeleteQueryTemplateFactory extends AbstractQueryTemplateFactory {
  private Map<String, String> anonymousVariableMappings;
  private Queue<Queue<GraphPattern>> whereClauseBranchPatterns;
  private final JsonLdService jsonLdService;
  private static final Logger LOGGER = LogManager.getLogger(DeleteQueryTemplateFactory.class);
  private String selectedBranchName;

  /**
   * Constructs a new query template factory.
   * 
   */
  public DeleteQueryTemplateFactory(JsonLdService jsonLdService) {
    this.jsonLdService = jsonLdService;
    this.whereClauseBranchPatterns = new ArrayDeque<>();
  }

  /**
   * Generate a SPARQL query template to delete the target instance.
   * 
   * @param params     An object containing two parameters to write, namely:
   *                   rootNode - The root node of contents to parse into a
   *                   template
   *                   targetId - The target instance IRI.
   * @param branchName Optional branch name to filter which branch to process (can
   *                   be null)
   */
  public String write(QueryTemplateFactoryParameters params, String branchName) {
    this.reset();
    this.selectedBranchName = branchName;
    LOGGER.debug("=== DeleteQueryTemplateFactory.write: selectedBranchName set to = {}", this.selectedBranchName);

    ModifyQuery deleteTemplate = this.genDeleteTemplate(params.targetId());
    this.recursiveParseNode(deleteTemplate, null, params.rootNode());
    this.addBranches(deleteTemplate);

    return deleteTemplate.getQueryString();
  }

  // Keep backward compatibility
  public String write(QueryTemplateFactoryParameters params) {
    return write(params, null);
  }

  protected void reset() {
    this.whereClauseBranchPatterns = new ArrayDeque<>();
    this.anonymousVariableMappings = new HashMap<>();
    this.selectedBranchName = null;
  }

  /**
   * Initialise a delete template.
   * 
   * @param targetId The identifier of the instance to delete.
   */
  private ModifyQuery genDeleteTemplate(String targetId) {
    TriplePattern identifierTriple = SparqlBuilder.var("id0").has(QueryResource.DC_TERM_ID,
        Rdf.literalOf(targetId));
    return QueryResource.getDeleteQuery().delete(identifierTriple).where(identifierTriple);
  }

  /**
   * Retrieves the variable from the replacement object node as a query variable.
   * 
   * @param replacementNode Target for retrieval. Node must be an Object Node.
   */
  private Variable parseVariable(ObjectNode replacementNode) {
    // If it is an object, it is definitely a replacement object, and retrieving the
    // @replace key is sufficient;
    String replacementId = replacementNode.path(ShaclResource.REPLACE_KEY).asText();
    String replacementType = replacementNode.path(ShaclResource.TYPE_KEY).asText();
    // Replacement IRI fields with prefixes should be generated as query variables
    // Code will attempt to retrieve existing query variable for the same prefix,
    // but if it is new, the variable will be incremented according to the mapping
    // size
    if (replacementType.equals(QueryResource.IRI_KEY) && replacementNode.has("prefix")) {
      // Generates a mapping key based on the replacement name and its prefix
      String mappingKey = replacementId + replacementNode.path("prefix").asText();
      String idVar = this.anonymousVariableMappings.computeIfAbsent(mappingKey,
          k -> replacementId + this.anonymousVariableMappings.size());
      return QueryResource.genVariable(idVar);
    }
    return QueryResource.genVariable(replacementId);
  }

  /**
   * Update the where patterns to the delete template ONLY if this is not a
   * branch. Patterns will be appended into the branch queue if it is a branch.
   * 
   * @param wherePattern        The target pattern.
   * @param deleteTemplate      The query object holding all the delete template
   *                            values.
   * @param whereBranchPatterns An optional collection to store the graph patterns
   *                            for a WHERE clause if it belongs to a branch.
   */
  private void updateWherePatterns(GraphPattern wherePattern, ModifyQuery deleteTemplate,
      Queue<GraphPattern> whereBranchPatterns) {
    // Directly attach graph pattern to the where clause if it is not a branch
    if (whereBranchPatterns == null) {
      deleteTemplate.where(wherePattern);
    } else {
      whereBranchPatterns.offer(wherePattern);
    }
  }

  /**
   * Recursively parses the node to generate the DELETE query contents.
   * 
   * @param deleteTemplate      The query object holding the delete query.
   * @param whereBranchPatterns An optional collection to store the graph patterns
   *                            for a WHERE clause. If given, patterns will not be
   *                            appended to the delete template.
   * @param currentNode         Input contents to perform operation on.
   */
  private void recursiveParseNode(ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns,
      ObjectNode currentNode) {
    // First retrieve the ID value as a subject of the triple if required, else
    // default to target it
    JsonNode idNode = currentNode.path(ShaclResource.ID_KEY);
    if (idNode.isMissingNode()) {
      idNode = genBlankNode();
    }
    RdfSubject idTripleSubject = idNode.isObject() ? this.parseVariable((ObjectNode) idNode)
        : Rdf.iri(((TextNode) idNode).textValue());

    Iterator<Map.Entry<String, JsonNode>> iterator = currentNode.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> field = iterator.next();
      JsonNode objectNode = field.getValue();
      String predicate = field.getKey();
      switch (predicate) {
        case ShaclResource.TYPE_KEY:
          // Create the following query line for all @type fields
          RdfObject typeTripleObject = objectNode.isObject() ? this.parseVariable((ObjectNode) objectNode)
              : Rdf.iri(((TextNode) objectNode).textValue());
          TriplePattern triplePattern = idTripleSubject.isA(typeTripleObject);
          deleteTemplate.delete(triplePattern);

          // Add to branch patterns if we're in a branch, otherwise add to main WHERE
          if (whereBranchPatterns != null) {
            whereBranchPatterns.offer(triplePattern);
          } else {
            deleteTemplate.where(triplePattern);
          }
          break;
        case ShaclResource.BRANCH_KEY:
          LOGGER.debug("=== BRANCH CASE: selectedBranchName = {}", this.selectedBranchName);
          ArrayNode branches = this.jsonLdService.getArrayNode(objectNode);

          for (JsonNode branchNode : branches) {
            ObjectNode specificBranch = this.jsonLdService.getObjectNode(branchNode);
            String currentBranchName = specificBranch.path(ShaclResource.BRANCH_KEY).asText();

            LOGGER.debug("=== Checking branch: {}, matches selected? {}",
                currentBranchName,
                this.selectedBranchName != null && this.selectedBranchName.equals(currentBranchName));

            // FILTER: Skip branches that don't match selectedBranchName
            if (this.selectedBranchName != null && !this.selectedBranchName.equals(currentBranchName)) {
              LOGGER.debug("=== SKIPPING branch: {}", currentBranchName);
              continue;
            }

            // Process the matching branch
            LOGGER.debug("=== PROCESSING branch: {}", currentBranchName);
            Queue<GraphPattern> branchWherePatterns = new ArrayDeque<>();
            this.whereClauseBranchPatterns.offer(branchWherePatterns);

            // Create a copy without the @branch key to avoid reprocessing
            ObjectNode branchContentOnly = specificBranch.deepCopy();
            branchContentOnly.remove(ShaclResource.BRANCH_KEY);

            // Inherit the parent node's @id if the branch doesn't have one
            if (!branchContentOnly.has(ShaclResource.ID_KEY) && currentNode.has(ShaclResource.ID_KEY)) {
              branchContentOnly.set(ShaclResource.ID_KEY, currentNode.path(ShaclResource.ID_KEY).deepCopy());
              LOGGER.info("=== Branch inherited parent @id: {}", currentNode.path(ShaclResource.ID_KEY));
            }

            this.recursiveParseNode(deleteTemplate, branchWherePatterns, branchContentOnly);
          }
          break;
        case ShaclResource.REVERSE_KEY:
          if (objectNode.isArray()) {
            LOGGER.error(
                "Invalid reverse predicate JSON-LD schema for {}! Fields must be stored in an object!",
                idTripleSubject);
            throw new IllegalArgumentException(
                "Invalid reverse predicate JSON-LD schema! Fields must be stored in an object!");
          } else if (objectNode.isObject()) {
            // Reverse fields must be an object that may contain one or multiple fields
            Iterator<String> fieldIterator = objectNode.fieldNames();
            while (fieldIterator.hasNext()) {
              String reversePredicate = fieldIterator.next();
              this.parseNestedNode(currentNode.path(ShaclResource.ID_KEY), objectNode.path(reversePredicate),
                  Rdf.iri(reversePredicate), deleteTemplate, whereBranchPatterns, true);
            }
          }
          break;
        case ShaclResource.ID_KEY, ShaclResource.CONTEXT_KEY:
          // Ignore @id and @context fields
          break;
        default:
          this.parseFieldNode(currentNode.path(ShaclResource.ID_KEY), objectNode, idTripleSubject, Rdf.iri(predicate),
              deleteTemplate, whereBranchPatterns);
          break;
      }
    }
  }

  /**
   * Parses any field node.
   * 
   * @param idNode              The ID node of the current node.
   * @param objectNode          The node that is the target/ object of the triple
   *                            statement.
   * @param subject             The subject of the triple.
   * @param predicate           The predicate path of the triple.
   * @param deleteTemplate      The query object holding the delete query.
   * @param whereBranchPatterns An optional collection to store the graph patterns
   *                            for a WHERE clause. If given, patterns will not be
   *                            appended to the delete template.
   */
  private void parseFieldNode(JsonNode idNode, JsonNode objectNode, RdfSubject subject, Iri predicate,
      ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns) {
    // For object field node
    if (objectNode.isObject()) {
      JsonNode targetTripleObjectNode = objectNode.has(ShaclResource.REPLACE_KEY)
          ? objectNode
          : objectNode.path(ShaclResource.ID_KEY);

      // IF the object does not contain a @id or @replace key, it is a blank node
      if (targetTripleObjectNode.isMissingNode()) {
        targetTripleObjectNode = genBlankNode();
      }
      RdfObject sparqlObject = targetTripleObjectNode.isObject()
          ? this.parseVariable((ObjectNode) targetTripleObjectNode)
          : Rdf.iri(((TextNode) targetTripleObjectNode).textValue());

      // Add the triple statement directly to DELETE clause
      TriplePattern tripleStatement = subject.has(predicate, sparqlObject);
      deleteTemplate.delete(tripleStatement);

      GraphPattern wherePattern = tripleStatement;
      // But add optional clause when required for where clause
      if (objectNode.has(ShaclResource.REPLACE_KEY)
          && objectNode.path(ShaclResource.TYPE_KEY).asText().equals("literal")) {
        wherePattern = GraphPatterns.optional(tripleStatement);
      }
      this.updateWherePatterns(wherePattern, deleteTemplate, whereBranchPatterns);

      // Further processing for array type
      if (objectNode.has(ShaclResource.REPLACE_KEY)
          && objectNode.path(ShaclResource.TYPE_KEY).asText().equals(ShaclResource.ARRAY_KEY)
          && objectNode.has(ShaclResource.CONTENTS_KEY)) {
        // This should generate a DELETE query with a variable whenever IDs are detected
        ObjectNode arrayContents = this.getArrayReplacementContents(
            this.jsonLdService.getObjectNode(objectNode.path(ShaclResource.CONTENTS_KEY)),
            objectNode.path(ShaclResource.REPLACE_KEY).asText());
        this.recursiveParseNode(deleteTemplate, whereBranchPatterns, arrayContents);
      }
      // No further processing required for objects intended for replacement, @value,
      if (!objectNode.has(ShaclResource.REPLACE_KEY) && !objectNode.has(ShaclResource.VAL_KEY) &&
      // or a one line instance link to a TextNode eg: "@id" : "instanceIri"
          !(objectNode.has(ShaclResource.ID_KEY) && objectNode.size() == 1
              && objectNode.path(ShaclResource.ID_KEY).isTextual())) {
        this.recursiveParseNode(deleteTemplate, whereBranchPatterns, (ObjectNode) objectNode);
      }
      // For arrays,iterate through each object and parse the nested node
    } else if (objectNode.isArray()) {
      ArrayNode fieldArray = (ArrayNode) objectNode;
      for (JsonNode tripleObjNode : fieldArray) {
        this.parseNestedNode(idNode, tripleObjNode, predicate, deleteTemplate, whereBranchPatterns, false);
      }
    } else {
      TriplePattern triplePattern = subject.has(predicate, Rdf.literalOf(((TextNode) objectNode).textValue()));
      deleteTemplate.delete(triplePattern);
      this.updateWherePatterns(triplePattern, deleteTemplate, whereBranchPatterns);
    }
  }

  /**
   * Parses a nested node (two layers down) with the required parameters.
   * 
   * @param idNode              The ID node of the current top level node.
   * @param objectNode          The node acting as the object of the triple.
   * @param predicatePath       The predicate path of the triple.
   * @param deleteTemplate      The query object holding the delete query.
   * @param whereBranchPatterns An optional collection to store the graph patterns
   *                            for a WHERE clause. If given, patterns will not be
   *                            appended to the delete template.
   * @param isReverse           Indicates if the variable should be inverse or
   *                            not.
   */
  private void parseNestedNode(JsonNode idNode, JsonNode objectNode, Iri predicatePath,
      ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns, boolean isReverse) {
    if (isReverse) {
      if (objectNode.isObject()) {
        // A reverse node indicates that the replacement object should now be the
        // subject and the Id Node should become the object
        if (objectNode.has(ShaclResource.REPLACE_KEY)) {
          RdfSubject replacementVar = this.parseVariable((ObjectNode) objectNode);
          this.parseFieldNode(null, idNode, replacementVar, predicatePath,
              deleteTemplate, whereBranchPatterns);
        } else {
          // A reverse node indicates that the original object should now be the subject
          // And the Id Node should become the object
          ObjectNode nestedReverseNode = (ObjectNode) objectNode;
          // Ensure the predicate path excludes the enclosing <>
          String predicate = predicatePath.getQueryString();
          nestedReverseNode.set(predicate.substring(1, predicate.length() - 1), idNode);
          this.recursiveParseNode(deleteTemplate, whereBranchPatterns, nestedReverseNode);
        }
      } else if (objectNode.isArray()) {
        // For reverse arrays, iterate and recursively parse each object as a reverse
        // node
        ArrayNode objArray = (ArrayNode) objectNode;
        for (JsonNode nestedReverseObjNode : objArray) {
          this.parseNestedNode(idNode, nestedReverseObjNode, predicatePath, deleteTemplate, whereBranchPatterns, true);
        }
      }
    } else {
      // This aspect is used for parsing arrays without any reversion
      // Creating a new node where the ID node is the parent is sufficient
      ObjectNode nestedNode = this.jsonLdService.genObjectNode();
      nestedNode.set(ShaclResource.ID_KEY, idNode);
      // Ensure the predicate path excludes the enclosing <>
      String predicate = predicatePath.getQueryString();
      nestedNode.set(predicate.substring(1, predicate.length() - 1), objectNode);
      this.recursiveParseNode(deleteTemplate, whereBranchPatterns, nestedNode);
    }
  }

  /**
   * Get and update the array replacement contents so that the array field is
   * properly targeted in the query.
   */
  private ObjectNode getArrayReplacementContents(ObjectNode contents, String arrayField) {
    ObjectNode newIdNode = this.jsonLdService.genObjectNode();
    newIdNode.put(ShaclResource.REPLACE_KEY, arrayField);
    newIdNode.put(ShaclResource.TYPE_KEY, "literal");
    // Replace or add @id with a new ID node based on the array field
    contents.set(ShaclResource.ID_KEY, newIdNode);
    return contents;
  }

  /**
   * Generates a blank node based on the mapping size.
   */
  private ObjectNode genBlankNode() {
    ObjectNode blankNode = this.jsonLdService.genObjectNode();
    blankNode.put(ShaclResource.REPLACE_KEY, String.valueOf(this.anonymousVariableMappings.size()));
    blankNode.put(ShaclResource.TYPE_KEY, QueryResource.IRI_KEY);
    return blankNode;
  }

  /**
   * Update the delete template to append the branch patterns as group.
   * 
   * @param deleteTemplate The query object holding the delete query.
   */
  private void addBranches(ModifyQuery deleteTemplate) {
    if (this.whereClauseBranchPatterns.isEmpty()) {
      return;
    }

    // If specific branch selected, add directly
    if (this.selectedBranchName != null && this.whereClauseBranchPatterns.size() == 1) {
      Queue<GraphPattern> branchPattern = this.whereClauseBranchPatterns.poll();
      LOGGER.info("Adding branch patterns directly to WHERE clause for branch: {}", this.selectedBranchName);
      // Add patterns directly to WHERE clause without OPTIONAL
      for (GraphPattern pattern : branchPattern) {
        deleteTemplate.where(pattern);
      }
      return;
    }

    // Fallback: Use original logic (wraps each branch in OPTIONAL)
    while (!this.whereClauseBranchPatterns.isEmpty()) {
      Queue<GraphPattern> branchPattern = this.whereClauseBranchPatterns.poll();
      GraphPatternNotTriples branchPatterns = GraphPatterns.optional(
          // Convert queue to an array so that it is accepted by GraphPatterns.optional
          branchPattern.toArray(new GraphPattern[0]));
      deleteTemplate.where(branchPatterns);
    }
  }
}
