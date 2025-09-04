package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.TriplesTemplate;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfSubject;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class DeleteQueryTemplateFactory extends AbstractQueryTemplateFactory {
  private Map<String, String> anonymousVariableMappings;
  private Queue<GraphPattern> whereClausePatterns;
  private Queue<Queue<GraphPattern>> whereClauseBranchPatterns;
  private final JsonLdService jsonLdService;
  private static final Logger LOGGER = LogManager.getLogger(DeleteQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   * 
   */
  public DeleteQueryTemplateFactory(JsonLdService jsonLdService) {
    this.jsonLdService = jsonLdService;
    this.whereClausePatterns = new ArrayDeque<>();
    this.whereClauseBranchPatterns = new ArrayDeque<>();
  }

  /**
   * Generate a SPARQL query template to delete the target instance.
   * 
   * @param params An object containing two parameters to write, namely:
   *               rootNode - The root node of contents to parse into a template
   *               targetId - The target instance IRI.
   */
  public Queue<String> write(QueryTemplateFactoryParameters params) {
    this.reset();
    TriplePattern identifierTriple = SparqlBuilder.var("id0").has(QueryResource.DC_TERM_ID,
        Rdf.literalOf(params.targetId()));
    TriplesTemplate deleteClause = SparqlBuilder.triplesTemplate(identifierTriple);
    this.whereClausePatterns.offer(identifierTriple);
    this.recursiveParseNode(deleteClause, this.whereClausePatterns, params.rootNode());

    Queue<String> result = new ArrayDeque<>();
    result.offer(this.genDeleteTemplate(deleteClause));
    return result;
  }

  protected void reset() {
    this.whereClauseBranchPatterns = new ArrayDeque<>();
    this.anonymousVariableMappings = new HashMap<>();
  }

  /**
   * Recursively parses the node to generate the DELETE query contents.
   * 
   * @param deleteTriplesTemplate A collection of the triples required for the
   *                              DELETE clause.
   * @param wherePatterns         A collection containing graph patterns for the
   *                              WHERE clause.
   * @param currentNode           Input contents to perform operation on.
   */
  private void recursiveParseNode(TriplesTemplate deleteTriplesTemplate, Queue<GraphPattern> wherePatterns,
      ObjectNode currentNode) {
    // First retrieve the ID value as a subject of the triple if required, else
    // default to target it
    JsonNode idNode = currentNode.path(ShaclResource.ID_KEY);
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
          this.appendTriples(idTripleSubject.isA(typeTripleObject), deleteTriplesTemplate, wherePatterns);
          break;
        case ShaclResource.BRANCH_KEY:
          // Iterate over all possible branches
          ArrayNode branches = this.jsonLdService.getArrayNode(objectNode);
          for (JsonNode branch : branches) {
            // Generate the required delete template and store the template
            // Retain the current ID value
            ObjectNode branchNode = this.jsonLdService.getObjectNode(branch);
            branchNode.set(ShaclResource.ID_KEY, currentNode.path(ShaclResource.ID_KEY));
            Queue<GraphPattern> branchPatterns = new ArrayDeque<>();
            this.recursiveParseNode(deleteTriplesTemplate, branchPatterns, branchNode);
            this.whereClauseBranchPatterns.offer(branchPatterns);
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
                  Rdf.iri(reversePredicate), deleteTriplesTemplate, wherePatterns, true);
            }
          }
          break;
        case ShaclResource.ID_KEY, ShaclResource.CONTEXT_KEY:
          // Ignore @id and @context fields
          break;
        default:
          this.parseFieldNode(currentNode.path(ShaclResource.ID_KEY), objectNode, idTripleSubject, Rdf.iri(predicate),
              deleteTriplesTemplate, wherePatterns);
          break;
      }
    }
  }

  /**
   * Appends the same triple to both the delete and where clause templates.
   * 
   * @param triple                The triple statement to attach.
   * @param deleteTriplesTemplate A collection of the triples required for the
   *                              DELETE clause.
   * @param wherePatterns         A collection containing graph patterns for the
   *                              WHERE clause.
   */
  private void appendTriples(TriplePattern triple, TriplesTemplate deleteTriplesTemplate,
      Queue<GraphPattern> wherePatterns) {
    deleteTriplesTemplate.and(triple);
    wherePatterns.offer(triple);
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
    // Id replacement fields with prefixes should be generated as query variables
    // Code will attempt to retrieve existing query variable for the same prefix,
    // but if it is new, the id will be incremented according to the mapping size
    if (replacementType.equals(LifecycleResource.IRI_KEY) && replacementId.equals(StringResource.ID_KEY)) {
      String idPrefix = replacementNode.path("prefix").asText();
      String idVar = this.anonymousVariableMappings.computeIfAbsent(idPrefix,
          k -> StringResource.ID_KEY + this.anonymousVariableMappings.size());
      return SparqlBuilder.var(idVar);
    }
    return SparqlBuilder.var(StringResource.parseQueryVariable(replacementId));
  }

  /**
   * Parses any field node.
   * 
   * @param idNode                The ID node of the current node.
   * @param objectNode            The node that is the target/ object of the
   *                              triple statement.
   * @param subject               The subject of the triple.
   * @param predicate             The predicate path of the triple.
   * @param deleteTriplesTemplate A collection of the triples required for the
   *                              DELETE clause.
   * @param wherePatterns         A collection containing graph patterns for the
   *                              WHERE clause.
   */
  private void parseFieldNode(JsonNode idNode, JsonNode objectNode, RdfSubject subject, Iri predicate,
      TriplesTemplate deleteTriplesTemplate, Queue<GraphPattern> wherePatterns) {
    // For object field node
    if (objectNode.isObject()) {
      JsonNode targetTripleObjectNode = objectNode.has(ShaclResource.REPLACE_KEY)
          ? objectNode
          : objectNode.path(ShaclResource.ID_KEY);

      RdfObject sparqlObject = targetTripleObjectNode.isObject()
          ? this.parseVariable((ObjectNode) targetTripleObjectNode)
          : Rdf.iri(((TextNode) targetTripleObjectNode).textValue());

      // Add the triple statement directly to DELETE clause
      TriplePattern tripleStatement = subject.has(predicate, sparqlObject);
      deleteTriplesTemplate.and(tripleStatement);

      // But add optional clause when required
      if (objectNode.has(ShaclResource.REPLACE_KEY)
          && objectNode.path(ShaclResource.TYPE_KEY).asText().equals("literal")) {
        wherePatterns.offer(GraphPatterns.optional(tripleStatement));
      } else {
        wherePatterns.offer(tripleStatement);
      }
      // Further processing for array type
      if (objectNode.has(ShaclResource.REPLACE_KEY)
          && objectNode.path(ShaclResource.TYPE_KEY).asText().equals(ShaclResource.ARRAY_KEY)
          && objectNode.has(ShaclResource.CONTENTS_KEY)) {
        // This should generate a DELETE query with a variable whenever IDs are detected
        this.recursiveParseNode(deleteTriplesTemplate, wherePatterns,
            this.jsonLdService.getObjectNode(objectNode.path(ShaclResource.CONTENTS_KEY)));
      }
      // No further processing required for objects intended for replacement, @value,
      if (!objectNode.has(ShaclResource.REPLACE_KEY) && !objectNode.has(ShaclResource.VAL_KEY) &&
      // or a one line instance link to a TextNode eg: "@id" : "instanceIri"
          !(objectNode.has(ShaclResource.ID_KEY) && objectNode.size() == 1
              && objectNode.path(ShaclResource.ID_KEY).isTextual())) {
        this.recursiveParseNode(deleteTriplesTemplate, wherePatterns, (ObjectNode) objectNode);
      }
      // For arrays,iterate through each object and parse the nested node
    } else if (objectNode.isArray()) {
      ArrayNode fieldArray = (ArrayNode) objectNode;
      for (JsonNode tripleObjNode : fieldArray) {
        this.parseNestedNode(idNode, tripleObjNode, predicate, deleteTriplesTemplate, wherePatterns, false);
      }
    } else {
      // Text nodes are string literals
      this.appendTriples(subject.has(predicate, Rdf.literalOf(((TextNode) objectNode).textValue())),
          deleteTriplesTemplate, wherePatterns);
    }
  }

  /**
   * Parses a nested node (two layers down) with the required parameters.
   * 
   * @param idNode                The ID node of the current top level node.
   * @param objectNode            The node acting as the object of the triple.
   * @param predicatePath         The predicate path of the triple.
   * @param deleteTriplesTemplate A collection of the triples required for the
   *                              DELETE clause.
   * @param wherePatterns         A collection containing graph patterns for the
   *                              WHERE clause.
   * @param isReverse             Indicates if the variable should be inverse or
   *                              not.
   */
  private void parseNestedNode(JsonNode idNode, JsonNode objectNode, Iri predicatePath,
      TriplesTemplate deleteTriplesTemplate, Queue<GraphPattern> wherePatterns, boolean isReverse) {
    if (isReverse) {
      if (objectNode.isObject()) {
        // A reverse node indicates that the replacement object should now be the
        // subject and the Id Node should become the object
        if (objectNode.has(ShaclResource.REPLACE_KEY)) {
          RdfSubject replacementVar = this.parseVariable((ObjectNode) objectNode);
          this.parseFieldNode(null, idNode, replacementVar, predicatePath,
              deleteTriplesTemplate, wherePatterns);
        } else {
          // A reverse node indicates that the original object should now be the subject
          // And the Id Node should become the object
          ObjectNode nestedReverseNode = (ObjectNode) objectNode;
          // Ensure the predicate path excludes the enclosing <>
          String predicate = predicatePath.getQueryString();
          nestedReverseNode.set(predicate.substring(1, predicate.length() - 1), idNode);
          this.recursiveParseNode(deleteTriplesTemplate, wherePatterns, nestedReverseNode);
        }
      } else if (objectNode.isArray()) {
        // For reverse arrays, iterate and recursively parse each object as a reverse
        // node
        ArrayNode objArray = (ArrayNode) objectNode;
        for (JsonNode nestedReverseObjNode : objArray) {
          this.parseNestedNode(idNode, nestedReverseObjNode, predicatePath, deleteTriplesTemplate, wherePatterns, true);
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
      this.recursiveParseNode(deleteTriplesTemplate, wherePatterns, nestedNode);
    }
  }

  /**
   * Generates a delete template from the delete builder contents.
   * 
   * @param deleteBuilder  A query builder for the DELETE clause.
   * @param whereBuilder   A query builder for the WHERE clause.
   * @param branchBuilders A query builders for any form branches.
   */
  private String genDeleteTemplate(TriplesTemplate deleteClause) {
    StringBuilder whereClause = new StringBuilder();
    while (!this.whereClausePatterns.isEmpty()) {
      whereClause.append(this.whereClausePatterns.poll().getQueryString())
          .append("\n");
    }
    while (!this.whereClauseBranchPatterns.isEmpty()) {
      Queue<GraphPattern> branchPattern = this.whereClauseBranchPatterns.poll();
      whereClause.append(
          // Convert queue to an array so that it is accepted by GraphPatterns.optional
          GraphPatterns.optional(
                  branchPattern.toArray(new GraphPattern[0]))
              .getQueryString())
          .append("\n");
    }
    return QueryResource.DC_TERM.getQueryString() +
        "DELETE " + deleteClause.getQueryString().toString() +
        " WHERE {" + whereClause.toString() + "}";
  }
}
