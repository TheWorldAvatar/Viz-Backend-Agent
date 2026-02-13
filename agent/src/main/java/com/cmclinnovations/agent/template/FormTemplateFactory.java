package com.cmclinnovations.agent.template;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.service.core.AuthenticationService;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class FormTemplateFactory {
  private final AuthenticationService authenticationService;
  private final JsonLdService jsonLdService;

  // Data stores
  private Queue<JsonNode> properties;
  private Map<String, Object> form;
  private Map<String, JsonNode> groups;
  private Map<String, JsonNode> nodes;

  private final Map<String, String> context;
  private final Map<String, Object> idPropertyShape;
  private static final Logger LOGGER = LogManager.getLogger(FormTemplateFactory.class);

  /**
   * Constructs a new form template factory.
   * 
   * @param authService   A service to perform authentication operations.
   * @param jsonLdService A service for interactions with JSON LD.
   */
  public FormTemplateFactory(AuthenticationService authenticationService, JsonLdService jsonLdService) {
    this.authenticationService = authenticationService;
    this.jsonLdService = jsonLdService;
    this.context = this.setupContext();
    this.idPropertyShape = this.setupIdPropertyShape();
  }

  /**
   * Generate form template in JSON object format.
   * 
   * @param data        Data to be parsed for form template.
   * @param defaultVals Default values for the form template if there is an
   *                    existing entity.
   */
  public Map<String, Object> genTemplate(ArrayNode data, Map<String, Object> defaultVals) {
    this.reset(); // Reset each time method is called to prevent any data storage
    LOGGER.debug("Generating template from query results...");
    this.sortData(data);

    // No template should be generated if there are no properties
    if (this.properties.isEmpty()) {
      return new HashMap<>();
    } else {
      this.form.put(ShaclResource.CONTEXT_KEY, this.context);
      this.parseInputs(defaultVals);
    }

    return this.form;
  }

  /**
   * Resets the factory.
   */
  private void reset() {
    this.form = new HashMap<>();
    this.groups = new HashMap<>();
    this.nodes = new HashMap<>();
    this.properties = new ArrayDeque<>();
  }

  /**
   * Initialise the context for the form template.
   */
  private Map<String, String> setupContext() {
    Map<String, String> contextHolder = new HashMap<>();
    contextHolder.put(ShaclResource.COMMENT_PROPERTY, ShaclResource.RDFS_PREFIX + ShaclResource.COMMENT_PROPERTY);
    contextHolder.put(ShaclResource.LABEL_PROPERTY, ShaclResource.RDFS_PREFIX + ShaclResource.LABEL_PROPERTY);
    contextHolder.put(ShaclResource.PROPERTY_GROUP, ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_GROUP);
    contextHolder.put(ShaclResource.PROPERTY_SHAPE, ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_SHAPE);
    contextHolder.put(ShaclResource.NAME_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.NAME_PROPERTY);
    contextHolder.put(ShaclResource.DESCRIPTION_PROPERTY,
        ShaclResource.SHACL_PREFIX + ShaclResource.DESCRIPTION_PROPERTY);
    contextHolder.put(ShaclResource.ORDER_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.ORDER_PROPERTY);
    contextHolder.put(ShaclResource.NODE_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.NODE_PROPERTY);
    contextHolder.put(ShaclResource.GROUP_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.GROUP_PROPERTY);
    contextHolder.put(ShaclResource.PROPERTY_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_PROPERTY);
    contextHolder.put(ShaclResource.DEFAULT_VAL_PROPERTY,
        ShaclResource.SHACL_PREFIX + ShaclResource.DEFAULT_VAL_PROPERTY);
    contextHolder.put(ShaclResource.CLASS_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.CLASS_PROPERTY);
    contextHolder.put(ShaclResource.DATA_TYPE_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.DATA_TYPE_PROPERTY);
    contextHolder.put(ShaclResource.IN_PROPERTY, ShaclResource.SHACL_PREFIX + ShaclResource.IN_PROPERTY);
    return contextHolder;
  }

  /**
   * Initialise the ID property shape for the form template.
   */
  private Map<String, Object> setupIdPropertyShape() {
    ObjectNode idFieldNode = this.jsonLdService.genInstance("_:",
        ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_SHAPE);
    String idPropertyShapeDefinition = "{\"name\":{\"@value\":\"id\"}," +
        "\"description\":{\"@value\":\"ID\"}," +
        "\"order\":-1," +
        "\"datatype\":\"string\"," +
        "\"minCount\":{\"@type\":\"http://www.w3.org/2001/XMLSchema#integer\",\"@value\":\"1\"}," +
        "\"maxCount\":{\"@type\":\"http://www.w3.org/2001/XMLSchema#integer\",\"@value\":\"1\"}" +
        "}";
    ObjectNode idRemainderPropertyShape = (ObjectNode) this.jsonLdService.readObjectNode(idPropertyShapeDefinition);
    idFieldNode.setAll(idRemainderPropertyShape);
    return this.jsonLdService.convertValue(idFieldNode, new TypeReference<HashMap<String, Object>>() {
    });
  }

  /**
   * Sorts the data fields into property or property group.
   * 
   * @param fields Fields to category.
   */
  private void sortData(ArrayNode fields) {
    // All array nodes will be followed up with a get(index) if they are arrays,
    // else, path are used for object nodes
    for (JsonNode field : fields) {
      if (field.has(ShaclResource.TYPE_KEY)) {
        String type = field.path(ShaclResource.TYPE_KEY).get(0).asText();
        if (type.equals(ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_SHAPE)) {
          this.properties.offer(field);
        } else if (type.equals(ShaclResource.SHACL_PREFIX + ShaclResource.PROPERTY_GROUP)) {
          this.groups.put(field.path(ShaclResource.ID_KEY).asText(), field);
        } else if (type.equals(ShaclResource.SHACL_PREFIX + ShaclResource.NODE_SHAPE)) {
          this.nodes.put(field.path(ShaclResource.ID_KEY).asText(), field);
        } else {
          LOGGER.error("Invalid input node! Only property shape, property group, and node shape is allowed.");
          throw new IllegalArgumentException(
              "Invalid input node! Only property shape, property group, and node shape is allowed.");
        }
      }
    }
  }

  /**
   * Parse the property inputs into Spring Boot compliant JSON response format.
   * 
   * @param defaultVals Default values for the form template if there is an
   *                    existing entity.
   */
  private void parseInputs(Map<String, Object> defaultVals) {
    Map<String, Map<String, Map<String, Object>>> altProperties = new HashMap<>();
    Map<String, Map<String, Object>> defaultProperties = new HashMap<>();
    Set<String> userRoles = this.authenticationService.getUserRoles();
    while (!this.properties.isEmpty()) {
      JsonNode currentProperty = this.properties.poll();
      // If authorisation is enabled, and there are roles associated to the property,
      // only show the form field IF the user has the authority to do so
      if (this.authenticationService.isAuthenticationEnabled()
          && currentProperty.has(ShaclResource.TWA_FORM_PREFIX + ShaclResource.ROLE_PROPERTY)) {
        String unmappedPropertyRoles = currentProperty.path(ShaclResource.TWA_FORM_PREFIX + ShaclResource.ROLE_PROPERTY)
            .get(0).path(ShaclResource.VAL_KEY).asText();
        // Skip this iteration if permission is not given
        if (this.authenticationService.isUnauthorised(userRoles, unmappedPropertyRoles)) {
          continue;
        }
      }

      // If the property belongs to a node shape, extract the properties into another
      // set of mapping
      if (currentProperty.has(ShaclResource.TWA_FORM_PREFIX + ShaclResource.BELONGS_TO_PROPERTY)) {
        JsonNode belongsToObjectNode = currentProperty
            .path(ShaclResource.TWA_FORM_PREFIX + ShaclResource.BELONGS_TO_PROPERTY);
        ArrayNode typedTargetNode;
        if (belongsToObjectNode.isArray()) {
          typedTargetNode = (ArrayNode) belongsToObjectNode;
        } else {
          typedTargetNode = this.jsonLdService.genArrayNode();
          typedTargetNode.add(belongsToObjectNode);
        }
        for (JsonNode nodePropertyNode : typedTargetNode) {
          String nodeId = nodePropertyNode.path(ShaclResource.ID_KEY).asText();
          Map<String, Map<String, Object>> nodeProperties = altProperties.getOrDefault(nodeId, new HashMap<>());
          this.parseProperty(currentProperty, defaultVals, nodeProperties);
          altProperties.put(nodeId, nodeProperties);
        }
      } else {
        // Else simply generate the property
        this.parseProperty(currentProperty, defaultVals, defaultProperties);
      }
    }
    List<Map<String, Object>> outputDefaultProperties = this.genOutputs(defaultProperties);
    Map<String, Object> idShapeMappings = new HashMap<>(this.idPropertyShape);
    if (defaultVals.containsKey(QueryResource.ID_KEY)) {
      idShapeMappings.put(ShaclResource.DEFAULT_VAL_PROPERTY, defaultVals.get(QueryResource.ID_KEY));
    }
    outputDefaultProperties.add(0, idShapeMappings);
    this.form.put(ShaclResource.PROPERTY_PROPERTY, outputDefaultProperties);

    // Branches
    List<Map<String, Object>> nodeShape = new ArrayList<>();
    boolean hasOrderProperty = this.nodes.values().stream()
        .anyMatch(node -> node.has(ShaclResource.SHACL_ORDER_PROPERTY));
    this.nodes.forEach((key, node) -> {
      Map<String, Object> output = new HashMap<>();
      if (node.has(ShaclResource.SHACL_ORDER_PROPERTY)) {
        output.put(ShaclResource.SHACL_ORDER_PROPERTY,
            node.path(ShaclResource.SHACL_ORDER_PROPERTY).get(0).get(ShaclResource.VAL_KEY).asInt());
      } else if (hasOrderProperty) {
        output.put(ShaclResource.SHACL_ORDER_PROPERTY, -1);
      }
      output.put(ShaclResource.LABEL_PROPERTY,
          node.path(ShaclResource.RDFS_PREFIX + ShaclResource.LABEL_PROPERTY).get(0));
      output.put(ShaclResource.COMMENT_PROPERTY,
          node.path(ShaclResource.RDFS_PREFIX + ShaclResource.COMMENT_PROPERTY).get(0));
      output.put(ShaclResource.PROPERTY_PROPERTY,
          this.genOutputs(altProperties.getOrDefault(key, new HashMap<>())));
      nodeShape.add(output);
    });
    if (hasOrderProperty) {
      nodeShape.sort(Comparator.comparingInt(map -> (int) map.get(ShaclResource.SHACL_ORDER_PROPERTY)));
    }
    this.form.put(ShaclResource.NODE_PROPERTY, nodeShape);
  }

  /**
   * Parses and stores the properties and groups into the result mappings.
   * 
   * @param property       Target property.
   * @param defaultVals    Default values for the form template if there is an
   *                       existing entity.
   * @param resultMappings Mappings to store the parsed property.
   */
  private void parseProperty(JsonNode property, Map<String, Object> defaultVals,
      Map<String, Map<String, Object>> resultMappings) {
    // When there is a group
    if (property.has(ShaclResource.SHACL_PREFIX + ShaclResource.GROUP_PROPERTY)) {
      String groupId = property.path(ShaclResource.SHACL_PREFIX + ShaclResource.GROUP_PROPERTY)
          .get(0).path(ShaclResource.ID_KEY).asText();
      // Retrieve existing group in parsed model if available, or else, generate one
      // from the associated group
      Map<String, Object> group = resultMappings.getOrDefault(groupId,
          this.parseInputModel(this.groups.get(groupId), defaultVals));
      // Retrieve existing group properties in parsed model if available, or else,
      // generate one; Type cast is definitely accurate
      List<Map<String, Object>> groupProperties = (List<Map<String, Object>>) group
          .getOrDefault(ShaclResource.PROPERTY_PROPERTY, new ArrayList<>());
      // Add new property
      groupProperties.add(this.parseInputModel(property, defaultVals));
      // Update the results
      group.put(ShaclResource.PROPERTY_PROPERTY, groupProperties);
      resultMappings.put(groupId, group);
    } else {
      // Without a group, simply use the ID as hash key
      resultMappings.put(property.path(ShaclResource.ID_KEY).asText(),
          this.parseInputModel(property, defaultVals));
    }
  }

  /**
   * Parse the input into a suitable JSON model.
   * 
   * @param input       Input of interest.
   * @param defaultVals Default values for the form template if there is an
   *                    existing entity.
   */
  private Map<String, Object> parseInputModel(JsonNode input, Map<String, Object> defaultVals) {
    Map<String, Object> inputModel = new HashMap<>();
    // Transform each field into a suitable JSON format
    Iterator<Map.Entry<String, JsonNode>> iterator = input.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> shapeFieldEntry = iterator.next();
      String shapeField = shapeFieldEntry.getKey();
      JsonNode shapeFieldNode = shapeFieldEntry.getValue();
      switch (shapeField) {
        case ShaclResource.ID_KEY:
          // Id will always be a string
          inputModel.put(shapeField, shapeFieldNode.asText());
          break;
        case ShaclResource.TYPE_KEY:
          // Type will always be enclosed in a string array of one item
          inputModel.put(shapeField, shapeFieldNode.get(0).asText());
          break;
        case ShaclResource.SHACL_NAME_PROPERTY:
          Map<String, Object> nameLiteral = this.jsonLdService.convertValue(shapeFieldNode.get(0),
              new TypeReference<HashMap<String, Object>>() {
              });
          inputModel.put(StringResource.getLocalName(shapeField), nameLiteral);
          if (!defaultVals.isEmpty()) {
            String parsedField = nameLiteral.get(ShaclResource.VAL_KEY).toString().replace(ShaclResource.WHITE_SPACE,
                "_");
            // Retrieve field directly
            if (defaultVals.containsKey(parsedField)) {
              inputModel.put(ShaclResource.DEFAULT_VAL_PROPERTY, defaultVals.get(parsedField));
              // Retrieve field from array group if not found
            } else if (input.has(ShaclResource.SHACL_GROUP_PROPERTY)) {
              String groupId = input.get(ShaclResource.SHACL_GROUP_PROPERTY).get(0).get(ShaclResource.ID_KEY).asText();
              String groupName = this.groups.get(groupId).get(ShaclResource.RDFS_PREFIX + ShaclResource.LABEL_PROPERTY)
                  .get(0)
                  .get(ShaclResource.VAL_KEY).asText()
                  .replace(ShaclResource.WHITE_SPACE, "_");
              if (defaultVals.containsKey(groupName)) {
                List<Map<String, SparqlResponseField>> defaultArrayVals = (List<Map<String, SparqlResponseField>>) defaultVals
                    .get(groupName);
                inputModel.put(ShaclResource.DEFAULT_VAL_PROPERTY, defaultArrayVals.stream()
                    .map(map -> map.get(parsedField))
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList()));
              }
            }
          }
          break;
        case ShaclResource.SHACL_DEFAULT_VAL_PROPERTY:
          // Extract field name
          String fieldName = this.jsonLdService
              .convertValue(input.get(ShaclResource.SHACL_NAME_PROPERTY).get(0),
                  new TypeReference<HashMap<String, Object>>() {
                  })
              .get(ShaclResource.VAL_KEY)
              .toString().replace(ShaclResource.WHITE_SPACE,
                  "_");
          // When there are no pre-existing values for this field stored in defaultVals,
          // default to the default value set in the SHACL shape
          // This condition is necessary to prevent users from overwriting the
          // pre-existing value if any
          if (defaultVals.isEmpty() || defaultVals.containsKey(fieldName)) {
            JsonNode defaultValueNode = shapeFieldNode.get(0);
            String defaultVal = defaultValueNode.has(ShaclResource.ID_KEY)
                ? defaultValueNode.get(ShaclResource.ID_KEY).asText()
                : defaultValueNode.get(ShaclResource.VAL_KEY).asText();
            String fieldType = input.has(ShaclResource.SHACL_IN_PROPERTY)
                || input.has(ShaclResource.SHACL_CLASS_PROPERTY) ? "uri" : "literal";
            SparqlResponseField defaultValNode = new SparqlResponseField(fieldType, defaultVal, "", "");
            inputModel.put(ShaclResource.DEFAULT_VAL_PROPERTY, defaultValNode);
          }
          break;
        case ShaclResource.SHACL_ORDER_PROPERTY:
          Map<String, Object> orderMap = this.jsonLdService.convertValue(shapeFieldNode.get(0),
              new TypeReference<HashMap<String, Object>>() {
              });
          inputModel.put(StringResource.getLocalName(shapeField),
              Integer.valueOf(orderMap.get(ShaclResource.VAL_KEY).toString()));
          break;
        case ShaclResource.SHACL_DATA_TYPE_PROPERTY:
          // Data types are stored in @id key with xsd namespace
          // But we are only interested in the local name and extract it accordingly
          Map<String, Object> dataType = this.jsonLdService.convertValue(shapeFieldNode.get(0),
              new TypeReference<HashMap<String, Object>>() {
              });
          inputModel.put(StringResource.getLocalName(shapeField),
              StringResource.getLocalName(dataType.get(ShaclResource.ID_KEY).toString()));
          break;
        case ShaclResource.SHACL_IN_PROPERTY:
          ArrayNode inArray = (ArrayNode) shapeFieldNode;
          // Iterate and remove any blank node values
          Iterator<JsonNode> elements = inArray.elements();
          while (elements.hasNext()) {
            JsonNode currentElement = elements.next();
            if (currentElement.isObject()) {
              String valueConstraint = currentElement.get(ShaclResource.ID_KEY).asText();
              if (valueConstraint.startsWith("_:")) {
                elements.remove(); // Remove the current blank node
                break; // break iteration if blank node is found assuming only one blank node
              }
            }
          }
          // Convert the new array and append it into the output
          inputModel.put(ShaclResource.IN_PROPERTY,
              this.jsonLdService.convertValue(inArray, new TypeReference<List<HashMap<String, Object>>>() {
              }));
          break;
        default:
          // Every other fields are stored as a nested JSON object of key:value pair
          // within a one item JSON array
          inputModel.put(StringResource.getLocalName(shapeField),
              this.jsonLdService.convertValue(shapeFieldNode.get(0), new TypeReference<HashMap<String, Object>>() {
              }));
          break;
      }
    }
    return inputModel;
  }

  /**
   * Generates the Spring Boot compliant JSON response output from the target
   * properties.
   * 
   * @param properties target properties.
   */
  private List<Map<String, Object>> genOutputs(Map<String, Map<String, Object>> properties) {
    return properties.values().stream().map(propOrGroup -> {
      // For a property group which has `property` relations,
      // sort the properties before appending the group
      if (propOrGroup.containsKey(ShaclResource.PROPERTY_PROPERTY)) {
        List<Map<String, Object>> groupProperties = (List<Map<String, Object>>) propOrGroup
            .get(ShaclResource.PROPERTY_PROPERTY);
        groupProperties.sort(Comparator.comparingInt(map -> (int) map.get(ShaclResource.ORDER_PROPERTY)));
        propOrGroup.put(ShaclResource.PROPERTY_PROPERTY, groupProperties);
      }
      return propOrGroup;
    }).sorted(Comparator.comparingInt(map -> (int) map.get(ShaclResource.ORDER_PROPERTY))) // Sort results by order
        .collect(Collectors.toList());
  }
}
