package com.cmclinnovations.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
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

  private static final String API_VERSION = "1.17.0-pagination-SNAPSHOT";
  private static final String STATUS_MESSAGE_EN = "Agent is ready to receive requests.";
  private static final String STATUS_MESSAGE_DE = "Agent ist bereit, Anfragen zu empfangen.";
  private static final String INVALID_GEOCODING_MESSAGE_EN = "Invalid geocoding parameters! Detected a block number but no street is provided!";
  private static final String INVALID_GEOCODING_MESSAGE_DE = "Ungültige Geokodierungsparameter! Blocknummer erkannt, aber keine Straße angegeben!";
  private static final String INVALID_ROUTE_MESSAGE_EN = "Route is invalid at /invalid! Please contact your technical team for assistance!";

  private static Stream<Arguments> provideParametersForStatusRoute() {
    return Stream.of(
        Arguments.of("en-US", STATUS_MESSAGE_EN),
        Arguments.of("en-GB", STATUS_MESSAGE_EN),
        Arguments.of("es-ES", STATUS_MESSAGE_EN),
        Arguments.of("de-DE", STATUS_MESSAGE_DE));
  }

  @ParameterizedTest
  @MethodSource("provideParametersForStatusRoute")
  void testStatusRoute(String acceptLanguageHeader, String expectedStatusMessage) throws Exception {
    this.mockMvc.perform(get("/status")
        .header("Accept-Language", acceptLanguageHeader))
        .andExpect(status().isOk())
        .andExpect(content()
            .json(genStandardApiResponse("\"message\": \"" + expectedStatusMessage + "\"")));
  }

  private static Stream<Arguments> provideParametersForGeocodingRoute() {
    return Stream.of(
        Arguments.of("en-US", INVALID_GEOCODING_MESSAGE_EN),
        Arguments.of("en-GB", INVALID_GEOCODING_MESSAGE_EN),
        Arguments.of("es-ES", INVALID_GEOCODING_MESSAGE_EN),
        Arguments.of("de-DE", INVALID_GEOCODING_MESSAGE_DE));
  }

  @ParameterizedTest
  @MethodSource("provideParametersForGeocodingRoute")
  void testGeocodingRoute(String acceptLanguageHeader, String expectedStatusMessage) throws Exception {
    this.mockMvc.perform(get("/location/geocode?block=5")
        .header("Accept-Language", acceptLanguageHeader))
        .andExpect(status().isBadRequest())
        .andExpect(content()
            .json(genStandardErrorApiResponse(expectedStatusMessage, 400)));
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
        .andExpect(status().isNotFound())
        .andExpect(content().json(genStandardErrorApiResponse(
            "Resource at file:/usr/local/tomcat/resources/application-form.json is not found. Please ensure you have a valid resource in the file path.",
            404)));
  }

  private static Stream<Arguments> provideParametersForInvalidRoutes() {
    return Stream.of(
        Arguments.of("en-US", "/invalid", INVALID_ROUTE_MESSAGE_EN), // getAllInstances
        Arguments.of("en-GB", "/invalid/label", INVALID_ROUTE_MESSAGE_EN), // getAllInstancesWithLabel
        Arguments.of("en-GB", "/parent/id/invalid", INVALID_ROUTE_MESSAGE_EN), // getAllInstancesWithParent
        Arguments.of("en-GB", "/invalid/id", INVALID_ROUTE_MESSAGE_EN), // getInstance
        Arguments.of("en-GB", "/invalid/label/id", INVALID_ROUTE_MESSAGE_EN), // getInstanceWithLabels
        Arguments.of("en-GB", "/invalid/search", INVALID_ROUTE_MESSAGE_EN), // getMatchingInstances
        Arguments.of("en-GB", "/form/invalid", INVALID_ROUTE_MESSAGE_EN), // getFormTemplate
        Arguments.of("es-ES", "/form/invalid", INVALID_ROUTE_MESSAGE_EN));
  }

  @ParameterizedTest
  @MethodSource("provideParametersForInvalidRoutes")
  void testRoutes_InvalidRoute(String acceptLanguageHeader, String route, String expectedErrorMessage)
      throws Exception {
    File sampleFile = FileServiceTest.genSampleFile("/" + FileService.APPLICATION_FORM_RESOURCE, "{}");
    try {
      this.mockMvc.perform(get(route).header("Accept-Language", acceptLanguageHeader))
          .andExpect(status().isNotFound())
          .andExpect(content().json(genStandardErrorApiResponse(expectedErrorMessage, 404)));
    } finally {
      sampleFile.delete();
    }
  }

  private static String genStandardApiResponse(String expectedResponseData) {
    return "{\"apiVersion\":\"" + API_VERSION + "\",\"data\":{" + expectedResponseData + "}}";
  }

  private static String genStandardErrorApiResponse(String expectedErrorMessage, int expectedCode) {
    return "{\"apiVersion\":\"" + API_VERSION + "\",\"error\":{" +
        "\"code\":" + expectedCode + ",\"message\":\"" + expectedErrorMessage + "\"}}";
  }
}