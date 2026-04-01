package com.cmclinnovations.agent.model;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;
import com.fasterxml.jackson.annotation.JsonIgnore;
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
  private Map<String, List<Map<String, SparqlResponseField>>> arrayBindingFields;

  /**
   * This constructor is added solely for the purpose of deserialisation and
   * should not be executed within the code base. JsonIgnore is also used in
   * several GET methods to support Jackson deserialisation without setters.
   */
  public SparqlBinding() {
    this.bindings = new HashMap<>();
    this.arrayBindingFields = new HashMap<>();
  }

  /**
   * Constructs a new model.
   */
  public SparqlBinding(ObjectNode sparqlRow, List<String> variables) {
    this();
    Iterator<Map.Entry<String, JsonNode>> iterator = sparqlRow.fields();
    Set<String> missingVariables = new HashSet<>();
    missingVariables.addAll(variables);
    while (iterator.hasNext()) {
      Map.Entry<String, JsonNode> sparqlCol = iterator.next();
      JsonNode sparqlField = sparqlCol.getValue();
      String type = StringResource.getNodeString(sparqlField, "type");
      // Defaults to null if it is a URI, else it should be string
      String dataTypeDefaultOption = type.equals(QueryResource.URI_TYPE) ? null : ShaclResource.XSD_STRING;
      this.bindings.put(sparqlCol.getKey(), new SparqlResponseField(
          type,
          StringResource.getNodeString(sparqlField, "value"),
          StringResource.optNodeString(sparqlField, "datatype", dataTypeDefaultOption),
          StringResource.optNodeString(sparqlField, "xml:lang", null)));
      // Removes the variable from the missing list if available
      missingVariables.remove(sparqlCol.getKey());
    }
    missingVariables.forEach(var -> {
      this.bindings.put(var, null);
    });
  }

  /**
   * Retrieve the Bindings as a map object.
   * 
   * @return a map containing either SparqlResponseField or
   *         List<SparqlResponseField> as its values.
   */
  public Map<String, Object> get() {
    Map<String, Object> resultBindings = new HashMap<>();
    resultBindings.putAll(this.bindings);
    resultBindings.putAll(this.arrayBindingFields);
    return resultBindings;
  }

  /**
   * Retrieve all the field names.
   */
  @JsonIgnore
  public Set<String> getFields() {
    return this.bindings.entrySet().stream()
        .filter(entry -> entry.getValue() != null)
        .map(Map.Entry::getKey)
        .collect(Collectors.toSet());
  }

  public List<Map<String, SparqlResponseField>> getList(String field) {
    return this.arrayBindingFields.get(QueryResource.genVariable(field).getVarName());
  }

  /**
   * Inits any field arrays.
   * 
   * @param arrayVars Mappings between each array group and their individual
   *                  fields.
   */
  public void initArray(Map<String, Set<String>> arrayVars) {
    if (this.arrayBindingFields.isEmpty() && !arrayVars.isEmpty()) {
      String bestMatchGroup = ShaclResource.findBestMatchingGroup(this.getFields(), arrayVars);
      List<Map<String, SparqlResponseField>> currentArrayGroup = new ArrayList<>();
      Map<String, SparqlResponseField> currentArrayFields = new HashMap<>();

      if (bestMatchGroup != null) {
        arrayVars.get(bestMatchGroup).forEach((field) -> {
          SparqlResponseField fieldResponse = this.getFieldResponse(field);
          if (fieldResponse != null) {
            currentArrayFields.put(field, fieldResponse);
          }
          this.bindings.remove(field); // Remove field from bindings as they should now be an array
        });
        currentArrayGroup.add(currentArrayFields);
        this.arrayBindingFields.put(bestMatchGroup, currentArrayGroup);
      }
    }
  }

  /**
   * Add fields as an array only if there are distinct values.
   * 
   * @param secBinding The secondary binding for checking.
   * @param arrayVars  Mappings between each array group and their individual
   *                   fields.
   */
  public void addFieldArray(SparqlBinding secBinding, Map<String, Set<String>> arrayVars) {
    String bestMatchGroup = ShaclResource.findBestMatchingGroup(secBinding.getFields(), arrayVars);
    Map<String, SparqlResponseField> targetMergedArrayFields = new HashMap<>();
    arrayVars.get(bestMatchGroup).forEach((field) -> {
      SparqlResponseField fieldResponse = secBinding.getFieldResponse(field);
      if (fieldResponse != null) {
        targetMergedArrayFields.put(field, fieldResponse);
      }
      this.bindings.remove(field); // Remove field from bindings as they should now be an array
    });
    this.arrayBindingFields.computeIfAbsent(bestMatchGroup, k -> new ArrayList<>())
        .add(targetMergedArrayFields);
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
   * Verify if the bindings have the specified field. Also checks array variables
   * 
   * @param field Field of interest
   */
  public boolean containsField(String field) {
    return (this.bindings.containsKey(field) && this.bindings.get(field) != null)
        || (this.arrayBindingFields.containsKey(field) && this.arrayBindingFields.get(field) != null
            && !this.arrayBindingFields.get(field).isEmpty());
  }

  /**
   * Merges with the other sparqlbinding. WARNING: Overrides existing fields.
   * 
   * @param other Target binding to merge.
   */
  public void merge(SparqlBinding other) {
    Map<String, Object> otherValues = other.get();
    otherValues.forEach((field, value) -> {
      if (value instanceof List<?>) {
        this.arrayBindingFields.computeIfAbsent(field, k -> new ArrayList<>())
            .addAll((List<Map<String, SparqlResponseField>>) value);
      } else {
        this.bindings.put(field, TypeCastUtils.castToObject(value, SparqlResponseField.class));
      }
    });
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