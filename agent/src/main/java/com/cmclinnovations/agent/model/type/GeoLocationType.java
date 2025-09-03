package com.cmclinnovations.agent.model.type;

import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.PropertyPath;
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder;

import com.cmclinnovations.agent.utils.QueryResource;

public enum GeoLocationType {
  POSTAL_CODE(PropertyPathBuilder.of(QueryResource.FIBO_FND_PLC_ADR.iri("hasPostalCode")).build()),
  BLOCK(PropertyPathBuilder.of(QueryResource.FIBO_FND_PLC_ADR.iri("hasStreetAddress"))
      .then(QueryResource.FIBO_FND_PLC_ADR.iri("hasPrimaryAddressNumber"))
      .then(QueryResource.FIBO_FND_REL_REL.iri("hasTag")).build()),
  STREET(PropertyPathBuilder.of(QueryResource.FIBO_FND_PLC_ADR.iri("hasStreetAddress"))
      .then(QueryResource.FIBO_FND_PLC_ADR.iri("hasStreetName"))
      .then(QueryResource.FIBO_FND_REL_REL.iri("hasTag")).build()),
  CITY(PropertyPathBuilder.of(QueryResource.FIBO_FND_PLC_LOC.iri("hasCityName")).build()),
  COUNTRY(PropertyPathBuilder.of(QueryResource.FIBO_FND_PLC_LOC.iri("hasCountry")).build());

  private final PropertyPath predicate;

  GeoLocationType(PropertyPath predicate) {
    this.predicate = predicate;
  }

  public PropertyPath getPred() {
    return this.predicate;
  }
}