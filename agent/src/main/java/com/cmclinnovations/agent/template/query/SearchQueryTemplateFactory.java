package com.cmclinnovations.agent.template.query;

import java.util.HashMap;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.utils.StringResource;

public class SearchQueryTemplateFactory extends QueryTemplateFactory {
  private static final Logger LOGGER = LogManager.getLogger(SearchQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   * 
   */
  public SearchQueryTemplateFactory() {
    // no initialisation step is required
  }

  /**
   * Generate a SPARQL query template to get the data that meets the search
   * criteria. It will typically be in the following format:
   * 
   * SELECT ?iri WHERE {
   * ?iri a <clazz_iri>.
   * ?iri <prop_path>/<sub_path> ?string_property.
   * ?iri <prop_path>/<sub_number_path> ?number_property.
   * FILTER(STR(?string_property) = STR("string criteria"))
   * FILTER(?number_property >= min_value && ?number_property <= max_value)
   * }
   * 
   * @param bindings  The bindings queried from SHACL restrictions that should
   *                  be in the template.
   * @param criterias All the required search criteria.
   */
  public Queue<String> write(QueryTemplateFactoryParameters params) {
    Map<String, String> queryLines = new HashMap<>();
    LOGGER.info("Generating a query template for getting the data that matches the search criteria...");
    StringBuilder whereBuilder = new StringBuilder();
    StringBuilder filters = new StringBuilder();
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    super.sortBindings(params.bindings(), queryLines);

    queryLines.entrySet().forEach(currentLine -> {
      String variable = currentLine.getKey();
      // Do not generate or act on any id query lines
      if (!variable.equals("id")) {
        // note that if no criteria or empty string is passed in the API, the filter
        // will not be added
        if (params.criterias().containsKey(variable) && !params.criterias().get(variable).isEmpty()) {
          // If there is no search filters to be added, this variable should not be added
          String searchFilters = super.genSearchCriteria(variable, params.criterias());
          if (!searchFilters.isEmpty()) {
            whereBuilder.append(currentLine.getValue());
            filters.append(searchFilters);
          }
        }
      }
    });
    return super.genFederatedQuery("?iri", whereBuilder.append(filters).toString(), targetClass);
  }
}
