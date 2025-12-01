package com.cmclinnovations.agent.service;

import java.util.Iterator;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class DeleteService {
  private final KGService kgService;
  private final QueryTemplateService queryTemplateService;

  private static final Logger LOGGER = LogManager.getLogger(DeleteService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param kgService            KG service for performing the query.
   * @param queryTemplateService Service for generating query templates.
   */
  public DeleteService(KGService kgService, QueryTemplateService queryTemplateService) {
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
  }

  /**
   * Delete the instance associated with the target identifier.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   * @param branchName The branch name to filter (can be null).
   */
  public ResponseEntity<StandardApiResponse<?>> delete(String resourceID, String targetId, String branchName) {

    LOGGER.debug("Deleting {} instance of {} with branch = {}", resourceID, targetId, branchName);

    // Get template to check if it has branches
    ObjectNode template = this.queryTemplateService.getJsonLdTemplate(resourceID);
    boolean templateHasBranches = hasBranches(template);

    if (templateHasBranches && (branchName == null || branchName.isEmpty())) {
      String errorMsg = String.format(
          "Template for '%s' contains branches but no 'branch_delete' parameter provided. " +
              "Cannot delete without specifying which branch.",
          resourceID);
      LOGGER.error(errorMsg);
      throw new IllegalArgumentException(errorMsg);
    }

    String query = this.queryTemplateService.genDeleteQuery(resourceID, targetId, branchName);
    return this.kgService.delete(query, targetId);
  }

  /**
   * Check if the JSON-LD template contains any @branch arrays
   */
  private boolean hasBranches(ObjectNode template) {
    return recursivelyCheckForBranches(template);
  }

  /**
   * Recursively search for @branch fields in the template
   */
  private boolean recursivelyCheckForBranches(JsonNode node) {
    if (node.isObject()) {
      ObjectNode objNode = (ObjectNode) node;

      // Check if this node has @branch
      if (objNode.has("@branch") && objNode.get("@branch").isArray()) {
        return true;
      }

      // Recurse into all fields
      Iterator<Map.Entry<String, JsonNode>> fields = objNode.fields();
      while (fields.hasNext()) {
        if (recursivelyCheckForBranches(fields.next().getValue())) {
          return true;
        }
      }
    } else if (node.isArray()) {
      // Recurse into array elements
      for (JsonNode element : node) {
        if (recursivelyCheckForBranches(element)) {
          return true;
        }
      }
    }
    return false;
  }
}
