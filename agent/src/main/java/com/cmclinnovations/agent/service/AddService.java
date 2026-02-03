package com.cmclinnovations.agent.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.UUID;

import org.apache.jena.rdf.model.Model;
import org.apache.jena.riot.Lang;
import org.apache.jena.riot.RDFFormat;
import org.apache.jena.riot.RDFWriter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.topbraid.shacl.rules.RuleUtil;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.ShaclRuleType;
import com.cmclinnovations.agent.model.type.TrackActionType;
import com.cmclinnovations.agent.service.core.ChangelogService;
import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class AddService {
  private final ChangelogService changelogService;
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
  public AddService(ChangelogService changelogService, JsonLdService jsonLdService, KGService kgService,
      QueryTemplateService queryTemplateService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.changelogService = changelogService;
    this.jsonLdService = jsonLdService;
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Overloaded method to instantiate the target instance following the input
   * parameters. ID field will default to a random UUID if no id parameter is
   * sent. Ignores any custom message and defaults to default messages.
   * 
   * @param resourceID  The target resource identifier for the instance.
   * @param param       Request parameters.
   * @param trackAction The action required for tracking.
   */
  public ResponseEntity<StandardApiResponse<?>> instantiate(String resourceID, Map<String, Object> param,
      TrackActionType trackAction) {
    return this.instantiate(resourceID, param, null, null, trackAction);
  }

  /**
   * Overloaded method to instantiate the target instance following the input
   * parameters. ID field will default to a random UUID if no id parameter is
   * sent.
   * 
   * @param resourceID  The target resource identifier for the instance.
   * @param param       Request parameters.
   * @param trackAction The action required for tracking.
   */
  public ResponseEntity<StandardApiResponse<?>> instantiate(String resourceID, Map<String, Object> param,
      String successLogMessage, String messageResource, TrackActionType trackAction) {
    String id = param.getOrDefault(QueryResource.ID_KEY, UUID.randomUUID()).toString();
    return this.instantiate(resourceID, id, param, successLogMessage, messageResource, trackAction);
  }

  /**
   * Instantiates the target instance following the input parameters and the
   * target ID.
   * 
   * @param resourceID  The target resource identifier for the instance.
   * @param targetId    The target instance IRI.
   * @param param       Request parameters.
   * @param trackAction The action required for tracking.
   */
  public ResponseEntity<StandardApiResponse<?>> instantiate(String resourceID, String targetId,
      Map<String, Object> param, String successLogMessage, String messageResource, TrackActionType trackAction) {
    LOGGER.info("Instantiating an instance of {} ...", resourceID);
    // Update ID value to target ID
    param.put(QueryResource.ID_KEY, targetId);
    // Retrieve the instantiation JSON schema
    ObjectNode addJsonSchema = this.queryTemplateService.getJsonLdTemplate(resourceID);

    // Attempt to replace all placeholders in the JSON schema
    this.recursiveReplacePlaceholders(addJsonSchema, null, null, param);
    // Add the static ID reference
    this.jsonLdService.appendId(addJsonSchema, targetId);
    return this.instantiateJsonLd(addJsonSchema, resourceID, successLogMessage, messageResource, trackAction);
  }

  /**
   * Logs the activity for the target instance.
   * 
   * @param iri         The target instance IRI.
   * @param trackAction The action required for tracking.
   */
  public void logActivity(String iri, TrackActionType trackAction) {
    Map<String, Object> agentDetails = this.changelogService.setAgent();
    Map<String, Object> actionDetails = this.changelogService.logAction(iri, trackAction);
    if (!agentDetails.isEmpty()) {
      String agentId = this.instantiate(QueryResource.HISTORY_AGENT_RESOURCE, agentDetails, TrackActionType.IGNORED)
          .getBody().data().id();
      actionDetails.put(QueryResource.HISTORY_AGENT_RESOURCE, agentId);
    }
    this.instantiate(QueryResource.HISTORY_ACTIVITY_RESOURCE, actionDetails, TrackActionType.IGNORED);
  }

  /**
   * Instantiate an instance based on a jsonLD object.
   * 
   * @param jsonLdSchema      The target json LD object to instantiate.
   * @param resourceID        The target resource identifier for the instance.
   * @param successLogMessage Optional log message on success.
   * @param messageResource   Optional resource id of the message to be displayed
   *                          when successful.
   * @param trackAction       The action required for tracking.
   */
  private ResponseEntity<StandardApiResponse<?>> instantiateJsonLd(JsonNode jsonLdSchema, String resourceID,
      String successLogMessage, String messageResource, TrackActionType trackAction) {
    LOGGER.info("Adding instance to endpoint...");
    String instanceIri = jsonLdSchema.path(ShaclResource.ID_KEY).asText();
    String jsonString = jsonLdSchema.toString();

    ResponseEntity<String> response = this.kgService.add(jsonString);

    Model sparqlConstructRules = this.kgService.getShaclRules(resourceID, ShaclRuleType.SPARQL_RULE);
    Model otherRules = this.kgService.getShaclRules(resourceID, ShaclRuleType.TRIPLE_RULE);
    if (response.getStatusCode() == HttpStatus.OK && (!sparqlConstructRules.isEmpty() || !otherRules.isEmpty())) {
      LOGGER.info("Detected rules! Instantiating inferred instances to endpoint...");
      this.kgService.execShaclRules(sparqlConstructRules, Rdf.iri(instanceIri).getQueryString());

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
      LOGGER.info(successLogMessage == null ? "Instantiation is successful!" : successLogMessage);
      if (trackAction != TrackActionType.IGNORED) {
        this.logActivity(instanceIri, trackAction);
      }
      return this.responseEntityBuilder.success(instanceIri,
          LocalisationTranslator
              .getMessage(messageResource == null ? LocalisationResource.SUCCESS_ADD_KEY : messageResource));
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
        if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals(LifecycleResource.SCHEDULE_RESOURCE)) {
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
        } else if (currentNode.path(ShaclResource.TYPE_KEY).asText().equals(QueryResource.IRI_KEY)
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
          String branchAdd = (String) replacements.get(QueryResource.ADD_BRANCH_KEY);
          if (branchAdd == null || branchAdd.isEmpty()) {
            throw new IllegalArgumentException("No branch specified for branch addition!");
          }
          ArrayNode branches = this.jsonLdService.getArrayNode(currentNode.path(ShaclResource.BRANCH_KEY));
          // Extract the matched option with the specific branch name
          ObjectNode matchedOption = this.jsonLdService.genObjectNode();
          for (JsonNode branchNode : branches) {
            if (branchNode.get(ShaclResource.BRANCH_KEY).asText().equals(branchAdd)) {
              ObjectNode branchObj = (ObjectNode) branchNode;
              // Remove branch key as it should not be reused
              branchObj.remove(ShaclResource.BRANCH_KEY);
              matchedOption = branchObj;
            }
          }

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
      Map<String, Object> currentFields = new HashMap<>(arrayField);
      arrayField.forEach((key, value) -> {
        currentFields.put(key.replaceFirst(arrayFieldName + ShaclResource.WHITE_SPACE, ""), value);
      });
      // Copy the template to prevent any modification
      ObjectNode currentArrayItem = arrayTemplate.deepCopy();
      currentFields.put(QueryResource.ID_KEY, UUID.randomUUID()); // generate a new ID key for each item in the array
      this.recursiveReplacePlaceholders(currentArrayItem, null, null, currentFields);
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
