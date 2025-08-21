package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.Iterator;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;

public class DeleteQueryTemplateFactory extends AbstractQueryTemplateFactory {
  private Queue<StringBuilder> deleteBranchBuilders;
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
   *               rootNode - The root node of contents to parse into a template
   *               targetId - The target instance IRI.
   */
  public Queue<String> write(QueryTemplateFactoryParameters params) {
    this.reset();
    StringBuilder deleteBuilder = new StringBuilder();
    StringBuilder whereBuilder = new StringBuilder();
    this.recursiveParseNode(deleteBuilder, whereBuilder, params.rootNode(), params.targetId(), true);
    StringResource.appendTriple(deleteBuilder, ShaclResource.VARIABLE_MARK + LifecycleResource.IRI_KEY,
        StringResource.parseIriForQuery(ShaclResource.DC_TERMS_ID), StringResource.parseLiteral(params.targetId()));
    Queue<String> result = new ArrayDeque<>();
    result.offer(this.genDeleteTemplate(deleteBuilder, whereBuilder, this.deleteBranchBuilders));
    return result;
  }

  protected void reset() {
    this.deleteBranchBuilders = new ArrayDeque<>();
  }

  /**
   * Recursively parses the node to generate the DELETE query contents.
   * 
   * @param deleteBuilder A query builder for the DELETE clause.
   * @param whereBuilder  A query builder for the WHERE clause.
   * @param currentNode   Input contents to perform operation on.
   * @param targetId      The target instance IRI.
   * @param isIdRequired  Indicator to generate an instance ID or an ID variable
   *                      following targetId.
   */
  private void recursiveParseNode(StringBuilder deleteBuilder, StringBuilder whereBuilder, ObjectNode currentNode,
      String targetId, boolean isIdRequired) {
    // First retrieve the ID value as a subject of the triple if required, else
    // default to target it
    String idTripleSubject = isIdRequired ? this.getFormattedQueryVariable(currentNode.path(ShaclResource.ID_KEY),
        targetId) : targetId;
    Iterator<Map.Entry<String, JsonNode>> iterator = currentNode.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> field = iterator.next();
      JsonNode fieldNode = field.getValue();
      String fieldKey = field.getKey();
      switch (fieldKey) {
        case ShaclResource.TYPE_KEY:
          // Create the following query line for all @type fields
          String typeTripleObject = this.getFormattedQueryVariable(fieldNode, targetId);
          StringResource.appendTriple(deleteBuilder, idTripleSubject, StringResource.RDF_TYPE, typeTripleObject);
          StringResource.appendTriple(whereBuilder, idTripleSubject, StringResource.RDF_TYPE, typeTripleObject);
          break;
        case ShaclResource.BRANCH_KEY:
          // Iterate over all possible branches
          ArrayNode branches = this.jsonLdService.getArrayNode(fieldNode);
          for (JsonNode branch : branches) {
            // Generate the required delete template and store the template
            StringBuilder deleteBranchBuilder = new StringBuilder();
            StringBuilder deleteBranchWhereBuilder = new StringBuilder();
            ObjectNode branchNode = this.jsonLdService.getObjectNode(branch);
            // Retain the current ID value
            branchNode.set(ShaclResource.ID_KEY, currentNode.path(ShaclResource.ID_KEY));
            this.recursiveParseNode(deleteBranchBuilder, deleteBranchWhereBuilder, branchNode, targetId, isIdRequired);
            this.deleteBranchBuilders.offer(deleteBranchBuilder);
            this.deleteBranchBuilders.offer(deleteBranchWhereBuilder);
          }
          break;
        case ShaclResource.REVERSE_KEY:
          if (fieldNode.isArray()) {
            LOGGER.error(
                "Invalid reverse predicate JSON-LD schema for {}! Fields must be stored in an object!",
                idTripleSubject);
            throw new IllegalArgumentException(
                "Invalid reverse predicate JSON-LD schema! Fields must be stored in an object!");
          } else if (fieldNode.isObject()) {
            // Reverse fields must be an object that may contain one or multiple fields
            Iterator<String> fieldIterator = fieldNode.fieldNames();
            while (fieldIterator.hasNext()) {
              String reversePredicate = fieldIterator.next();
              this.parseNestedNode(currentNode.path(ShaclResource.ID_KEY), fieldNode.path(reversePredicate),
                  reversePredicate, deleteBuilder, whereBuilder, targetId, true, isIdRequired);
            }
          }
          break;
        case ShaclResource.ID_KEY, ShaclResource.CONTEXT_KEY:
          // Ignore @id and @context fields
          break;
        default:
          this.parseFieldNode(currentNode.path(ShaclResource.ID_KEY), fieldNode, idTripleSubject, field.getKey(),
              deleteBuilder, whereBuilder, targetId, isIdRequired);
          break;
      }
    }
  }

  /**
   * Retrieves the query variable from the replacement node as either an IRI or
   * query variable.
   * 
   * @param replacementNode Target for retrieval. Node must be an Object or Text
   *                        Node.
   * @param targetId        The target instance IRI.
   */
  private String getFormattedQueryVariable(JsonNode replacementNode, String targetId) {
    // If it is an object, it is definitely a replacement object, and retriving the
    // @replace key is sufficient;
    if (replacementNode.isObject()) {
      String replacementId = replacementNode.path(ShaclResource.REPLACE_KEY).asText();
      String replacementType = replacementNode.path(ShaclResource.TYPE_KEY).asText();
      // Only the id replacement field with prefixes will be returned as an IRI
      if (replacementType.equals(LifecycleResource.IRI_KEY) && replacementId.equals(StringResource.ID_KEY)) {
        return StringResource.parseIriForQuery(replacementNode.path("prefix").asText() + targetId);
      }
      return ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(replacementId);
    } else {
      // Otherwise, default to text
      return StringResource.parseIriForQuery(((TextNode) replacementNode).textValue());
    }
  }

  /**
   * Parses any field node.
   * 
   * @param idNode        The ID node of the current node.
   * @param fieldNode     The field node of the current node.
   * @param subject       The node acting as the subject of the triple.
   * @param predicate     The predicate path of the triple.
   * @param deleteBuilder A query builder for the DELETE clause.
   * @param whereBuilder  A query builder for the WHERE clause.
   * @param targetId      The target instance IRI.
   * @param isIdRequired  Indicator to generate an instance ID or an ID variable
   *                      following targetId.
   */
  private void parseFieldNode(JsonNode idNode, JsonNode fieldNode, String subject, String predicate,
      StringBuilder deleteBuilder, StringBuilder whereBuilder, String targetId, boolean isIdRequired) {
    // For object field node
    if (fieldNode.isObject()) {
      JsonNode targetTripleObjectNode = fieldNode.has(ShaclResource.REPLACE_KEY)
          ? fieldNode
          : fieldNode.path(ShaclResource.ID_KEY);
      String formattedPredicate = StringResource.parseIriForQuery(predicate);
      String formattedObjVar = this.getFormattedQueryVariable(targetTripleObjectNode, targetId);
      String parsedId = targetId;
      // If this is a nested array element, where no id is required but this is an id
      // field, not literal, extend the target id with the predicate
      if (!isIdRequired && formattedObjVar.startsWith("<") && formattedObjVar.endsWith(">")
          && targetId.contains(ShaclResource.VARIABLE_MARK)) {
        parsedId = targetId + StringResource.getLocalName(predicate);
        formattedObjVar = parsedId;
      }
      StringResource.appendTriple(deleteBuilder, subject, formattedPredicate, formattedObjVar);
      if (fieldNode.has(ShaclResource.REPLACE_KEY)
          && fieldNode.path(ShaclResource.TYPE_KEY).asText().equals("literal")) {
        StringBuilder optionalBuilder = new StringBuilder();
        StringResource.appendTriple(optionalBuilder, subject, formattedPredicate, formattedObjVar);
        whereBuilder.append(StringResource.genOptionalClause(optionalBuilder.toString()));
      } else {
        StringResource.appendTriple(whereBuilder, subject, formattedPredicate, formattedObjVar);
      }
      // Further processing for array type
      if (fieldNode.has(ShaclResource.REPLACE_KEY)
          && fieldNode.path(ShaclResource.TYPE_KEY).asText().equals(ShaclResource.ARRAY_KEY)
          && fieldNode.has(ShaclResource.CONTENTS_KEY)) {
        // This should generate a DELETE query with a variable whenever IDs are detected
        this.recursiveParseNode(deleteBuilder, whereBuilder,
            this.jsonLdService.getObjectNode(fieldNode.path(ShaclResource.CONTENTS_KEY)),
            formattedObjVar, false);
      }
      // No further processing required for objects intended for replacement, @value,
      if (!fieldNode.has(ShaclResource.REPLACE_KEY) && !fieldNode.has(ShaclResource.VAL_KEY) &&
      // or a one line instance link to a TextNode eg: "@id" : "instanceIri"
          !(fieldNode.has(ShaclResource.ID_KEY) && fieldNode.size() == 1
              && fieldNode.path(ShaclResource.ID_KEY).isTextual())) {
        this.recursiveParseNode(deleteBuilder, whereBuilder, (ObjectNode) fieldNode, parsedId, isIdRequired);
      }
      // For arrays,iterate through each object and parse the nested node
    } else if (fieldNode.isArray()) {
      ArrayNode fieldArray = (ArrayNode) fieldNode;
      for (JsonNode tripleObjNode : fieldArray) {
        this.parseNestedNode(idNode, tripleObjNode, predicate, deleteBuilder, whereBuilder, targetId, false,
            isIdRequired);
      }
    }
  }

  /**
   * Parses a nested node (two layers down) with the required parameters.
   * 
   * @param idNode        The ID node of the current top level node.
   * @param objectNode    The node acting as the object of the triple.
   * @param predicatePath The predicate path of the triple.
   * @param deleteBuilder A query builder for the DELETE clause.
   * @param whereBuilder  A query builder for the WHERE clause.
   * @param targetId      The target instance IRI.
   * @param isReverse     Indicates if the variable should be inverse or not.
   * @param isIdRequired  Indicator to generate an instance ID or an ID variable
   *                      following targetId.
   */
  private void parseNestedNode(JsonNode idNode, JsonNode objectNode, String predicatePath, StringBuilder deleteBuilder,
      StringBuilder whereBuilder,
      String targetId, boolean isReverse, boolean isIdRequired) {
    if (isReverse) {
      if (objectNode.isObject()) {
        // A reverse node indicates that the replacement object should now be the
        // subject and the Id Node should become the object
        if (objectNode.has(ShaclResource.REPLACE_KEY)) {
          String replacementVar = this.getFormattedQueryVariable(objectNode, targetId);
          this.parseFieldNode(null, idNode, replacementVar, predicatePath,
              deleteBuilder, whereBuilder, targetId, isIdRequired);
        } else {
          // A reverse node indicates that the original object should now be the subject
          // And the Id Node should become the object
          ObjectNode nestedReverseNode = (ObjectNode) objectNode;
          nestedReverseNode.set(predicatePath, idNode);
          this.recursiveParseNode(deleteBuilder, whereBuilder, nestedReverseNode, targetId, isIdRequired);
        }
      } else if (objectNode.isArray()) {
        // For reverse arrays, iterate and recursively parse each object as a reverse
        // node
        ArrayNode objArray = (ArrayNode) objectNode;
        for (JsonNode nestedReverseObjNode : objArray) {
          this.parseNestedNode(idNode, nestedReverseObjNode, predicatePath, deleteBuilder, whereBuilder, targetId, true,
              isIdRequired);
        }
      }
    } else {
      // This aspect is used for parsing arrays without any reversion
      // Creating a new node where the ID node is the parent is sufficient
      ObjectNode nestedNode = this.jsonLdService.genObjectNode();
      nestedNode.set(ShaclResource.ID_KEY, idNode);
      nestedNode.set(predicatePath, objectNode);
      this.recursiveParseNode(deleteBuilder, whereBuilder, nestedNode, targetId, isIdRequired);
    }
  }

  /**
   * Generates a delete template from the delete builder contents.
   * 
   * @param deleteBuilder  A query builder for the DELETE clause.
   * @param whereBuilder   A query builder for the WHERE clause.
   * @param branchBuilders A query builders for any form branches.
   */
  private String genDeleteTemplate(StringBuilder deleteBuilder, StringBuilder whereBuilder,
      Queue<StringBuilder> branchBuilders) {
    while (!branchBuilders.isEmpty()) {
      // Branch builders will have two builders per branch
      // First builder is for the DELETE clause
      deleteBuilder.append(branchBuilders.poll().toString());
      // Second builder is for the WHERE clause, which should also be optional
      whereBuilder.append(StringResource.genOptionalClause(branchBuilders.poll().toString()));
    }
    return "DELETE {" + deleteBuilder.toString() + "} WHERE {" + whereBuilder.toString() + "}";
  }
}
