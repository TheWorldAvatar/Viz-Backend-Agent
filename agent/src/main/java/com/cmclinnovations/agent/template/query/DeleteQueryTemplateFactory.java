package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.ModifyQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
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
  private final JsonLdService jsonLdService;
  private static final Logger LOGGER = LogManager.getLogger(DeleteQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   * 
   */
  public DeleteQueryTemplateFactory(JsonLdService jsonLdService) {
    this.jsonLdService = jsonLdService;
  }

  /**
   * Generate a SPARQL query template to delete the target instance.
   * 
   * @param params An object containing two parameters to write, namely:
   *               rootNode - The root node of contents to parse into a
   *               template
   *               targetId - The target instance IRI.
   */
  public String write(QueryTemplateFactoryParameters params) {
    this.reset();

    ModifyQuery deleteTemplate = this.genDeleteTemplate(params.targetIds().poll().get(0),
        this.parseVariable((ObjectNode) params.rootNode().path(ShaclResource.ID_KEY)));
    this.recursiveParseNode(deleteTemplate, null, params.rootNode(), params.branchName(), params.optVarNames());
    return deleteTemplate.getQueryString();
  }

  protected void reset() {
    this.anonymousVariableMappings = new HashMap<>();
  }

  /**
   * Initialise a delete template.
   * 
   * @param targetId The identifier of the instance to delete.
   * @param idVar    The instance id as a variable.
   */
  private ModifyQuery genDeleteTemplate(String targetId, Variable idVar) {
    TriplePattern identifierTriple = idVar.has(QueryResource.DC_TERM_ID,
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
   *                            for a WHERE clause if it belongs to a branch. If
   *                            given, patterns will not be appended to the delete
   *                            template.
   * @param currentNode         Input contents to perform operation on.
   * @param branch              Name of branch for deletion.
   * @param optVarNames         Set of names of optional variables.
   */
  private void recursiveParseNode(ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns,
      ObjectNode currentNode, String branch, Set<String> optVarNames) {
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
          if (branch == null || branch.isEmpty()) {
            throw new IllegalArgumentException("No branch specified for branch deletion!");
          }
          // Find the matching branch
          ArrayNode branches = this.jsonLdService.getArrayNode(objectNode);
          ObjectNode matchingBranch = this.jsonLdService.genObjectNode();
          for (JsonNode branchNode : branches) {
            if (branchNode.get(ShaclResource.BRANCH_KEY).asText().equals(branch)) {
              ObjectNode branchObj = (ObjectNode) branchNode;
              // Remove branch key as it should not be reused
              branchObj.remove(ShaclResource.BRANCH_KEY);
              matchingBranch = branchObj;
            }
          }
          Queue<GraphPattern> branchPatterns = new ArrayDeque<>();
          // Parse branch contents directly into delete template
          matchingBranch.set(ShaclResource.ID_KEY, currentNode.path(ShaclResource.ID_KEY));
          this.recursiveParseNode(deleteTemplate, branchPatterns, matchingBranch, branch, optVarNames);
          deleteTemplate.where(branchPatterns.toArray(new GraphPattern[0]));
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
                  Rdf.iri(reversePredicate), deleteTemplate, whereBranchPatterns, branch, true, optVarNames);
            }
          }
          break;
        case ShaclResource.ID_KEY, ShaclResource.CONTEXT_KEY:
          // Ignore @id and @context fields
          break;
        default:
          this.parseFieldNode(currentNode.path(ShaclResource.ID_KEY), objectNode, idTripleSubject, Rdf.iri(predicate),
              deleteTemplate, whereBranchPatterns, branch, optVarNames);
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
   *                            for a WHERE clause if it belongs to a branch.If
   *                            given, patterns will not be appended to the delete
   *                            template.
   * @param branch              Name of branch for deletion.
   * @param optVarNames         Set of names of optional variables.
   */
  private void parseFieldNode(JsonNode idNode, JsonNode objectNode, RdfSubject subject, Iri predicate,
      ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns, String branch, Set<String> optVarNames) {
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

      if ((objectNode.has(ShaclResource.REPLACE_KEY)
          && objectNode.path(ShaclResource.TYPE_KEY).asText().equals("literal"))
          || optVarNames.contains(objectNode.path(ShaclResource.ID_KEY).path(ShaclResource.REPLACE_KEY).asText())) {
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
        this.recursiveParseNode(deleteTemplate, whereBranchPatterns, arrayContents, branch, optVarNames);
      }
      // No further processing required for objects intended for replacement, @value,
      if (!objectNode.has(ShaclResource.REPLACE_KEY) && !objectNode.has(ShaclResource.VAL_KEY) &&
      // or a one line instance link to a TextNode eg: "@id" : "instanceIri"
          !(objectNode.has(ShaclResource.ID_KEY) && objectNode.size() == 1
              && objectNode.path(ShaclResource.ID_KEY).isTextual())) {
        this.recursiveParseNode(deleteTemplate, whereBranchPatterns, (ObjectNode) objectNode, branch, optVarNames);
      }
      // For arrays,iterate through each object and parse the nested node
    } else if (objectNode.isArray()) {
      ArrayNode fieldArray = (ArrayNode) objectNode;
      for (JsonNode tripleObjNode : fieldArray) {
        this.parseNestedNode(idNode, tripleObjNode, predicate, deleteTemplate, whereBranchPatterns, branch, false, optVarNames);
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
   *                            for a WHERE clause if it belongs to a branch. If
   *                            given, patterns will not be appended to the delete
   *                            template.
   * @param branch              Name of branch for deletion.
   * @param isReverse           Indicates if the variable should be inverse or
   *                            not.
   * @param optVarNames         Set of names of optional variables.
   */
  private void parseNestedNode(JsonNode idNode, JsonNode objectNode, Iri predicatePath,
      ModifyQuery deleteTemplate, Queue<GraphPattern> whereBranchPatterns, String branch, boolean isReverse, Set<String> optVarNames) {
    if (isReverse) {
      if (objectNode.isObject()) {
        // A reverse node indicates that the replacement object should now be the
        // subject and the Id Node should become the object
        if (objectNode.has(ShaclResource.REPLACE_KEY)) {
          RdfSubject replacementVar = this.parseVariable((ObjectNode) objectNode);
          this.parseFieldNode(null, idNode, replacementVar, predicatePath,
              deleteTemplate, whereBranchPatterns, branch, optVarNames);
        } else {
          // A reverse node indicates that the original object should now be the subject
          // And the Id Node should become the object
          ObjectNode nestedReverseNode = (ObjectNode) objectNode;
          // Ensure the predicate path excludes the enclosing <>
          String predicate = predicatePath.getQueryString();
          nestedReverseNode.set(predicate.substring(1, predicate.length() - 1), idNode);
          this.recursiveParseNode(deleteTemplate, whereBranchPatterns, nestedReverseNode, branch, optVarNames);
        }
      } else if (objectNode.isArray()) {
        // For reverse arrays, iterate and recursively parse each object as a reverse
        // node
        ArrayNode objArray = (ArrayNode) objectNode;
        for (JsonNode nestedReverseObjNode : objArray) {
          this.parseNestedNode(idNode, nestedReverseObjNode, predicatePath, deleteTemplate, whereBranchPatterns, branch,
              true, optVarNames);
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
      this.recursiveParseNode(deleteTemplate, whereBranchPatterns, nestedNode, branch, optVarNames);
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
}
