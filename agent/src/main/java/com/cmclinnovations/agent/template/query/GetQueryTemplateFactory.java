package com.cmclinnovations.agent.template.query;

import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.cmclinnovations.agent.model.ParentField;
import com.cmclinnovations.agent.model.QueryTemplateFactoryParameters;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public class GetQueryTemplateFactory extends QueryTemplateFactory {
  private static final Logger LOGGER = LogManager.getLogger(GetQueryTemplateFactory.class);

  /**
   * Constructs a new query template factory.
   */
  public GetQueryTemplateFactory() {
    // no initialisation step is required
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
   * @param params An object containing four parameters to write, namely:
   *               bindings - SHACL restrictions
   *               targetId - Optional Filter constraint for a specific instance
   *               parent - Optional details if instances must be associated
   *               addQueryStatements - Optional additional query statements
   *               roles - Optional set containing the roles
   *               addVars - Optional additional variables to be included in the
   *               query, along with their order sequence
   */
  public Queue<String> write(QueryTemplateFactoryParameters params) {
    Map<String, String> queryLines = new HashMap<>();
    LOGGER.info("Generating a query template for getting data...");
    StringBuilder selectVariableBuilder = new StringBuilder();
    StringBuilder whereBuilder = new StringBuilder();
    // Extract the first binding class but it should not be removed from the queue
    String targetClass = params.bindings().peek().peek().getFieldValue(StringResource.CLAZZ_VAR);
    super.sortBindings(params.bindings(), queryLines, params.roles());

    // Retrieve only the property fields if no sequence of variable is present
    if (super.varSequence.isEmpty()) {
      selectVariableBuilder.append(ShaclResource.VARIABLE_MARK).append(LifecycleResource.IRI_KEY);
      super.variables.forEach(variable -> selectVariableBuilder.append(ShaclResource.WHITE_SPACE)
          .append(variable));
      params.addVars().forEach((field, fieldSequence) -> selectVariableBuilder.append(ShaclResource.WHITE_SPACE)
          .append(ShaclResource.VARIABLE_MARK)
          .append(StringResource.parseQueryVariable(field)));
    } else {
      super.varSequence.putAll(params.addVars());
      List<String> sortedSequence = new ArrayList<>(super.varSequence.keySet());
      sortedSequence
          .sort((key1, key2) -> ShaclResource.compareLists(super.varSequence.get(key1), super.varSequence.get(key2)));
      // Append a ? before the property
      sortedSequence.forEach(variable -> selectVariableBuilder.append(ShaclResource.VARIABLE_MARK)
          .append(StringResource.parseQueryVariable(variable))
          .append(ShaclResource.WHITE_SPACE));
      super.setSequence(sortedSequence);
    }
    queryLines.values().forEach(whereBuilder::append);
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
    // Add filter clause for a parent field instead if available
    if (parentField != null) {
      String parsedFieldName = "";
      String normalizedParentFieldName = StringResource.parseQueryVariable(parentField.name());
      for (String variable : super.variables) {
        if (variable.endsWith(normalizedParentFieldName)) {
          parsedFieldName = variable;
          break;
        }
      }
      if (parsedFieldName.isEmpty()) {
        LOGGER.error("Unable to find matching variable for parent field: {}", parentField.name());
        throw new IllegalArgumentException(
            MessageFormat.format("Unable to find matching variable for parent field: {0}", parentField.name()));
      }
      query.append("FILTER STRENDS(STR(")
          .append(parsedFieldName)
          .append("), \"")
          .append(parentField.id())
          .append("\")");
    } else if (!filterId.isEmpty()) {
      // Add filter clause if there is a valid filter ID
      query.append("FILTER STRENDS(STR(?id), \"")
          .append(filterId)
          .append("\")");
    }
  }
}
