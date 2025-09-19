package com.cmclinnovations.agent.template.query;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfLiteral.StringLiteral;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfObject;

import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
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

    this.appendOptionalIdFilters(selectTemplate, params.targetId(), params.parent());
    return super.appendAdditionalPatterns(selectTemplate, params.addQueryStatements());
  }

  /**
   * Appends optional filters related to IDs to the query if required.
   * 
   * @param selectTemplate Target select query template.
   * @param filterId       An optional field to target the query at a specific
   *                       instance.
   * @param parentField    An optional parent field to target the query with
   *                       specific parents.
   */
  private void appendOptionalIdFilters(SelectQuery selectTemplate, String filterId, ParentField parentField) {
    RdfObject object = QueryResource.ID_VAR;
    // Add filter clause for a parent field instead if available
    if (parentField != null) {
      Variable parsedField = null;
      String normalizedParentFieldName = QueryResource.genVariable(parentField.name()).getVarName();
      for (Variable variable : super.variables) {
        if (variable.getVarName().endsWith(normalizedParentFieldName)) {
          parsedField = variable;
          break;
        }
      }
      if (parsedField == null) {
        LOGGER.error("Unable to find matching variable for parent field: {}", parentField.name());
        throw new IllegalArgumentException(
            MessageFormat.format("Unable to find matching variable for parent field: {0}", parentField.name()));
      }
      selectTemplate.where(parsedField.has(QueryResource.DC_TERM_ID, Rdf.literalOf(parentField.id())));
    } else if (!filterId.isEmpty()) {
      StringLiteral filter = Rdf.literalOf(filterId);
      object = filter;
      selectTemplate.where(Expressions.bind(Expressions.str(filter), QueryResource.ID_VAR));
    }
    selectTemplate.where(QueryResource.IRI_VAR.has(QueryResource.DC_TERM_ID, object));
  }
}
