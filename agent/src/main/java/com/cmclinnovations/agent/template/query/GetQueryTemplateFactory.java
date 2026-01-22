package com.cmclinnovations.agent.template.query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.Queries;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject;

import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.model.ShaclPropertyBinding;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.service.core.AuthenticationService;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public class GetQueryTemplateFactory extends QueryTemplateFactory {
  private static final Logger LOGGER = LogManager.getLogger(GetQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   */
  public GetQueryTemplateFactory(AuthenticationService authenticationService) {
    super(authenticationService);
  }

  /**
   * Generate a SPARQL query template to get the required data. It will typically
   * be in the following format:
   * 
   * SELECT * WHERE {
   * ?iri a <clazz_iri>.
   * ?iri <prop_path>/<sub_path> ?property1.
   * ?iri <prop_path>/<prop_path2> ?property2.
   * }
   * 
   * @param params An object containing the following parameters to write, namely:
   *               bindings - SHACL restrictions
   *               targetId - Optional Filter constraint for a specific instance
   *               parent - Optional details if instances must be associated
   *               addQueryStatements - Optional additional query statements
   *               addVars - Optional additional variables to be included in the
   *               query, along with their order sequence
   */
  public String write(QueryTemplateFactoryParameters params) {
    LOGGER.info("Generating a query template for getting data...");
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    SelectQuery selectTemplate = super.genWhereClauseContent(targetClass, params.bindings());

    // Retrieve only the property fields if no sequence of variable is present
    if (super.varSequence.isEmpty()) {
      selectTemplate.select(QueryResource.IRI_VAR)
          .select(QueryResource.ID_VAR);
      super.variables.forEach(variable -> selectTemplate.select(variable));
      params.addVars().forEach((field, fieldSequence) -> selectTemplate.select(field));
    } else {
      super.varSequence.putAll(params.addVars());
      List<Variable> sortedSequence = new ArrayList<>(super.varSequence.keySet());
      sortedSequence
          .sort((key1, key2) -> ShaclResource.compareLists(super.varSequence.get(key1), super.varSequence.get(key2)));
      sortedSequence.add(0, QueryResource.ID_VAR);
      // Add variables
      sortedSequence
          .forEach(variable -> selectTemplate.select(variable));
      super.setSequence(sortedSequence);
    }

    String valuesClause = this.appendOptionalIdFilters(selectTemplate, params.targetIds());
    return super.appendAdditionalPatterns(selectTemplate, params.addQueryStatements() + valuesClause);
  }

  /**
   * Generate the WHERE clause of a query:
   * 
   * @param queryVarsAndPaths The query construction requirements.
   */
  public String genWhereClause(Queue<Queue<SparqlBinding>> queryVarsAndPaths) {
    LOGGER.info("Generating the WHERE clause...");
    super.reset();
    // Extract out select and where, test what we get
    Map<String, Map<String, ShaclPropertyBinding>> propertyBindingMap = super.parseNodeShapes(queryVarsAndPaths);
    return super.write(Queries.SELECT(), propertyBindingMap)
        .getQueryString()
        // SparqlBuilder concats OPTIONAL and UNION instead of wrapping them as nested,
        // code is an adjustment
        .replaceAll("OPTIONAL\\s*(\\{.*})\\s*UNION\\s*OPTIONAL\\s*(\\{.*\\})", "$1 UNION $2")
        // Extract only the WHERE clause content
        .replaceAll("(?s)SELECT\\s*\\*\\s*\\nWHERE\\s*\\{(.*)\\}\\n$", "$1");
  }

  /**
   * Appends optional filters related to IDs to the query if required.
   * 
   * @param selectTemplate Target select query template.
   * @param filterIds      Optional param for target instances.
   * @return A string representing the VALUES clause if multiple IDs are given
   */
  private String appendOptionalIdFilters(SelectQuery selectTemplate, Queue<List<String>> filterIds) {
    RdfObject object = QueryResource.ID_VAR;
    String valuesClause = "";
    if (filterIds.size() == 1) {
      List<String> currentIds = filterIds.poll();
      StringLiteral filter = Rdf.literalOf(currentIds.get(0));
      object = filter;
      selectTemplate.where(Expressions.bind(Expressions.str(filter), QueryResource.ID_VAR));
      if (currentIds.size() > 1) {
        valuesClause += QueryResource.values(QueryResource.EVENT_ID_VAR.getVarName(),
            List.of(Rdf.iri(currentIds.get(1)).getQueryString()));
      }
    } else if (filterIds.size() > 1) {
      List<String> idValues = new ArrayList<>();
      List<String> eventIdValues = new ArrayList<>();
      while (!filterIds.isEmpty()) {
        List<String> currentIds = filterIds.poll();
        String currentId = Rdf.literalOf(currentIds.get(0)).getQueryString();
        idValues.add(currentId);
        if (currentIds.size() > 1) {
          eventIdValues.add(Rdf.iri(currentIds.get(1)).getQueryString());
        }
      }
      valuesClause += QueryResource.values(QueryResource.ID_KEY, idValues);
      if (!eventIdValues.isEmpty()) {
        valuesClause += QueryResource.values(QueryResource.EVENT_ID_VAR.getVarName(), eventIdValues);
      }
    }
    selectTemplate.where(QueryResource.IRI_VAR.has(QueryResource.DC_TERM_ID, object));
    return valuesClause;
  }
}
