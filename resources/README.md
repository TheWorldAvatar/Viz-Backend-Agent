# Examples

This directory provides examples for different applications of the agent.

# Table of Contents

- [1. SHACL Derivation](#1-shacl-derivation)
- [2. Role-based data access](#2-role-based-data-access)

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

## 2. Role-based data access

Access to specific fields can be controlled through setting the optional `https://theworldavatar.io/kg/form/role` property in the `SHACL` shapes to a list of permissible roles. A simple example has been set up below.

```
base:ConceptShape
  a sh:NodeShape ;
  sh:targetClass ontoexample:Concept ;
  sh:property [
    sh:name "public"
    sh:description "A field that can be view by anyone"
    sh:order 0;
    sh:path ontoexample:publicPath ;
    sh:datatype xsd:string ;
  ];
  sh:property [
    sh:name "operation"
    sh:description "A field that can only be viewed by a user with the operation role"
    sh:order 1;
    sh:path ontoexample:operationPath ;
    sh:datatype xsd:string ;
    twa-form:role "operation" ;
  ];
  sh:property [
    sh:name "task-viewer"
    sh:description "A field that can only be viewed by a user with the task-viewer role"
    sh:order 2;
    sh:path ontoexample:taskViewerPath ;
    sh:datatype xsd:string ;
    twa-form:role "task-viewer" ;
  ];
  sh:property [
    sh:name "multi-role"
    sh:description "A field that can only be viewed by a user with either the operation or task-viewer role"
    sh:order 3;
    sh:path ontoexample:multiRolePath ;
    sh:datatype xsd:string ;
    twa-form:role "operation;task-viewer" ;
  ].
```

> [!TIP]
> Do **NOT** use the property IF the field should be accessed by anyone

> [!IMPORTANT]
> The property **MUST** target a string literal with semi-colon delimiters
