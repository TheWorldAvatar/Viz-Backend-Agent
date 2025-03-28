package com.cmclinnovations.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.ResourceUtils;

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
}