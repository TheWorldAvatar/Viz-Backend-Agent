package com.cmclinnovations.agent.model.type;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;

public enum TrackActionType {
  CREATION("https://theworldavatar.io/kg/ontochangelog/CreationActivity", "label.activity.creation"),
  MODIFICATION("https://theworldavatar.io/kg/ontochangelog/ModificationActivity", "label.activity.modify"),
  CONTRACT_RESET_STATUS("https://theworldavatar.io/kg/ontochangelog/ContractResetActivity",
      "label.activity.reset.contract"),
  APPROVED("https://theworldavatar.io/kg/ontochangelog/ApprovalActivity", "label.activity.approve"),
  ASSIGNMENT("https://theworldavatar.io/kg/ontochangelog/AssignmentActivity", "label.activity.assign"),
  CANCELLATION("https://theworldavatar.io/kg/ontochangelog/CancellationActivity", "label.activity.cancel"),
  COMPLETION("https://theworldavatar.io/kg/ontochangelog/CompletionActivity", "label.activity.complete"),
  SAVED_COMPLETION("https://theworldavatar.io/kg/ontochangelog/SavingActivity", "label.activity.saved"),
  ISSUE_REPORT("https://theworldavatar.io/kg/ontochangelog/IssueReportedActivity", "label.activity.report"),
  RESCINDMENT("https://theworldavatar.io/kg/ontochangelog/RescindmentActivity", "label.activity.rescind"),
  RESCHEDULED("https://theworldavatar.io/kg/ontochangelog/RescheduleActivity", "label.activity.reschedule"),
  ACCRUAL("https://theworldavatar.io/kg/ontochangelog/AccrualActivity", "label.activity.accrue"),
  EXEMPT("https://theworldavatar.io/kg/ontochangelog/ExemptionActivity", "label.activity.exempt"),
  IGNORED("", "");

  private final String clazz;
  private final String translationKey;

  TrackActionType(String clazz, String translationKey) {
    this.clazz = clazz;
    this.translationKey = translationKey;
  }

  public String getClazz() {
    return this.clazz;
  }

  public String getTranslationKey() {
    return this.translationKey;
  }

  /**
   * Gets the corresponding translated message for a given URI.
   * 
   * @param uri The activity URI class
   */
  public static SparqlResponseField getMessage(String uri) {
    for (TrackActionType type : TrackActionType.values()) {
      if (type.clazz.equals(uri)) {
        String translation = LocalisationTranslator.getMessage(type.getTranslationKey());
        return new SparqlResponseField(QueryResource.LITERAL_TYPE, translation, ShaclResource.XSD_STRING, "");
      }
    }
    throw new IllegalArgumentException("Invalid URI: " + uri);
  }
}