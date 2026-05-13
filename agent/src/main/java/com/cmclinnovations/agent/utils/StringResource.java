package com.cmclinnovations.agent.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import tools.jackson.databind.JsonNode;

public class StringResource {
  public static final String FIELD_REQUEST_PARAM = "field";
  public static final String LABEL_REQUEST_PARAM = "label";
  public static final String LIMIT_REQUEST_PARAM = "limit";
  public static final String PAGE_REQUEST_PARAM = "page";
  public static final String SEARCH_REQUEST_PARAM = "search";
  public static final String SORT_BY_REQUEST_PARAM = "sort_by";
  public static final String START_TIMESTAMP_REQUEST_PARAM = "startTimestamp";
  public static final String END_TIMESTAMP_REQUEST_PARAM = "endTimestamp";
  public static final String TYPE_REQUEST_PARAM = "type";
  public static final String DEFAULT_SORT_BY = "-id";
  public static final String CLAZZ_VAR = "clazz";
  public static final String ORIGINAL_PREFIX = "ori_";
  public static final String SORT_KEY = "@sort";

  public static final String INVALID_SHACL_ERROR_MSG = "Invalid knowledge model! SHACL restrictions have not been defined/instantiated in the knowledge graph.";
  public static final Pattern DATE_RANGE_FILTER_PATTERN = Pattern
      .compile("(\\d{4}-\\d{2}-\\d{2})\\.\\.(\\d{4}-\\d{2}-\\d{2})");
  public static final Pattern NUMERIC_FILTER_PATTERN = Pattern
      .compile("(eq|neq|gt|lt|gte|lte)(\\d+(?:\\.\\d+)?)");

  // Private constructor to prevent instantiation
  private StringResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }

  /**
   * Get a string from the specified field name of the input field.
   * 
   * @param field     object containing the string.
   * @param fieldName target field key.
   */
  public static String getNodeString(JsonNode field, String fieldName) {
    return optNodeString(field, fieldName, null);
  }

  /**
   * Get an optional string from the input field.
   * 
   * @param field         object containing the string.
   * @param fieldName     target field key.
   * @param defaultOption default value if there is no such field.
   */
  public static String optNodeString(JsonNode field, String fieldName, String defaultOption) {
    JsonNode fieldNode = field.path(fieldName);
    if (fieldNode.isMissingNode()) {
      if (defaultOption == null) {
        return "";
      }
      return defaultOption;
    }
    return fieldNode.asString();
  }

  /**
   * Appends the triples as a query line in the builder.
   * 
   * @param queryBuilder A query builder for any clause.
   * @param subject      Subject node value for triple.
   * @param predicate    Predicate node value for triple.
   * @param object       Object node value for triple.
   */
  public static void appendTriple(StringBuilder queryBuilder, String subject, String predicate, String object) {
    queryBuilder.append(subject)
        .append(ShaclResource.WHITE_SPACE).append(predicate).append(ShaclResource.WHITE_SPACE)
        .append(object)
        .append(ShaclResource.FULL_STOP);
  }

  /**
   * Replaces the last target character for a string.
   * 
   * @param str         Target string.
   * @param target      The character(s) for replacement.
   * @param replacement The replacement string.
   */
  public static String replaceLast(String str, String target, String replacement) {
    int lastIndex = str.lastIndexOf(target);
    if (lastIndex == -1) {
      return str;
    }

    StringBuilder sb = new StringBuilder(str);
    sb.replace(lastIndex, lastIndex + target.length(), replacement);
    return sb.toString();
  }

  /**
   * Get local name of the IRI for namespaces containing # or /.
   * 
   * @param iri Input.
   */
  public static String getLocalName(String iri) {
    try {
      // Check if IRI is valid
      Rdf.iri(iri);
      int index = iri.indexOf("#");
      if (index != -1) {
        return iri.substring(index + 1);
      }
      String[] parts = iri.split("/");
      return parts[parts.length - 1];
    } catch (IllegalArgumentException _) {
      return iri;
    }
  }

  /**
   * Retrieve the prefix of the input IRI.
   * 
   * @param iri Input.
   */
  public static String getPrefix(String iri) {
    // Executes to check if iri is valid
    Rdf.iri(iri);
    int lastSlashIndex = iri.lastIndexOf("/");
    return iri.substring(0, lastSlashIndex);
  }

  /**
   * Maps the input roles string to a set for easy checks.
   * 
   * @param roles A string of roles delimited by ;.
   */
  public static Set<String> mapRoles(String roles) {
    if (roles == null || roles.isEmpty()) {
      return new HashSet<>();
    }
    return Arrays.stream(roles.split(";")).map(String::trim)
        .collect(Collectors.toSet());
  }

  /**
   * Parses the filters given in request parameters to readable format.
   * 
   * @param filters    Filters provided in the request parameters.
   * @param isContract Indicates if it is a contract or task otherwise.
   */
  public static Map<String, Set<String>> parseFilters(Map<String, String> filters, Boolean isContract) {
    return filters.entrySet()
        .stream()
        .map(entry -> {
          Set<String> valueSet = StringResource.parseFilterValue(entry.getValue());
          return Map.entry(
              LifecycleResource.revertLifecycleSpecialFields(entry.getKey(), isContract),
              valueSet);
        })
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  /**
   * Parses the filter value into suitable values.
   * 
   * @param value Input value for parsing.
   */
  private static Set<String> parseFilterValue(String value) {
    Set<String> valueSet = new LinkedHashSet<>();
    // Parse filters for date
    Matcher dateMatcher = DATE_RANGE_FILTER_PATTERN.matcher(value);
    if (dateMatcher.find()) {
      String startFilterDate = dateMatcher.group(1);
      String endFilterDate = dateMatcher.group(2);
      // Add a date key to handle the date filter differently later
      valueSet.add(LifecycleResource.DATE_KEY);
      valueSet.add(startFilterDate);
      if (!startFilterDate.equals(endFilterDate)) {
        valueSet.add(endFilterDate);
      }
      // Early termination for date filters
      return valueSet;
    }

    // Parse filters for numbers
    Matcher numericMatcher = NUMERIC_FILTER_PATTERN.matcher(value);
    if (numericMatcher.find()) {
      valueSet.add(QueryResource.NUMERIC_TYPE);
      // Execute the extraction first before moving to the next match
      do {
        String operator = numericMatcher.group(1);
        String number = numericMatcher.group(2);
        valueSet.add(operator);
        valueSet.add(number);
      } while (numericMatcher.find());

      return valueSet;
    }

    return Arrays.stream(value.split("\\|"))
        .map(string -> string.equals(QueryResource.NULL_KEY) ? string
            : "\"" + string.trim()
                .replace("\\", "\\\\")
                .replaceAll("\\r?\\n", "\\\\n") + "\"")
        .collect(Collectors.toSet());
  }
}