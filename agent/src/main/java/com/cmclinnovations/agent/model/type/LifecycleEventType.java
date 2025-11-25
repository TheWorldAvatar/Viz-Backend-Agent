package com.cmclinnovations.agent.model.type;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.utils.LifecycleResource;

public enum LifecycleEventType {
  APPROVED("approve", LifecycleResource.CREATION_STAGE, LifecycleResource.EVENT_APPROVAL),
  ACTIVE_SERVICE("complete", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DELIVERY),
  SERVICE_ORDER_RECEIVED("order", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_ORDER_RECEIVED),
  SERVICE_ORDER_DISPATCHED("dispatch", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DISPATCH),
  SERVICE_EXECUTION("complete", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DELIVERY),
  SERVICE_CANCELLATION("cancel", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_CANCELLATION),
  SERVICE_INCIDENT_REPORT("report", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_INCIDENT_REPORT),
  ARCHIVE_COMPLETION("completed", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_COMPLETION),
  ARCHIVE_RESCINDMENT("rescinded", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_RESCISSION),
  ARCHIVE_TERMINATION("terminated", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_TERMINATION);

  private final String id;
  private final String stage;
  private final String event;

  LifecycleEventType(String id, String stage, String event) {
    this.id = id;
    this.stage = stage;
    this.event = event;
  }

  public String getId() {
    return this.id;
  }

  public String getStage() {
    return this.stage;
  }

  public String getEvent() {
    return this.event;
  }

  public String getShaclReplacement() {
    return "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
        + "sh:property/sh:hasValue " + Rdf.iri(this.getEvent()).getQueryString();
  }

  /**
   * Retrieves a LifecycleEventType enum constant based on its ID.
   *
   * @param enumId The string representing the enum's constant name.
   */
  public static LifecycleEventType fromId(String enumId) {
    for (LifecycleEventType event : LifecycleEventType.values()) {
      if (event.getId().equals(enumId)) {
        return event;
      }
    }
    return null;
  }
}