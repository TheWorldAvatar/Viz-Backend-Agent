package com.cmclinnovations.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.service.core.FileServiceTest;

@SpringBootTest
@AutoConfigureMockMvc
class AgentApplicationTests {
  @Autowired
  private MockMvc mockMvc;

  @Test
  void testStatusRoute() throws Exception {
    this.mockMvc.perform(get("/status"))
        .andExpect(status().isOk())
        .andExpect(content().string("Agent is ready to receive requests."));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/missing", // getAllInstances
      "/missing/label", // getAllInstancesWithLabel
      "/parent/id/missing", // getAllInstancesWithParent
      "/missing/id", // getInstance
      "/missing/label/id", // getInstanceWithLabels
      "/missing/search", // getMatchingInstances
      "/csv/missing", // getAllInstancesInCSV
      "/form/missing" }) // getFormTemplate
  void testRoutes_MissingFormResource(String route) throws Exception {
    this.mockMvc.perform(get(route))
        .andExpect(status().isBadRequest())
        .andExpect(TestUtils.contentContains(
            "Resource at file:/usr/local/tomcat/resources/application-form.json is not found. Please ensure you have a valid resource in the file path."));
  }

  @ParameterizedTest
  @ValueSource(strings = {
      "/invalid", // getAllInstances
      "/invalid/label", // getAllInstancesWithLabel
      "/parent/id/invalid", // getAllInstancesWithParent
      "/invalid/id", // getInstance
      "/invalid/label/id", // getInstanceWithLabels
      "/invalid/search", // getMatchingInstances
      "/csv/invalid", // getAllInstancesInCSV
      "/form/invalid" }) // getFormTemplate
  void testRoutes_InvalidRoute(String route) throws Exception {
    File sampleFile = FileServiceTest.genSampleFile("/" + FileService.APPLICATION_FORM_RESOURCE, "{}");
    try {
      this.mockMvc.perform(get(route))
          .andExpect(status().isBadRequest())
          .andExpect(content().string(
              "Route is invalid at /invalid! Please contact your technical team for assistance!"));
    } finally {
      sampleFile.delete();
    }
  }
}