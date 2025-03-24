package com.cmclinnovations.agent;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.util.ResourceUtils;

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
}