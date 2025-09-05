package com.cmclinnovations.agent.template.query;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

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
  public Queue<String> write(QueryTemplateFactoryParameters params) {
    LOGGER.info("Generating a query template for getting data...");
    StringBuilder selectVariableBuilder = new StringBuilder();
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    String whereClauseLines = super.genWhereClauseContent(params.bindings());

    // Retrieve only the property fields if no sequence of variable is present
    if (super.varSequence.isEmpty()) {
      selectVariableBuilder.append(QueryResource.IRI_VAR.getQueryString())
          .append(ShaclResource.WHITE_SPACE).append(QueryResource.ID_VAR.getQueryString());
      super.variables.forEach(variable -> selectVariableBuilder.append(ShaclResource.WHITE_SPACE)
          .append(variable.getQueryString()));
      params.addVars().forEach((field, fieldSequence) -> selectVariableBuilder.append(ShaclResource.WHITE_SPACE)
          .append(QueryResource.genVariable(field).getQueryString()));
    } else {
      super.varSequence.putAll(params.addVars());
      List<String> sortedSequence = new ArrayList<>(super.varSequence.keySet());
      sortedSequence
          .sort((key1, key2) -> ShaclResource.compareLists(super.varSequence.get(key1), super.varSequence.get(key2)));
      sortedSequence.add(0, StringResource.ID_KEY);
      // Append a ? before the property
      sortedSequence
          .forEach(variable -> selectVariableBuilder.append(QueryResource.genVariable(variable).getQueryString())
              .append(ShaclResource.WHITE_SPACE));
      super.setSequence(sortedSequence);
    }

    StringBuilder whereBuilder = new StringBuilder(whereClauseLines);
    this.appendOptionalIdFilters(whereBuilder, params.targetId(), params.parent());
    whereBuilder.append(params.addQueryStatements());
    return super.genFederatedQuery(selectVariableBuilder.toString(), whereBuilder.toString(), targetClass);
  }

  /**
   * Appends optional filters related to IDs to the query if required.
   * 
   * @param query       Builder for the query template.
   * @param filterId    An optional field to target the query at a specific
   *                    instance.
   * @param parentField An optional parent field to target the query with specific
   *                    parents.
   */
  private void appendOptionalIdFilters(StringBuilder query, String filterId, ParentField parentField) {
    String subject = QueryResource.IRI_VAR.getQueryString();
    String object = QueryResource.ID_VAR.getQueryString();
    // Add filter clause for a parent field instead if available
    if (parentField != null) {
      String parsedFieldName = "";
      String normalizedParentFieldName = QueryResource.genVariable(parentField.name()).getVarName();
      for (Variable variable : super.variables) {
        if (variable.getVarName().endsWith(normalizedParentFieldName)) {
          parsedFieldName = variable.getQueryString();
          break;
        }
      }
      if (parsedFieldName.isEmpty()) {
        LOGGER.error("Unable to find matching variable for parent field: {}", parentField.name());
        throw new IllegalArgumentException(
            MessageFormat.format("Unable to find matching variable for parent field: {0}", parentField.name()));
      }
      StringResource.appendTriple(query, parsedFieldName, Rdf.iri(ShaclResource.DC_TERMS_ID).getQueryString(),
          Rdf.literalOf(parentField.id()).getQueryString());
    } else if (!filterId.isEmpty()) {
      object = Rdf.literalOf(filterId).getQueryString();
      query.append("BIND(\"").append(filterId).append("\" AS ?").append(StringResource.ID_KEY).append(")");
    }

    StringResource.appendTriple(query, subject, Rdf.iri(ShaclResource.DC_TERMS_ID).getQueryString(), object);
  }
}
