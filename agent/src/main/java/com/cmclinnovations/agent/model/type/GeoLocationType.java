package com.cmclinnovations.agent.model.type;

public enum GeoLocationType {
  POSTAL_CODE("fibo-fnd-plc-adr:hasPostalCode"),
  BLOCK("fibo-fnd-plc-adr:hasStreetAddress/fibo-fnd-plc-adr:hasPrimaryAddressNumber/fibo-fnd-rel-rel:hasTag"),
  STREET("fibo-fnd-plc-adr:hasStreetAddress/fibo-fnd-plc-adr:hasStreetName/fibo-fnd-rel-rel:hasTag"),
  CITY("fibo-fnd-plc-loc:hasCityName"),
  COUNTRY("fibo-fnd-plc-loc:hasCountry");

  private final String predicate;

  GeoLocationType(String predicate) {
    this.predicate = predicate;
  }

  public String getPred() {
    return this.predicate;
  }
}