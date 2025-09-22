package com.cmclinnovations.agent.template.query;

import java.util.ArrayDeque;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expression;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.service.core.AuthenticationService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

public class SearchQueryTemplateFactory extends QueryTemplateFactory {
  private static final Logger LOGGER = LogManager.getLogger(SearchQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   * 
   */
  public SearchQueryTemplateFactory(AuthenticationService authenticationService) {
    super(authenticationService);
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
  public String write(QueryTemplateFactoryParameters params) {
    LOGGER.info("Generating a query template for getting the data that matches the search criteria...");
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    SelectQuery selectTemplate = super.genWhereClauseContent(targetClass, params.bindings());
    // Generating the search criteria as separate filter statements
    Queue<Expression<?>> filters = new ArrayDeque<>();
    super.variables.forEach(variable -> {
      String varName = variable.getVarName();
      // Do not generate or act on any id query lines
      // note that if no criteria or empty string is passed in the API, the filter
      // will not be added
      if (!varName.equals(QueryResource.ID_KEY) && params.criterias().containsKey(varName)
          && !params.criterias().get(varName).isEmpty()) {
        // If there is no search filters to be added, this variable should not be added
        Expression<?> searchFilters = this.genSearchCriteria(variable, params.criterias());
        if (searchFilters != null) {
          filters.offer(searchFilters);
        }
      }
    });

    StringBuilder filterString = new StringBuilder();
    // Add filters directly to these if available
    while (!filters.isEmpty()) {
      Expression<?> filterExpression = filters.poll();
      filterString.append("FILTER ( ")
          .append(filterExpression.getQueryString())
          .append(" )\n");
    }
    selectTemplate.select(QueryResource.IRI_VAR);
    return super.appendAdditionalPatterns(selectTemplate, filterString.toString());

  }

  /**
   * Generates the search criteria expressions of a query ie:
   * STR(?var) = STR(string_criteria))
   * 
   * @param variable The variable object.
   * @param criteria The criteria to be met.
   */
  private Expression<?> genSearchCriteria(Variable variable, Map<String, String> criterias) {
    String varName = variable.getVarName();
    String criteriaVal = criterias.get(varName);
    if (criteriaVal.isEmpty()) {
      return null;
    }
    // The front end will return a range value if range parsing is required
    if (criteriaVal.equals("range")) {
      String minCriteriaVal = criterias.get("min " + varName);
      String maxCriteriaVal = criterias.get("max " + varName);
      Expression<?> rangeExpression = null;
      // Append min filter if available
      if (!minCriteriaVal.isEmpty()) {
        rangeExpression = Expressions.gte(variable, Rdf.literalOf(this.formatRangeCriteria(minCriteriaVal)));
      }
      // Append max filter if available
      if (!maxCriteriaVal.isEmpty()) {
        // Prefix should be a conditional && if the min filter is already present
        Expression<?> maxSearchExpression = Expressions.lte(variable,
            Rdf.literalOf(this.formatRangeCriteria(maxCriteriaVal)));
        if (rangeExpression == null) {
          rangeExpression = maxSearchExpression;
        } else {
          rangeExpression = Expressions.and(rangeExpression, maxSearchExpression);
        }
      }
      // No filter for range is possible if not given
      return rangeExpression;
    }
    return Expressions.equals(Expressions.str(variable), Rdf.literalOf(criteriaVal));
  }

  /**
   * Formats the range criteria to a number. Defaults to integer. Else, it writes
   * a double.
   * 
   * @param criteriaVal The value for parsing.
   */
  private Number formatRangeCriteria(String criteriaVal) {
    try {
      return Integer.parseInt(criteriaVal);
    } catch (NumberFormatException e) {
      return Double.parseDouble(criteriaVal);
    }
  }
}
