package com.cmclinnovations.agent.template.query;

import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.utils.ShaclResource;
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
    LOGGER.info("Generating a query template for getting the data that matches the search criteria...");
    StringBuilder filters = new StringBuilder();
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    String whereClauseLines = super.genWhereClauseContent(params.bindings());

    StringBuilder whereBuilder = new StringBuilder(whereClauseLines);
    super.variables.forEach(variableWithMark -> {
      String variable = variableWithMark.replace(ShaclResource.VARIABLE_MARK, "");
      // Do not generate or act on any id query lines
      // note that if no criteria or empty string is passed in the API, the filter
      // will not be added
      if (!variable.equals("id") && params.criterias().containsKey(variable)
          && !params.criterias().get(variable).isEmpty()) {
        // If there is no search filters to be added, this variable should not be added
        String searchFilters = this.genSearchCriteria(variable, params.criterias());
        if (!searchFilters.isEmpty()) {
          filters.append(searchFilters);
        }
      }
    });
    return super.genFederatedQuery("?iri", whereBuilder.append(filters).toString(), targetClass);
  }

  /**
   * Generates the search criteria query line of a query ie:
   * FILTER(STR(?var) = STR(string_criteria))
   * 
   * @param variable The name of the variable.
   * @param criteria The criteria to be met.
   */
  private String genSearchCriteria(String variable, Map<String, String> criterias) {
    String criteriaVal = criterias.get(variable);
    String formattedVar = StringResource.parseQueryVariable(variable);
    if (criteriaVal.isEmpty()) {
      return criteriaVal;
    }
    // The front end will return a range value if range parsing is required
    if (criteriaVal.equals("range")) {
      String rangeQuery = "";
      String minCriteriaVal = criterias.get("min " + variable);
      String maxCriteriaVal = criterias.get("max " + variable);
      // Append min filter if available
      if (!minCriteriaVal.isEmpty()) {
        rangeQuery += "FILTER(?" + formattedVar + " >= " + criterias.get("min " + variable);
      }
      // Append max filter if available
      if (!maxCriteriaVal.isEmpty()) {
        // Prefix should be a conditional && if the min filter is already present
        rangeQuery += rangeQuery.isEmpty() ? "FILTER(?" : " && ?";
        rangeQuery += formattedVar + " <= " + maxCriteriaVal;
      }
      if (!rangeQuery.isEmpty()) {
        rangeQuery += ")";
      }
      // Return empty string otherwise
      return rangeQuery;
    }
    return "FILTER(STR(?" + formattedVar + ") = \"" + criteriaVal + "\")";
  }
}
