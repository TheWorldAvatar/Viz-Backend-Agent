package com.cmclinnovations.agent.model.type;

import org.apache.jena.rdf.model.Resource;
import org.apache.jena.rdf.model.ResourceFactory;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

public enum ShaclRuleType {
  SPARQL_RULE("http://www.w3.org/ns/shacl#SPARQLRule"),
  TRIPLE_RULE("http://www.w3.org/ns/shacl#TripleRule");

  private final String iri;
  private final Resource resource;

  ShaclRuleType(String id) {
    this.iri = Rdf.iri(id).getQueryString();
    this.resource = ResourceFactory.createResource(id);
  }

  public Resource getResource() {
    return this.resource;
  }

  public String getIri() {
    return this.iri;
  }
}