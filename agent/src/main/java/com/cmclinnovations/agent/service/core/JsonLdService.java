package com.cmclinnovations.agent.service.core;

import java.text.MessageFormat;
import java.util.Map;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class JsonLdService {
  private final ObjectMapper objectMapper;

  private static final String RDFS_LABEL = "http://www.w3.org/2000/01/rdf-schema#label";
  private static final Logger LOGGER = LogManager.getLogger(JsonLdService.class);

  /**
   * Constructs a new service to interact with JSON-LD objects with the following
   * dependencies.
   * 
   * @param objectMapper The JSON object mapper.
   */
  public JsonLdService(ObjectMapper objectMapper) {
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieve the required replacement value.
   * 
   * @param replacementNode Node containing metadata for replacement.
   * @param replacements    Mappings of the replacement value with their
   *                        corresponding node.
   */
  public String getReplacementValue(ObjectNode replacementNode, Map<String, Object> replacements) {
    String replacementType = replacementNode.path(ShaclResource.TYPE_KEY).asText();
    // Iterate through the replacements and find the relevant key for replacement
    String replacementId = replacementNode.path(ShaclResource.REPLACE_KEY).asText();
    String targetKey = "";
    for (String key : replacements.keySet()) {
      // The loop should only be broken when an exact match is found
      // This is important for shorter names that may be a subset for larger mappings
      // eg id and friday
      if (key.equals(replacementId)) {
        targetKey = key;
        break;
      }
      if (key.contains(replacementId)) {
        targetKey = key;
      }
    }
    // Return the replacement value with the target key for literal
    if (replacementType.equals("literal")) {
      return replacements.get(targetKey).toString();
    } else if (replacementType.equals(LifecycleResource.IRI_KEY)) {
      JsonNode prefixNode = replacementNode.path("prefix");
      // Return the replacement value with the target key for any iris without a
      // prefix
      if (prefixNode.isMissingNode()) {
        return replacements.get(targetKey).toString();
      } else {
        // If a prefix is present, extract the identifer and append the prefix
        return prefixNode.asText() + StringResource.getLocalName(replacements.get(targetKey).toString());
      }
    } else {
      LOGGER.error("Invalid replacement type {} for {}!", replacementType, replacementId);
      throw new IllegalArgumentException(
          MessageFormat.format("Invalid replacement type {0} for {1}!", replacementType, replacementId));
    }
  }

  /**
   * Generates a instance object node with a readable label.
   * 
   * @param prefix  IRI prefix.
   * @param concept The ontology concept class/type for the instance.
   * @param label   The label for the instance.
   */
  public ObjectNode genInstance(String prefix, String concept, String label) {
    return this.genInstance(prefix, concept)
        .put(RDFS_LABEL, label);
  }

  /**
   * Generates a instance object node.
   * 
   * @param prefix  IRI prefix.
   * @param concept The ontology concept class/type for the instance.
   */
  public ObjectNode genInstance(String prefix, String concept) {
    return this.objectMapper.createObjectNode()
        .put(ShaclResource.ID_KEY, prefix + UUID.randomUUID())
        .put(ShaclResource.TYPE_KEY, concept);
  }

  /**
   * Generates a literal node based on input.
   * 
   * @param literalVal The value for the literal.
   * @param dataType   Indicates the data type for the literal. Defaults to string
   *                   if null.
   */
  public ObjectNode genLiteral(String literalVal, String dataType) throws NumberFormatException {
    if (dataType.equals(ShaclResource.XSD_DECIMAL) && literalVal.isEmpty()) {
      LOGGER.warn("Numeric value cannot be an empty string!");
      throw new NumberFormatException("Numeric value cannot be an empty string!");
    }
    ObjectNode literal = this.objectMapper.createObjectNode()
        .put(ShaclResource.VAL_KEY, literalVal);
    literal.put(ShaclResource.TYPE_KEY, dataType == null ? ShaclResource.XSD_STRING : dataType);
    return literal;
  }

  /**
   * Creates an empty object node.
   */
  public ObjectNode genObjectNode() {
    return this.objectMapper.createObjectNode();
  }

  /**
   * Retrieves an object node from a JSON node.
   * 
   * @param input The JSON node.
   */
  public ObjectNode getObjectNode(JsonNode input) {
    if (input.isObject()) {
      return (ObjectNode) input;
    } else {
      String inputString = input.toPrettyString();
      LOGGER.error("Invalid object input: {}", inputString);
      throw new IllegalArgumentException(MessageFormat.format("Invalid object input: {}", inputString));
    }
  }

  /**
   * Creates an empty array node.
   */
  public ArrayNode genArrayNode() {
    return this.objectMapper.createArrayNode();
  }

  /**
   * Generates an array node from the input JSON nodes.
   * 
   * @param inputs The required JSON nodes as inputs.
   */
  public ArrayNode genArrayNode(JsonNode... inputs) {
    ArrayNode placeholder = this.objectMapper.createArrayNode();

    for (JsonNode input : inputs) {
      if (input.isArray()) {
        StreamSupport.stream(input.spliterator(), false)
            .forEach(placeholder::add);
      } else {
        // object nodes should be added directly
        placeholder.add(input);
      }
    }
    return placeholder;
  }

  /**
   * Retrieves an object node from a JSON node.
   * 
   * @param input The JSON node.
   */
  public ArrayNode getArrayNode(JsonNode input) {
    if (input.isArray()) {
      return (ArrayNode) input;
    } else {
      String inputString = input.toPrettyString();
      LOGGER.error("Invalid array input: {}", inputString);
      throw new IllegalArgumentException(MessageFormat.format("Invalid array input: {}", inputString));
    }
  }
}
