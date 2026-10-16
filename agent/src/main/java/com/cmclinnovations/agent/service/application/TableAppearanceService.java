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
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.TableAppearanceResource;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Manages the shared appearance settings of tables in the viz frontend, such as
 * colour profiles, colsumn settings, and padding.
 */
@Service
public class TableAppearanceService {
  private final FileService fileService;
  private final GetService getService;
  private final ResponseEntityBuilder responseEntityBuilder;
  private final ObjectMapper objectMapper;

  private static final Logger LOGGER = LogManager.getLogger(TableAppearanceService.class);

  /**
   * Constructs a new service with the following dependencies.
   *
   * @param fileService           File service for accessing file resources.
   * @param getService            Service to execute the query.
   * @param responseEntityBuilder A component to build the response entity.
   * @param objectMapper          The JSON object mapper.
   */
  public TableAppearanceService(FileService fileService, GetService getService,
      ResponseEntityBuilder responseEntityBuilder, ObjectMapper objectMapper) {
    this.fileService = fileService;
    this.getService = getService;
    this.responseEntityBuilder = responseEntityBuilder;
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieves all colour profiles of the target table
   *
   * @param table The table key that the profiles belong to.
   */
  public ResponseEntity<StandardApiResponse<?>> getColourProfiles(String table) {
    if (table == null || table.isBlank()) {
      throw new IllegalArgumentException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FIELD_KEY,
              TableAppearanceResource.TABLE_KEY));
    }
    LOGGER.debug("Retrieving the colour profiles for the table {}...", table);
    String query = this.fileService.getContentsWithReplacement(FileService.TABLE_COLOUR_PROFILE_QUERY_RESOURCE,
        Rdf.literalOf(table).getQueryString());
    Queue<SparqlBinding> results = this.getService.getInstances(query);
    List<Map<String, Object>> profiles = new ArrayList<>();
    while (!results.isEmpty()) {
      Map<String, Object> profile = this.parseColourProfile(results.poll());
      if (profile != null) {
        profiles.add(profile);
      }
    }
    LOGGER.info("Retrieved {} colour profile(s) for the table {}", profiles.size(), table);
    return this.responseEntityBuilder.success(null, profiles);
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
