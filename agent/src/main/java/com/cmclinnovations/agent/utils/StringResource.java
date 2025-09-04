package com.cmclinnovations.agent.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.fasterxml.jackson.databind.JsonNode;

public class StringResource {
  public static final String ID_KEY = "id";

  public static final String CLAZZ_VAR = "clazz";
  public static final String RDF_TYPE = "rdf:type";
  public static final String REPLACEMENT_PLACEHOLDER = "[replace]";

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
    return fieldNode.asText();
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
   * Wraps the content into an OPTIONAL clause
   * 
   * @param content Content of the optional clause
   */
  public static String genOptionalClause(String content) {
    return "OPTIONAL{" + content + "}";
  }

  /**
   * Wraps the content into {}
   * 
   * @param content Target content
   */
  public static String genGroupGraphPattern(String content) {
    return "{" + content + "}";
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
    } catch (IllegalArgumentException e) {
      return iri;
    }
  }

  /**
   * Parses a SPARQL query variable to ensure that any spaces are replaced.
   * 
   * @param variable Target variable input.
   */
  public static String parseQueryVariable(String variable) {
    return variable.replaceAll("\\s+", "_");
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
}