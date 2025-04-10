package com.cmclinnovations.agent.model.type;

/**
 * Enums for calculation types
 */
public enum CalculationType {
  DIFFERENCE("https://spec.edmcouncil.org/fibo/ontology/FND/Utilities/Analytics/Difference"),
  TOTAL("https://www.omg.org/spec/Commons/QuantitiesAndUnits/Total");

  private final String expressionClazz;

  CalculationType(String expressionClazz) {
    this.expressionClazz = expressionClazz;
  }

  public String getExpressionClass() {
    return this.expressionClazz;
  }
}