package com.cmclinnovations.agent.model.type;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

public enum ShaclRuleType {
  SPARQL_RULE("http://www.w3.org/ns/shacl#SPARQLRule"),
  TRIPLE_RULE("http://www.w3.org/ns/shacl#TripleRule");

  private final String iri;

  ShaclRuleType(String id) {
    this.iri = Rdf.iri(id).getQueryString();
  }

  public String getIri() {
    return this.iri;
  }
}