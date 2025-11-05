package com.cmclinnovations.agent.model.type;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.utils.LifecycleResource;

public enum LifecycleEventType {
  APPROVED("approve", LifecycleResource.CREATION_STAGE, LifecycleResource.EVENT_APPROVAL, true),
  ACTIVE_SERVICE("complete", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DELIVERY, true),
  SERVICE_ORDER_RECEIVED("order", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_ORDER_RECEIVED,
      false),
  SERVICE_ORDER_DISPATCHED("dispatch", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DISPATCH,
      false),
  SERVICE_EXECUTION("complete", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_DELIVERY, false),
  SERVICE_CANCELLATION("cancel", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_CANCELLATION,
      false),
  SERVICE_INCIDENT_REPORT("report", LifecycleResource.SERVICE_EXECUTION_STAGE, LifecycleResource.EVENT_INCIDENT_REPORT,
      false),
  ARCHIVE_COMPLETION("completed", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_COMPLETION,
      true),
  ARCHIVE_RESCINDMENT("rescinded", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_RESCISSION,
      false),
  ARCHIVE_TERMINATION("terminated", LifecycleResource.EXPIRATION_STAGE, LifecycleResource.EVENT_CONTRACT_TERMINATION,
      false);

  private final String id;
  private final String stage;
  private final String event;
  private final boolean isContract;

  LifecycleEventType(String id, String stage, String event, boolean isContract) {
    this.id = id;
    this.stage = stage;
    this.event = event;
    this.isContract = isContract;
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

  public boolean getIsContract() {
    return this.isContract;
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