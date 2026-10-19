package com.cmclinnovations.agent.service.application;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.GetService;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.service.core.QueryTemplateService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

/**
 * Manages the presentation settings for a view model.
 */
@Service
public class ViewPresentationService {
  private final QueryTemplateService queryTemplateService;
  private final GetService getService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(ViewPresentationService.class);

  /**
   * Constructs a new service with the following dependencies.
   *
   * @param queryTemplateService  Query template retrieval.
   * @param getService            Service to execute the query.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public ViewPresentationService(QueryTemplateService queryTemplateService, GetService getService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.queryTemplateService = queryTemplateService;
    this.getService = getService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Retrieves all colour profiles of the target table
   *
   * @param table The table key that the profiles belong to.
   */
  public ResponseEntity<StandardApiResponse<?>> getCellFormattingRules(String resource) {
    if (resource == null || resource.isBlank()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY,
              resource));
    }
    LOGGER.debug("Retrieving the cell formatting rules for {}...", resource);
    String query = this.queryTemplateService.getQueryWithReplacements(
        FileService.VIEW_PRESENTATION_RULES_QUERY_RESOURCE,
        Rdf.literalOf(resource).getQueryString());
    Queue<SparqlBinding> results = this.getService.getInstances(query);

    Map<String, Object> rules = new HashMap<>();
    while (!results.isEmpty()) {
      SparqlBinding currentBinding = results.poll();
      String name = currentBinding.getFieldValue(ShaclResource.NAME_PROPERTY);
      String field = currentBinding.getFieldValue(StringResource.FIELD_REQUEST_PARAM);

      // Create a sub-map
      Map<String, Object> rule = new HashMap<>();
      rule.put(StringResource.OPERATOR_KEY, currentBinding.getFieldValue(StringResource.OPERATOR_KEY));
      rule.put(StringResource.VAL_KEY, currentBinding.getFieldValue(StringResource.VAL_KEY));
      rule.put(StringResource.BACKGROUND_KEY, currentBinding.getFieldValue(StringResource.BACKGROUND_KEY));
      if (currentBinding.containsField(StringResource.TEXT_KEY)) {
        rule.put(StringResource.TEXT_KEY, currentBinding.getFieldValue(StringResource.TEXT_KEY));
      }

      // Append to the list corresponding to this name as a key
      Map<String, List<Map<String, Object>>> currentResourceRules = (Map<String, List<Map<String, Object>>>) rules
          .computeIfAbsent(name, k -> new HashMap<>());
      List<Map<String, Object>> existingResourceFieldRuleList = currentResourceRules
          .computeIfAbsent(field, k -> new ArrayList<>());
      existingResourceFieldRuleList.add(rule);
    }
    LOGGER.info("Retrieved {} cell formatting rules for {}", rules.size(), resource);
    return this.responseEntityBuilder.success(null, rules);
  }
}
