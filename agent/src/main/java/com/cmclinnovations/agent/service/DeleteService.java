package com.cmclinnovations.agent.service;

import java.util.Queue;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.QueryResource;

@Service
public class DeleteService {
  private final KGService kgService;
  private final GetService getService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final QueryTemplateService queryTemplateService;

  private static final int DEPENDENCY_DISPLAY_LIMIT = 10;
  private static final Logger LOGGER = LogManager.getLogger(DeleteService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param kgService            KG service for performing the query.
   * @param queryTemplateService Service for generating query templates.
   */
  public DeleteService(KGService kgService, GetService getService,
      ResponseEntityBuilder responseEntityBuilder, QueryTemplateService queryTemplateService) {
    this.kgService = kgService;
    this.getService = getService;
    this.responseEntityBuilder = responseEntityBuilder;
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
    LOGGER.debug("Deleting {} instance of {}", resourceID, targetId);
    // Query for optional parameters
    Set<String> optVarNames = this.kgService.getSparqlOptionalParameters(resourceID);
    // Generate query with branch validation
    String query = this.queryTemplateService.genDeleteQuery(resourceID, targetId, branchName, optVarNames);
    return this.kgService.delete(query, targetId);
  }

  /**
   * Run an optional query to check for dependent data before deleting an instance.
   * 
   * @param resourceID The target resource identifier for the instance.
   * @param targetId   The target instance IRI.
   */
  public String safeguard(String resourceID, String targetId) {
    LOGGER.debug("Safeguarding delete of {} instance of {}", resourceID, targetId);
    // Find the query with ID replaced
    String safeguardQuery = this.queryTemplateService.getSafeguardQuery(resourceID, targetId);
    if (!safeguardQuery.isEmpty()) {
      Queue<SparqlBinding> dependent = this.getService.getInstances(safeguardQuery);
      if (!dependent.isEmpty()) {
        String dependentList = dependent.stream()
            .map(binding -> binding.getFieldValue(QueryResource.DEPENDENT_KEY))
            .limit(DEPENDENCY_DISPLAY_LIMIT)
            .collect(java.util.stream.Collectors.joining(", "));

        if (dependent.size() > DEPENDENCY_DISPLAY_LIMIT) {
          dependentList += "... and more";
        }

        return "Cannot delete this instance due to dependent data: " + dependentList;
      }
    } else {
      LOGGER.warn("No dependency check in place for {}", resourceID);
    }
    return "";
  }
}
