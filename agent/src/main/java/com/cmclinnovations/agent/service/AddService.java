package com.cmclinnovations.agent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.rules.RuleUtil;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AddService {
  private final JsonLdService jsonLdService;
  private final KGService kgService;
  private final QueryTemplateService queryTemplateService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(AddService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param jsonLdService         A service for interactions with JSON LD.
   * @param kgService             KG service for performing the query.
   * @param queryTemplateService  Service for generating query templates.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public AddService(JsonLdService jsonLdService, KGService kgService, QueryTemplateService queryTemplateService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.jsonLdService = jsonLdService;
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Overloaded method to instantiate the target instance following the input
   * parameters. ID field will default to a random UUID if no id parameter is
   * sent.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param param      Request parameters.
   */
  public ResponseEntity<StandardApiResponse> instantiate(String resourceID, Map<String, Object> param) {
    String id = param.getOrDefault(StringResource.ID_KEY, UUID.randomUUID()).toString();
    return instantiate(resourceID, id, param);
  }

  /**
   * Instantiates the target instance following the input parameters and the
   * target ID.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   * @param param      Request parameters.
   */
  public ResponseEntity<StandardApiResponse> instantiate(String resourceID, String targetId,
      Map<String, Object> param) {
    LOGGER.info("Instantiating an instance of {} ...", resourceID);
    // Update ID value to target ID
    param.put(StringResource.ID_KEY, targetId);
    // Retrieve the instantiation JSON schema
    ObjectNode addJsonSchema = this.queryTemplateService.getJsonLdTemplate(resourceID);
    // Attempt to replace all placeholders in the JSON schema
    this.recursiveReplacePlaceholders(addJsonSchema, null, null, param);
    // Add the static ID reference
    this.jsonLdService.appendId(addJsonSchema, targetId);
    return this.instantiateJsonLd(addJsonSchema, resourceID, LocalisationResource.SUCCESS_ADD_KEY);
  }

  /**
   * Instantiate an instance based on a jsonLD object.
   * 
   * @param jsonLdSchema    The target json LD object to instantiate.
   * @param resourceID      The target resource identifier for the instance.
   * @param messageResource The resource id of the message to be displayed when
   *                        successful.
   */
  public ResponseEntity<StandardApiResponse> instantiateJsonLd(JsonNode jsonLdSchema, String resourceID,
      String messageResource) {
    LOGGER.info("Adding instance to endpoint...");
    String instanceIri = jsonLdSchema.path(ShaclResource.ID_KEY).asText();
    String jsonString = jsonLdSchema.toString();

    ResponseEntity<String> response = this.kgService.add(jsonString);

    Model sparqlConstructRules = this.kgService.getShaclRules(resourceID, true);
    Model otherRules = this.kgService.getShaclRules(resourceID, false);
    if (response.getStatusCode() == HttpStatus.OK && (!sparqlConstructRules.isEmpty() || !otherRules.isEmpty())) {
      LOGGER.info("Detected rules! Instantiating inferred instances to endpoint...");
      this.kgService.execShaclRules(sparqlConstructRules);

      Model dataModel = this.kgService.readStringModel(jsonString, Lang.JSONLD);
      Model inferredData = RuleUtil.executeRules(dataModel, otherRules, null, null);
      try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
        RDFWriter.create()
            .source(inferredData)
            .format(RDFFormat.JSONLD)
            .output(out);
        String stringifiedInferredData = out.toString(StandardCharsets.UTF_8);
        response = this.kgService.add(stringifiedInferredData);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
    }

    if (response.getStatusCode() == HttpStatus.OK) {
      LOGGER.info("Instantiation is successful!");
      return this.responseEntityBuilder.success(instanceIri,
          LocalisationTranslator.getMessage(messageResource));
    }
    LOGGER.warn(response.getBody());
    throw new IllegalStateException(LocalisationTranslator.getMessage(LocalisationResource.ERROR_ADD_KEY));
  }

  /**
   * Replace the placeholders in the current node and recursively for its children
   * nodes based on the corresponding value in the replacement mappings if
   * available.
   * 
   * @param currentNode  Input contents to perform operation on.
   * @param parentNode   Parent node holding the current node, which must be an
   *                     object node. Arrays should be excluded.
   * @param parentField  Field name of the parent containing the current node.
   * @param replacements Mappings of the replacement value with their
   *                     corresponding node.
   */
  private void recursiveReplacePlaceholders(ObjectNode currentNode, ObjectNode parentNode, String parentField,
      Map<String, Object> replacements) {
    // If the current node has a single ID replacement key, verify if there is an
    // associated replacement value
    if (currentNode.size() == 1 && currentNode.has(ShaclResource.ID_KEY)
        && currentNode.get(ShaclResource.ID_KEY).has(ShaclResource.REPLACE_KEY)) {
      String replacement = this.jsonLdService.getReplacementValue((ObjectNode) currentNode.get(ShaclResource.ID_KEY),
          replacements);
      // When there is a missing replacement value, remove this field entirely and end
      // the method early; This step prevents the addition of blank nodes with empty
      // ID fields
      if (replacement.isEmpty()) {
        parentNode.remove(parentField);
        return;
      }
    }
    // If the current node is a replacement object, replace current node with
    // replacement value
    if (currentNode.has(ShaclResource.REPLACE_KEY)) {
      if (parentNode != null) {
        // Add a different interaction for schedule types
        if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals("schedule")) {
          this.replaceDayOfWeekSchedule(parentNode, parentField, replacements);
          // When parsing an array for an object node
        } else if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals(ShaclResource.ARRAY_KEY)) {
          ArrayNode resultArray = this.genFieldArray(currentNode, replacements);
          parentNode.set(parentField, resultArray);
          // Parse literal with data types differently
        } else if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals("literal")
            && currentNode.has(ShaclResource.DATA_TYPE_PROPERTY)) {
          String replacement = this.jsonLdService.getReplacementValue(currentNode, replacements);
          if (replacement.isEmpty()) { // Remove empty replacements
            parentNode.remove(parentField);
          } else {
            ObjectNode literalNode = this.jsonLdService.genLiteral(replacement,
                currentNode.path(ShaclResource.DATA_TYPE_PROPERTY).asText());
            parentNode.set(parentField, literalNode);
          }
          // IRIs that are not assigned to @id or @type should belong within a nested @id
          // object
        } else if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals(LifecycleResource.IRI_KEY)
            && !(parentField.equals(ShaclResource.ID_KEY) || parentField.equals(ShaclResource.TYPE_KEY))) {
          String replacement = this.jsonLdService.getReplacementValue(currentNode, replacements);
          if (replacement.isEmpty()) { // Remove empty replacements
            parentNode.remove(parentField);
          } else {
            ObjectNode newIriNode = this.jsonLdService.genObjectNode();
            newIriNode.put(ShaclResource.ID_KEY, replacement);
            parentNode.set(parentField, newIriNode);
          }
        } else {
          // For IRIs and literal with no other pattern, simply replace the value
          String replacement = this.jsonLdService.getReplacementValue(currentNode, replacements);
          if (replacement.isEmpty()) { // Remove empty replacements
            parentNode.remove(parentField);
          } else {
            parentNode.put(parentField, replacement);
          }
        }
      } else {
        LOGGER.error("Invalid parent node for replacement!");
        throw new IllegalArgumentException("Invalid parent node for replacement!");
      }
    } else {
      // Else recursively go deeper into the JSON object to find other replacements
      Queue<String> fieldNames = new ArrayDeque<>();
      currentNode.fieldNames().forEachRemaining(fieldNames::offer);
      while (!fieldNames.isEmpty()) {
        String fieldName = fieldNames.poll();
        JsonNode childNode = currentNode.get(fieldName);
        // For any form branch configuration field
        if (fieldName.equals(ShaclResource.BRANCH_KEY)) {
          ObjectNode matchedOption = this.findMatchingOption(
              this.jsonLdService.getArrayNode(currentNode.path(ShaclResource.BRANCH_KEY)),
              replacements.keySet());
          // Iterate and append each property in the target node to the current node
          Iterator<String> matchedOptionFieldNames = matchedOption.fieldNames();
          while (matchedOptionFieldNames.hasNext()) {
            String currentOptionField = matchedOptionFieldNames.next();
            this.recursiveReplacePlaceholders(matchedOption, currentNode, currentOptionField,
                replacements);
            JsonNode matchedField = matchedOption.path(currentOptionField);
            if (currentNode.has(currentOptionField)) {
              matchedField = this.jsonLdService.genArrayNode(currentNode.path(currentOptionField), matchedField);
            }
            // Append matched option field node to the current node
            currentNode.set(currentOptionField, matchedField);
          }
          currentNode.remove(ShaclResource.BRANCH_KEY); // Always remove the branch field once parsed
          // For all other fields
        } else {
          // If the child node is an object, recurse deeper
          if (childNode.isObject()) {
            recursiveReplacePlaceholders((ObjectNode) childNode, currentNode, fieldName, replacements);
          } else if (childNode.isArray()) {
            // If the child node contains an array, recursively parse through each object
            ArrayNode childrenNodes = (ArrayNode) childNode;
            Deque<Integer> removableIndexes = new ArrayDeque<>();
            Queue<JsonNode> additionalNodes = new ArrayDeque<>();
            for (int i = 0; i < childrenNodes.size(); i++) {
              ObjectNode currentChildNode = this.jsonLdService.getObjectNode(childrenNodes.get(i));
              // If child node is an array field
              if (currentChildNode.path(ShaclResource.TYPE_KEY).asText().equals(ShaclResource.ARRAY_KEY)) {
                ArrayNode resultArray = this.genFieldArray(currentChildNode, replacements);
                // Store the results and removable index
                for (JsonNode element : resultArray) {
                  additionalNodes.offer(element);
                }
                removableIndexes.addFirst(i);
              } else {
                // Assumes that the nodes in the array are object node
                this.recursiveReplacePlaceholders(currentChildNode, currentNode, fieldName, replacements);
              }
            }
            // Remove the array fields that should be removed
            while (!removableIndexes.isEmpty()) {
              childrenNodes.remove(removableIndexes.removeFirst());
            }
            // Add all additional nodes
            while (!additionalNodes.isEmpty()) {
              childrenNodes.add(additionalNodes.poll());
            }
          }
        }
      }
    }
  }

  /**
   * Search for the option that matches most of the replacement fields in the
   * array.
   * 
   * @param options        List of options to filter.
   * @param matchingFields A list containing the fields for matching.
   */
  private ObjectNode findMatchingOption(ArrayNode options, Set<String> matchingFields) {
    // Remove irrelevant parameters
    matchingFields.remove("entity");
    ObjectNode bestMatchNode = this.jsonLdService.genObjectNode();
    int maxMatches = -1;
    for (JsonNode currentOption : options) {
      Set<String> availableFields = new HashSet<>();
      this.recursiveFindReplaceFields(currentOption, availableFields);
      // Only continue parsing when there are less fields than matching fields
      // When there are more available fields than matching fields, multiple
      // options may matched and cannot be discerned
      if (availableFields.size() <= matchingFields.size()) {
        // Verify if there are more matched fields than the current maximum
        Set<String> intersection = new HashSet<>(availableFields);
        intersection.retainAll(matchingFields);
        if (intersection.size() > maxMatches) {
          maxMatches = intersection.size();
          bestMatchNode = this.jsonLdService.getObjectNode(currentOption);
        }
      }
    }
    return bestMatchNode;
  }

  /**
   * Recursively iterate through the current node to find all replacement fields.
   * 
   * @param currentNode The current object node for iteration.
   * @param foundFields A list storing the fields that have already been found.
   */
  private void recursiveFindReplaceFields(JsonNode currentNode, Set<String> foundFields) {
    // For an replacement object
    if (currentNode.has(ShaclResource.REPLACE_KEY)) {
      String replaceValue = currentNode.path(ShaclResource.REPLACE_KEY).asText();
      // Add the replace value if it has yet to be found
      if (!foundFields.contains(replaceValue)) {
        foundFields.add(replaceValue);
      }
    } else {
      Iterator<String> fields = currentNode.fieldNames();
      while (fields.hasNext()) {
        JsonNode currentField = currentNode.path(fields.next());
        if (currentField.isArray()) {
          for (JsonNode arrayItemNode : currentField) {
            this.recursiveFindReplaceFields(arrayItemNode, foundFields);
          }
        } else {
          this.recursiveFindReplaceFields(currentField, foundFields);
        }
      }
    }
  }

  /**
   * Generates a field array node from the replacement node of array type.
   * 
   * @param replacementNode Input contents to perform operation on.
   * @param replacements    Mappings of the replacement value with their
   *                        corresponding node.
   */
  private ArrayNode genFieldArray(ObjectNode replacementNode, Map<String, Object> replacements) {
    ArrayNode resultArray = this.jsonLdService.genArrayNode();
    ObjectNode arrayTemplate = this.jsonLdService.getObjectNode(replacementNode.path(ShaclResource.CONTENTS_KEY));

    String arrayFieldName = replacementNode.path(ShaclResource.REPLACE_KEY).asText();
    List<Map<String, Object>> arrayFields = (List<Map<String, Object>>) replacements.get(arrayFieldName);
    arrayFields.forEach(arrayField -> {
      // Copy the template to prevent any modification
      ObjectNode currentArrayItem = arrayTemplate.deepCopy();
      arrayField.putAll(replacements);// place existing replacements into the array mappings
      arrayField.put(StringResource.ID_KEY, UUID.randomUUID()); // generate a new ID key for each item in the array
      this.recursiveReplacePlaceholders(currentArrayItem, null, null, arrayField);
      resultArray.add(currentArrayItem);
    });
    return resultArray;
  }

  /**
   * Replaces the json model for the day of week schedules depending on the
   * indication in the request parameters.
   * 
   * @param parentNode   Parent node holding the day of week schedule
   *                     representation.
   * @param parentField  Field name of the parent containing the current node.
   * @param replacements Mappings of the replacement value with their
   *                     corresponding node.
   */
  private void replaceDayOfWeekSchedule(ObjectNode parentNode, String parentField, Map<String, Object> replacements) {
    // Note that this method assumes that the explicit recurrence interval will
    // always contain an array of items
    ArrayNode results = this.jsonLdService.genArrayNode(); // Empty array to store values
    // First iterate through the schedule array and retrieve all items that are not
    // the replacement object
    ArrayNode nodes = this.jsonLdService.getArrayNode(parentNode.path(parentField));
    for (int i = 0; i < nodes.size(); i++) {
      JsonNode currentScheduleNode = nodes.get(i);
      if (currentScheduleNode.isObject() && !currentScheduleNode.has(ShaclResource.REPLACE_KEY)) {
        results.add(currentScheduleNode);
      }
    }

    // Generate a queue of days of week
    Queue<String> daysOfWeek = new ArrayDeque<>();
    daysOfWeek.offer("Monday");
    daysOfWeek.offer("Tuesday");
    daysOfWeek.offer("Wednesday");
    daysOfWeek.offer("Thursday");
    daysOfWeek.offer("Friday");
    daysOfWeek.offer("Saturday");
    daysOfWeek.offer("Sunday");

    // Iterate over the queue
    while (!daysOfWeek.isEmpty()) {
      String currentDay = daysOfWeek.poll();
      // Parameter name is in lowercase based on the frontend
      if (replacements.containsKey(currentDay.toLowerCase()) && (boolean) replacements.get(currentDay.toLowerCase())) {
        // Only include the selected day if it has been selected on the frontend
        ObjectNode currentDayNode = this.jsonLdService.genObjectNode();
        currentDayNode.put(ShaclResource.ID_KEY,
            "https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/"
                + currentDay + "");
        results.add(currentDayNode);
      }
    }
    parentNode.set(parentField, results);
  } 
}
