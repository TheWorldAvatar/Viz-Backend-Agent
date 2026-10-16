package com.cmclinnovations.agent.service.application;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.TableAppearanceResource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages the presentation settings for a view model.
 */
@Service
public class ViewPresentationService {
  private final QueryTemplateService queryTemplateService;
  private final GetService getService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final ObjectMapper objectMapper;

  private static final Logger LOGGER = LogManager.getLogger(ViewPresentationService.class);

  /**
   * Constructs a new service with the following dependencies.
   *
   * @param queryTemplateService  Query template retrieval.
   * @param getService            Service to execute the query.
   * @param responseEntityBuilder A component to build the response entity.
   * @param objectMapper          The JSON object mapper.
   */
  public ViewPresentationService(QueryTemplateService queryTemplateService, GetService getService,
      ResponseEntityBuilder responseEntityBuilder, ObjectMapper objectMapper) {
    this.queryTemplateService = queryTemplateService;
    this.getService = getService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.objectMapper = objectMapper;
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
    List<Map<String, Object>> rules = new ArrayList<>();
    while (!results.isEmpty()) {
      Map<String, Object> rule = this.parseColourProfile(results.poll());
      if (rule != null) {
        rules.add(rule);
      }
    }
    LOGGER.info("Retrieved {} cell formattinng rules for {}", rules.size(), resource);
    return this.responseEntityBuilder.success(null, rules);
  }

  /**
   * Parses the binding into a colour profile. Returns null if the stored rules
   * are not a valid JSON array.
   *
   * @param binding The query result for one colour profile.
   */
  private Map<String, Object> parseColourProfile(SparqlBinding binding) {
    String iri = binding.getFieldValue(QueryResource.ID_KEY);
    JsonNode rules;
    try {
      rules = this.objectMapper.readTree(binding.getFieldValue(TableAppearanceResource.RULES_KEY));
    } catch (JacksonException e) {
      LOGGER.warn("Skipping colour profile {} as its rules are not valid JSON: {}", iri, e.getOriginalMessage());
      return null;
    }
    if (rules == null || !rules.isArray()) {
      LOGGER.warn("Skipping colour profile {} as its rules are not a JSON array!", iri);
      return null;
    }
    Map<String, Object> profile = new LinkedHashMap<>();
    profile.put(QueryResource.ID_KEY, iri);
    profile.put(TableAppearanceResource.TABLE_KEY, binding.getFieldValue(TableAppearanceResource.TABLE_KEY));
    profile.put(TableAppearanceResource.NAME_KEY, binding.getFieldValue(TableAppearanceResource.NAME_KEY));
    // Convert to plain Java collections so that the rules serialise as an array
    profile.put(TableAppearanceResource.RULES_KEY, this.objectMapper.convertValue(rules, List.class));
    return profile;
  }
}
