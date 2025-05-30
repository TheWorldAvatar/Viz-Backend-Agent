# Examples

This directory provides examples for different applications of the agent.

# Table of Contents

- [1. SHACL Derivation](#1-shacl-derivation)

## 1. SHACL Derivation

Users are able to derive additional triples using `SHACL` rules. This requires the following setup:

1. A `JSON-LD` template describing the data model input for instantiation
2. A `SHACL` rule associated with the class of the primary data model
3. Adding a key value pair in the `application-service.json` file for (1)
4. Adding a key value pair in the `application-form.json` file for (2)

An example is provided below:

1. `JSON-LD` template

```json
{
  "@id": {
    "@replace": "id",
    "@type": "iri",
    "prefix": "https://example.org/kg/calculation/"
  },
  "@type": "http://example.org/Calculation",
  "http://example.org/hasArg": {
    "@type": "http://example.org/Weight",
    "http://example.org/hasValue": {
      "@replace": "weight",
      "@type": "literal",
      "datatype": "http://www.w3.org/2001/XMLSchema#decimal"
    }
  }
}
```

2. `SHACL` rule in ttl

```
@prefix ex:   <http://example.org/> .
@prefix rdf:  <http://www.w3.org/1999/02/22-rdf-syntax-ns#> .
@prefix rdfs: <http://www.w3.org/2000/01/rdf-schema#> .
@prefix sh:   <http://www.w3.org/ns/shacl#> .
@prefix xsd:  <http://www.w3.org/2001/XMLSchema#> .

ex:CalculationShape a sh:NodeShape ;
  sh:targetClass ex:Calculation ;
  sh:rule [
   a sh:SPARQLRule ;
   sh:order 1;
   sh:construct """
     prefix ex: <http://example.org/>
     prefix rdf: <http://www.w3.org/1999/02/22-rdf-syntax-ns#>
     CONSTRUCT {
       ?this ex:hasOutput ?outputInstance .
       ?outputInstance rdf:type ex:Weight ;
                       ex:hasValue ?outputValue .
     }
     WHERE {
       ?this ex:hasArg ?input .
       ?input rdf:type ex:Weight ;
                       ex:hasValue ?weight .
       BIND (?weight * 2 AS ?outputValue)
       BIND (iri(concat(str(?this), "/output")) AS ?outputInstance)
     }
   """
  ] .
```

3. `application-service.json`

```json
{
  "calculation": "calculation"
}
```

4. `application-form.json`

```json
{
  "calculation": "http://example.org/Calculation"
}
```

A `POST` request should be sent to `<baseURL>/vis-backend-agent/calculation` with the `id` and `weight` parameters in the request body.
