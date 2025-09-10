package com.cmclinnovations.agent.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Holds the binding for each row in the SPARQL response. Each row has the
 * following format:
 * 
 * {
 * "type": SparqlResponseField,
 * "label": SparqlResponseField,
 * "description": SparqlResponseField,
 * "xml:lang": SparqlResponseField
 * }
 * 
 * In which SparqlResponseField is another JSON object
 */
public class SparqlBinding {
  private Map<String, SparqlResponseField> bindings;
  private Map<String, List<SparqlResponseField>> bindingList;
  private List<String> sequence;

  /**
   * Constructs a new model.
   */
  public SparqlBinding(ObjectNode sparqlRow) {
    this.bindings = new HashMap<>();
    this.bindingList = new HashMap<>();
    this.sequence = new ArrayList<>();
    Iterator<Map.Entry<String, JsonNode>> iterator = sparqlRow.fields();
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> sparqlCol = iterator.next();
      JsonNode sparqlField = sparqlCol.getValue();
      String type = StringResource.getNodeString(sparqlField, "type");
      // Defaults to null if it is a URI, else it should be string
      String dataTypeDefaultOption = type.equals("uri") ? null : ShaclResource.XSD_STRING;
      this.bindings.put(sparqlCol.getKey(), new SparqlResponseField(
          type,
          StringResource.getNodeString(sparqlField, "value"),
          StringResource.optNodeString(sparqlField, "datatype", dataTypeDefaultOption),
          StringResource.optNodeString(sparqlField, "xml:lang", null)));
    }
  }

  /**
   * Retrieve the Bindings as a map object.
   * 
   * @return a map containing either SparqlResponseField or
   *         List<SparqlResponseField> as its values.
   */
  public Map<String, Object> get() {
    Map<String, Object> resultBindings = new HashMap<>();
    // When there are array results,
    if (!this.bindingList.isEmpty()) {
      // Append array results to the result mappings
      this.bindingList.keySet().forEach(arrayField -> {
        resultBindings.put(arrayField,
            this.bindingList.get(arrayField));
        // Remove the existing mapping, so that it is not overwritten
        this.bindings.remove(arrayField);
      });
    }
    // Place the remaining bindings that exclude any array fields
    resultBindings.putAll(this.bindings);
    // Return unsorted bindings if not required
    if (this.sequence.isEmpty()) {
      return resultBindings;
    }
    // Else, sort the map if there is a sequence
    Map<String, Object> sortedBindings = new LinkedHashMap<>();
    this.sequence.forEach(variable -> {
      String field = QueryResource.genVariable(variable).getVarName();
      if (resultBindings.get(field) != null) {
        sortedBindings.put(field, resultBindings.get(field));
      }
    });
    return sortedBindings;
  }

  /**
   * Retrieve the field names.
   */
  public Set<String> getFields() {
    return this.bindings.keySet();
  }

  /**
   * Adds the sequence for the fields.
   * 
   * @param sequence List of order that fields should be in.
   */
  public void addSequence(List<String> sequence) {
    this.sequence = sequence;
  }

  /**
   * Add fields as an array only if there are distinct values.
   * 
   * @param secBinding The secondary binding for checking.
   * @param arrayVars  Mappings between each array group and their individual
   *                   fields.
   */
  public void addFieldArray(SparqlBinding secBinding, Map<String, Set<String>> arrayVars) {
    if (this.bindingList.isEmpty()) {
      Set<String> bestMatchFields = ShaclResource.findBestMatchingGroup(this.getFields(), arrayVars);
      bestMatchFields.forEach((field) -> {
        List<SparqlResponseField> initFields = new ArrayList<>();
        initFields.add(this.getFieldResponse(field));
        this.bindingList.put(QueryResource.genVariable(field).getVarName(), initFields);
      });
    }
    Set<String> bestMatchFields = ShaclResource.findBestMatchingGroup(secBinding.getFields(), arrayVars);
    bestMatchFields.forEach((field) -> {
      List<SparqlResponseField> fields = this.bindingList.computeIfAbsent(QueryResource.genVariable(field).getVarName(),
          k -> new ArrayList<>());
      fields.add(secBinding.getFieldResponse(field));
    });
  }

  /**
   * Retrieve the SPARQL field response.
   * 
   * @param field Field of interest
   */
  public SparqlResponseField getFieldResponse(String field) {
    return this.bindings.get(QueryResource.genVariable(field).getVarName());
  }

  /**
   * Retrieve the field value. Defaults to null if no value is found!
   * 
   * @param field Field of interest
   */
  public String getFieldValue(String field) {
    return this.getFieldValue(field, null);
  }

  /**
   * Retrieve the field value.
   * 
   * @param field        Field of interest
   * @param defaultValue Fall back value.
   */
  public String getFieldValue(String field, String defaultValue) {
    SparqlResponseField fieldBinding = this.bindings.get(QueryResource.genVariable(field).getVarName());
    if (fieldBinding == null) {
      return defaultValue;
    }
    return fieldBinding.value();
  }

  /**
   * Verify if the bindings have the specified field
   * 
   * @param field Field of interest
   */
  public boolean containsField(String field) {
    return this.bindings.containsKey(field);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o)
      return true;
    if (o == null || getClass() != o.getClass())
      return false;
    SparqlBinding that = (SparqlBinding) o;
    return Objects.equals(this.get(), that.get());
  }

  @Override
  public int hashCode() {
    return Objects.hash(this.get());
  }
}