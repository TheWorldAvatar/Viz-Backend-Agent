package com.cmclinnovations.agent.service.core;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.TypeCastUtils;

@Service
public class ConceptLabelService {
  private final KGService kgService;
  private final QueryTemplateService queryTemplateService;

  private static final String CONCEPT_FIELD = "type";
  private static final String LABEL_FIELD = "label";
  private static final Logger LOGGER = LogManager.getLogger(ConceptLabelService.class);

  /**
   * Constructs a new service with the following dependencies.
   *
   * @param kgService            KG service for performing the query.
   * @param queryTemplateService Service for generating query templates.
   */
  public ConceptLabelService(KGService kgService, QueryTemplateService queryTemplateService) {
    this.kgService = kgService;
    this.queryTemplateService = queryTemplateService;
  }

  /**
   * Resolves the default value of every concept field ie a property shape with
   * sh:in, in the form template into the concept's own rdfs:label, shaped as an
   * ordinary literal. This lets a read-only consumer render concept fields
   * through the same path as any literal field. Concepts without a stored label
   * are left untouched as their IRI, rather than emitting an empty value or one
   * derived from the IRI itself.
   *
   * @param formTemplate The target form template, which is updated in place.
   */
  public void resolve(Map<String, Object> formTemplate) {
    Map<String, SparqlResponseField> labels = new HashMap<>();
    Set<String> queriedClasses = new HashSet<>();
    this.getConceptShapes(formTemplate).forEach(conceptShape -> {
      this.getConceptClasses(conceptShape).forEach(conceptClass -> {
        // Each concept class need only be queried once per template
        if (queriedClasses.add(conceptClass)) {
          labels.putAll(this.getConceptLabels(conceptClass));
        }
      });
      this.replaceDefaultValue(conceptShape, labels);
    });
  }

  /**
   * Retrieves all the concept property shapes with a default value, from both the
   * top level properties and those nested within any node shape.
   *
   * @param formTemplate The target form template.
   */
  private List<Map<String, Object>> getConceptShapes(Map<String, Object> formTemplate) {
    Stream<?> nodeProperties = asList(formTemplate.get(ShaclResource.NODE_PROPERTY)).stream()
        .filter(Map.class::isInstance)
        .flatMap(nodeShape -> asList(((Map<String, Object>) nodeShape).get(ShaclResource.PROPERTY_PROPERTY)).stream());
    return Stream.concat(asList(formTemplate.get(ShaclResource.PROPERTY_PROPERTY)).stream(), nodeProperties)
        .filter(Map.class::isInstance)
        .map(propertyShape -> (Map<String, Object>) propertyShape)
        // Only sh:in fields are of interest; sh:class fields must retain their IRI as
        // they are rendered as nested entities
        .filter(propertyShape -> propertyShape.containsKey(ShaclResource.IN_PROPERTY)
            && propertyShape.get(ShaclResource.DEFAULT_VAL_PROPERTY) != null)
        .toList();
  }

  /**
   * Retrieves the concept classes targeted by the property shape. Note that sh:in
   * stores the parent class of the selectable concepts rather than the concepts
   * themselves.
   *
   * @param conceptShape The target concept property shape.
   */
  private Set<String> getConceptClasses(Map<String, Object> conceptShape) {
    return asList(conceptShape.get(ShaclResource.IN_PROPERTY)).stream()
        .filter(Map.class::isInstance)
        .map(inValue -> ((Map<String, Object>) inValue).get(ShaclResource.ID_KEY))
        .filter(Objects::nonNull)
        .map(Object::toString)
        .collect(Collectors.toSet());
  }

  /**
   * Retrieves the mappings between each concept of the target class and its
   * label. This reuses the same query backing the concept metadata route so that
   * the labels agree with the options offered in the corresponding form dropdown.
   *
   * @param conceptClass The target parent class of the concepts.
   */
  private Map<String, SparqlResponseField> getConceptLabels(String conceptClass) {
    Map<String, SparqlResponseField> labels = new HashMap<>();
    this.kgService
        .query(this.queryTemplateService.getConceptQuery(conceptClass), SparqlEndpointType.BLAZEGRAPH)
        .forEach(binding -> {
          String concept = binding.getFieldValue(CONCEPT_FIELD);
          SparqlResponseField label = binding.getFieldResponse(LABEL_FIELD);
          if (concept != null && label != null) {
            labels.put(concept, label);
          }
        });
    return labels;
  }

  /**
   * Replaces the default value of the concept property shape with the label of
   * its concept(s).
   *
   * @param conceptShape The target concept property shape, which is updated in
   *                     place.
   * @param labels       Mappings between each concept and its label.
   */
  private void replaceDefaultValue(Map<String, Object> conceptShape, Map<String, SparqlResponseField> labels) {
    Object defaultValue = conceptShape.get(ShaclResource.DEFAULT_VAL_PROPERTY);
    List<SparqlResponseField> currentValues;
    try {
      // Default values may either be a single field or an array of fields
      currentValues = TypeCastUtils.castToListObject(defaultValue, SparqlResponseField.class);
    } catch (ClassCastException | IllegalArgumentException e) {
      LOGGER.warn("Unable to parse the default value of a concept field! Retaining the existing value...", e);
      return;
    }
    if (currentValues.isEmpty()) {
      return;
    }
    List<SparqlResponseField> resolvedValues = currentValues.stream()
        .map(currentValue -> this.resolveValue(currentValue, labels))
        .toList();
    conceptShape.put(ShaclResource.DEFAULT_VAL_PROPERTY,
        defaultValue instanceof List ? resolvedValues : resolvedValues.get(0));
  }

  /**
   * Resolves the concept IRI into its label. Defaults to the existing value if
   * there is no associated label.
   *
   * @param currentValue The current default value of the concept field.
   * @param labels       Mappings between each concept and its label.
   */
  private SparqlResponseField resolveValue(SparqlResponseField currentValue,
      Map<String, SparqlResponseField> labels) {
    if (!QueryResource.URI_TYPE.equals(currentValue.type())) {
      return currentValue;
    }
    SparqlResponseField label = labels.get(currentValue.value());
    if (label == null) {
      LOGGER.warn("No label is available for the concept {}! Retaining its IRI...", currentValue.value());
      return currentValue;
    }
    return label;
  }

  /**
   * Casts the target value into a list, defaulting to an empty list.
   *
   * @param value The target value.
   */
  private static List<?> asList(Object value) {
    return value instanceof List<?> list ? list : List.of();
  }
}
