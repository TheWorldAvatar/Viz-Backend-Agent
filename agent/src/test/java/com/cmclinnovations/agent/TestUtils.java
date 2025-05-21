package com.cmclinnovations.agent;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;
import java.util.Queue;

import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.ResourceUtils;

import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.template.query.GetQueryTemplateFactoryTest;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

public class TestUtils {
  /**
   * Check if the result response entity contains the expected substring.
   * 
   * @param expectedSubstring The expected substring.
   * @return
   */
  public static ResultMatcher contentContains(final String expectedSubstring) {
    return result -> {
      String content = result.getResponse().getContentAsString();
      if (!content.contains(expectedSubstring)) {
        throw new AssertionError("Response content does not contain the expected substring: " + expectedSubstring);
      }
    };
  }

  /**
   * Retrieve SPARQL query in string format from the input file.
   * 
   * @param filePath Input file.
   */
  public static String getSparqlQuery(String filePath) throws IOException {
    File file = ResourceUtils.getFile("classpath:" + filePath);
    StringBuilder output = new StringBuilder();
    try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
      String line = reader.readLine();
      while (line != null) {
        if (line != null) {
          output.append(line.trim());
        }
        line = reader.readLine();
      }
    }
    return output.toString();
  }

  /**
   * Retrieve the JSON object from the input file.
   * 
   * @param filePath Input file.
   */
  public static ObjectNode getJson(String filePath) throws IOException {
    File file = ResourceUtils.getFile("classpath:" + filePath);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(file, ObjectNode.class);
  }

  /**
   * Retrieve the JSON object from the input file.
   * 
   * @param filePath Input file.
   */
  public static Map<String, Object> getMapJson(String filePath) throws IOException {
    File file = ResourceUtils.getFile("classpath:" + filePath);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(file, Map.class);
  }

  /**
   * Retrieve the JSON object from the input file.
   * 
   * @param filePath Input file.
   */
  public static ArrayNode getArrayJson(String filePath) throws IOException {
    File file = ResourceUtils.getFile("classpath:" + filePath);
    ObjectMapper mapper = new ObjectMapper();
    return mapper.readValue(file, ArrayNode.class);
  }

  /**
   * Generates an empty Object node.
   */
  public static ObjectNode genEmptyObjectNode() {
    ObjectMapper mapper = new ObjectMapper();
    return mapper.createObjectNode();
  }

  /**
   * Generates a mock version of one SPARQL binding.
   * 
   * @param resultBindings   Stores the binding generated
   * @param clazz            The target class of the query
   * @param varName          The variable name
   * @param multiPathPred    The multi path for the predicate
   * @param multiSubPathPred The multi sub path for the predicate
   * @param nodeGroup        The parent group of the field
   * @param order            The order of the field
   * @param isOptional       Indicates if the field is optional
   */
  public static void genMockSPARQLBinding(Queue<SparqlBinding> resultBindings, String clazz, String varName,
      String multiPathPred, String multiSubPathPred, String nodeGroup, String order, boolean isOptional) {
    SparqlBinding binding = mock(SparqlBinding.class);
    when(binding.getFieldValue(StringResource.CLAZZ_VAR)).thenReturn(clazz);
    when(binding.getFieldValue(GetQueryTemplateFactoryTest.NAME_VAR)).thenReturn(varName);
    when(binding.getFieldValue(ShaclResource.SUBJECT_VAR, "")).thenReturn("");
    when(binding.getFieldValue(ShaclResource.INSTANCE_CLASS_VAR, "")).thenReturn("");
    when(binding.getFieldValue(ShaclResource.NESTED_CLASS_VAR, "")).thenReturn("");

    // Only create mock interactions if there are values
    if (!multiPathPred.isEmpty()) {
      when(binding.containsField(GetQueryTemplateFactoryTest.MULTIPATH_VAR)).thenReturn(true);
      when(binding.getFieldValue(GetQueryTemplateFactoryTest.MULTIPATH_VAR)).thenReturn(multiPathPred);
    }

    if (!multiSubPathPred.isEmpty()) {
      when(binding.containsField(GetQueryTemplateFactoryTest.MULTISUBPATH_VAR)).thenReturn(true);
      when(binding.getFieldValue(GetQueryTemplateFactoryTest.MULTISUBPATH_VAR)).thenReturn(multiSubPathPred);
    }

    if (!nodeGroup.isEmpty()) {
      when(binding.containsField(ShaclResource.NODE_GROUP_VAR)).thenReturn(true);
      when(binding.getFieldValue(ShaclResource.NODE_GROUP_VAR)).thenReturn(nodeGroup);
    }

    if (!order.isEmpty()) {
      when(binding.containsField("order")).thenReturn(true);
      when(binding.getFieldValue("order")).thenReturn(order);
    }
    when(binding.getFieldValue(GetQueryTemplateFactoryTest.IS_OPTIONAL_VAR)).thenReturn(String.valueOf(isOptional));
    resultBindings.offer(binding);
  }
}