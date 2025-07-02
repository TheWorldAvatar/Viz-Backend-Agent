package com.cmclinnovations.agent.service.application;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.service.core.JsonLdService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

@Service
public class LifecycleReportService {
  private final JsonLdService jsonLdService;
  private final ObjectMapper objectMapper;

  private static final String LIFECYCLE_REPORT_PREFIX = "https://www.theworldavatar.io/kg/lifecycle/report/";

  /**
   * Constructs a new service to interact with JSON-LD objects with the following
   * dependencies.
   * 
   * @param jsonLdService A service to handle all JSON-LD related transformation.
   * @param objectMapper  The JSON object mapper.
   */
  public LifecycleReportService(JsonLdService jsonLdService, ObjectMapper objectMapper) {
    this.jsonLdService = jsonLdService;
    this.objectMapper = objectMapper;
  }

  /**
   * Generates a report instance.
   * 
   * @param contract The subject contract instance of interest to report on.
   */
  public ObjectNode genReportInstance(String contract) {
    ObjectNode report = this.jsonLdService.genInstance(LIFECYCLE_REPORT_PREFIX, LifecycleResource.LIFECYCLE_REPORT);
    ObjectNode contractNode = this.objectMapper.createObjectNode().put(ShaclResource.ID_KEY, contract);
    report.set(LifecycleResource.IS_ABOUT_RELATIONS, contractNode);
    return report;
  }
}
