# Vis Backend Agent

The Vis-Backend Agent is a supporting service to The World Avatar's [viz](https://github.com/TheWorldAvatar/viz) service. It is designed to manage all visualisation-related requests from a single point of access to for example, filter map layers, generate dynamic controls, or query, add, delete, and update instances within the registry. By abstracting the backend implementation details (such as which other agents to call), it provides a unified access point to the data within its specific stack. This design allows the ViP to be deployed on a separate stack while retaining the capability to ingest data from multiple stacks seamlessly.

All notable changes to this agent are documented in the `CHANGELOG.md` file. Please consult it for release notes and upgrade information.

## Table of Contents

- [Vis Backend Agent](#vis-backend-agent)
  - [1. Agent Deployment](#1-agent-deployment)
    - [1.1 Preparation](#11-preparation)
    - [1.2 Docker Deployment](#12-docker-deployment)
  - [2. Agent Route](#2-agent-route)
    - [2.1 Status Route](#21-status-route-baseurlvis-backend-agentstatus)
    - [2.2 Geocoding Route](#22-geocoding-route-baseurlvis-backend-agentlocation)
      - [2.2.1 Geocoding route](#221-geocoding-route)
      - [2.2.2 Address search route](#222-address-search-route)
    - [2.3 Form Route](#23-form-route-baseurlvis-backend-agentformtype)
    - [2.4 Concept Metadata Route](#24-concept-metadata-route-baseurlvis-backend-agenttype)
    - [2.5 Instance Route](#25-instance-route)
      - [2.5.1 Add route](#251-add-route)
      - [2.5.2 Delete route](#252-delete-route)
      - [2.5.3 Update route](#253-update-route)
      - [2.5.4 Get route](#254-get-route)
    - [2.6 Service Lifecycle Route](#26-service-lifecycle-route)
      - [2.6.1 Status route](#261-status-route)
      - [2.6.2 Draft route](#262-draft-route)
      - [2.6.3 Schedule route](#263-schedule-route)
      - [2.6.4 Service commencement route](#264-service-commencement-route)
      - [2.6.5 Service order route](#265-service-order-route)
      - [2.6.6 Archive contract route](#266-archive-contract-route)

## 1. Agent Deployment

The agent is designed for execution through a Docker container within [The World Avatar's stack](https://github.com/cambridge-cares/TheWorldAvatar/tree/main/Deploy/stacks/dynamic/stack-manager). It cannot run as a standalone container, and other deployment workflows are beyond the scope of this document.

### 1.1 Preparation

Before using this agent, follow the steps below to ensure you have everything you need to successfully run the agent.

##### Maven Repository credentials

This agent is set up to use this [Maven repository](https://maven.pkg.github.com/cambridge-cares/TheWorldAvatar/) (in addition to Maven central).
You'll need to provide your credentials in a single-word text files located like this:

```
./credentials/
    repo_username.txt
    repo_password.txt
```

repo_username.txt should contain your Github username. repo_password.txt should contain your Github [personal access token](https://docs.github.com/en/github/authenticating-to-github/creating-a-personal-access-token),
which must have a 'scope' that [allows you to publish and install packages](https://docs.github.com/en/packages/working-with-a-github-packages-registry/working-with-the-apache-maven-registry#authenticating-to-github-packages).

##### SPARQL Endpoints

> [!IMPORTANT]  
> Inference MUST be enabled in all SPARQL endpoints for them to function as expected.

##### Environment variables

The agent requires the following environment variables. These variables must be set in their respective docker configuration files for the agent to function as intended.

- `NAMESPACE`: Specifies the SPARQL namespace identifier containing the corresponding instances (default: kb)
- `TASKS_ENABLED`: Specifies if scheduled tasks must be executed. This is tentatively required only for lifecycle related tasks (default: false)
- `KEYCLOAK_ISSUER_URI`: Optional parameter to enable web security via Keycloak. Format: `http://<DOMAIN>/realms/<REALM>`; To disable, either set an empty string or remove the variable entirely

##### Files

**FORM TEMPLATE**

In generating the form template, users must create and upload [`SHACL` restrictions](./resources/README.md#1-shacl-restrictions) into the `namespace` specified in the previous section. Users must also generate a corresponding identifier and target classes in `./resources/application-form.json`. This file must be copied into the Docker container via bind mounts. The target class must also correspond to the object of the `NodeShape sh:targetClass ?object` triple in order to function.

**REST ENDPOINT**

The agent will require at least two files in order to function as a `REST` endpoint to add, delete, insert, and retrieve instances within the registry in the ViP.

1. `./resources/application-service.json`: A file mapping the resource identifier to the target file name in (2)
2. At least one `JSON-LD` file at `./resources/jsonld/example.jsonld`: This file provides a structure for the instance that must be instantiated and will follow the schemas defined in [this section](./resources/README.md#21-instantiation). Each file should correspond to one type of instance, and the resource ID defined in (1) must correspond to the respective file in order to function. For any lifecycle related functionalities, please also included the following `JSON-LD` files as stated in [this section](./resources/README.md#213-service-lifecycle).

**GEOCODING ENDPOINT**

Users must add a geocoding endpoint to the `geocode` resource identifier at `./resources/application-service.json`. This geocoding endpoint is expected to be a `SPARQL` compliant endpoint with geocoding data instantiated. For more details about the ontologies and restrictions involved, please read [section 4.2](./resources/README.md#22-geocoding).

### 1.2 Docker Deployment

**TEST ENVIRONMENT**

- Deploy the agent to execute the unit tests on a standalone container by running the following code in the CLI at the <root> directory.
- The success of all tests must be verified through the Docker logs.

```
docker compose -f "./docker/docker-compose.test.yml" up -d --build
```

**PRODUCTION ENVIRONMENT**

1. Build this agent's image by issuing `docker compose -f './docker/docker-compose.yml' build` within this directory. Do not start the container.
2. Update the environment variables in `./docker/vis-backend-agent.json` if required.
3. Copy the `./docker/vis-backend-agent.json` file into the `inputs/config/services` directory of the stack manager.
4. Ensure the bind mount path is correctly set in the stack configuration for `vis-resources`.
5. Start the stack manager as usual following [these instructions](https://github.com/cambridge-cares/TheWorldAvatar/tree/main/Deploy/stacks/dynamic/stack-manager).

**DEBUGGING ENVIRONMENT**
Follow the same steps as the **PRODUCTION ENVIRONMENT**, but use the `vis-backend-agent-debug.json` file instead in step 3.

If you are developing in VSCode, please add the following `launch.json` to the `.vscode` directory. Once the agent is running with the debug configuration, the developer can attach the debugger on the debug panel in VSCode.

```json
{
  // Use IntelliSense to learn about possible attributes.
  // Hover to view descriptions of existing attributes.
  // For more information, visit: https://go.microsoft.com/fwlink/?linkid=830387
  "version": "0.2.0",
  "configurations": [
    {
      "type": "java",
      "name": "Debug attach",
      "request": "attach",
      "port": 5007,
      "hostName": "localhost",
      "projectName": "vis-backend-agent"
    }
  ]
}
```

## 2. Agent Route

The agent currently offers the following API route(s). All routes will return the following response following [Google's JSON API style guide](https://google.github.io/styleguide/jsoncstyleguide.xml):

```json
{
  "apiVersion": "1.0.0",
  // Returns a data object on successful request
  "data": {
    "id" : "Optional identifier string",
    "message": "Optional messages",
    "deleted": "A boolean indicator mainly for DELETE requests",
    "items": [{...}, {...}] // An optional array of data objects
  },
  // Returns an error object on any errors with the request
  "error": {
    "code": 400, // HTTP status code
    "message": "Error message"
  }
}
```

### 2.1 Status Route: `<baseURL>/vis-backend-agent/status`

This route serves as a health check to confirm that the agent has been successfully initiated and is operating as anticipated. It can be called through a `GET` request with no parameters, as follows:

```
curl localhost:3838/vis-backend-agent/status
```

If successful, the response will return

```json
{
  "apiVersion": "1.0.0",
  "data": { "message": "Agent is ready to receive requests." }
}
```

### 2.2 Geocoding Route: `<baseURL>/vis-backend-agent/location`

This route serves as a geocoding endpoint to interface with addresses and coordinates.

#### 2.2.1 Geocoding route

To retrieve the geographic coordinates, users can send a `GET` request to `<baseURL>/vis-backend-agent/location/geocode` with at least one of the following parameters:

1. `postal_code`: Postal code of the address
2. `block`: The street block of the address; Must be sent along with the street name
3. `street`: The street name of the address
4. `city`: The city name of the address
5. `country`: The country IRI of the address following [this ontology](https://www.omg.org/spec/LCC/Countries/ISO3166-1-CountryCodes)

If successful, the response will return the coordinates in the `[longitude, latitude]` format that is compliant with `JSON`:

```json
{
  "apiVersion": "1.0.0",
  "data": { "items": [{ "coordinates": "[longitude, latitude]" }] }
}
```

Users may also send a `GET` request to `<baseURL>/vis-backend-agent/location?iri={location}` where `location` is the location IRI, to retrieve the associated geocoordinates.

#### 2.2.2 Address search route

To search for the address based on postal code, users can send a `GET` request to `<baseURL>/vis-backend-agent/location/addresses` with the following parameter:

1. `postal_code`: Postal code of the address

If successful, the response will return the addresses as an array in the following `JSON` format:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "items": [
      {
        "block": "block number",
        "street": "street name",
        "city": "city name",
        "country": "country IRI"
      },
      {
        "street": "street name",
        "city": "city name",
        "country": "country IRI"
      }
    ]
  }
}
```

### 2.3 Form Route: `<baseURL>/vis-backend-agent/form/{type}`

This route serves as an endpoint to retrieve the corresponding form template for the specified target class type. Users can send a `GET` request to `<baseURL>/vis-backend-agent/form/{type}`, where `{type}` is the requested identifier that must correspond to a target class in `./resources/application-form.json`.

Users can also retrieve a form template for a specific instance by appending the associated `id` at the end eg `<baseURL>/vis-backend-agent/form/{type}/{id}`.

If successful, the response will return a form template in the following (minimal) JSON-LD format. Please note that the template does not follow any valid ontology rules at the root level, and is merely a schema for the frontend. However, its nested values complies with `SHACL` ontological rules.

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "items": [{
      "http://www.w3.org/ns/shacl#property": [
        {
          "@id": "PROPERTY IRI",
          "@type": "http://www.w3.org/ns/shacl#PropertyShape",
          "http://www.w3.org/ns/shacl#name": {
            "@value": "form field name"
          },
          "http://www.w3.org/ns/shacl#description": {
            "@value": "description."
          },
          "http://www.w3.org/ns/shacl#group": {
            "@id": "GROUP IRI"
          }
        },
        {
          "@id": "GROUP IRI",
          "@type": "http://www.w3.org/ns/shacl#PropertyGroup",
          "http://www.w3.org/2000/01/rdf-schema#comment": {
            "@value": "Description of group."
          },
          "http://www.w3.org/2000/01/rdf-schema#label": {
            "@value": "property group name"
          },
          "http://www.w3.org/ns/shacl#property": [
            {
              "@id": "PROPERTY IRI",
              "@type": "http://www.w3.org/ns/shacl#PropertyShape",
              "http://www.w3.org/ns/shacl#name": {
                "@value": "form field name"
              },
              "http://www.w3.org/ns/shacl#description": {
                "@value": "description."
              },
              "http://www.w3.org/ns/shacl#group": {
                "@id": "GROUP IRI"
              }
            },
            ...
          ]
        }
      ]
    }]
  }
}
```

### 2.4 Concept Metadata Route: `<baseURL>/vis-backend-agent/type`

This route serves as an endpoint to retrieve all available ontology classes and subclasses along with their human readable labels and descriptions associated with the type. Users can send a `GET` request to `<baseURL>/vis-backend-agent/type` with the `uri` query parameter and value of the required ontology class.

If successful, the response will return an array of objects in the `data.items` keys in the following format:

```json
{
  "type": {
    "type": "uri",
    "value": "instance IRI",
    "dataType": "",
    "lang": ""
  },
  "label": {
    "type": "literal",
    "value": "Label of the class instance",
    "dataType": "http://www.w3.org/2001/XMLSchema#string",
    "lang": "Optional language field"
  },
  "description": {
    "type": "literal",
    "value": "Description for the class instance",
    "dataType": "http://www.w3.org/2001/XMLSchema#string",
    "lang": "Optional language field"
  },
  "parent": {
    "type": "uri",
    "value": "parent class IRI",
    "dataType": "",
    "lang": ""
  }
}
```

### 2.5 Instance Route

This route serves as a `RESTful` endpoint to perform `CRUD` operations for any resources based on the `type` specified.

#### 2.5.1 Add route

To add a new instance, users must send a POST request with their corresponding parameters to

```
<baseURL>/vis-backend-agent/{type}
```

where `{type}` is the requested identifier that must correspond to a target file name in`./resources/application-service.json`. The request parameters will depend on the `JSON-LD` file defined. More information on the required schema can be found in [this section](./resources/README.md#21-instantiation).

[`SHACL rules`](https://www.w3.org/TR/shacl-af/#rules) can be implemented to derive additional triples. For an example, see the [`./resources` directory](./resources/README.md#13-shacl-derivation). Currently, only the [`SparqlRule`](https://www.w3.org/TR/shacl-af/#SPARQLRule) is fully supported, while [`TripleRule`](https://www.w3.org/TR/shacl-af/#TripleRule) functionality is limited, as nested conditions are not supported.".

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "IRI",
    "message": "type has been successfully instantiated!"
  }
}
```

#### 2.5.2 Delete route

To delete an instance, users must send a DELETE request to

```
<baseURL>/vis-backend-agent/{type}/{id}
```

where `{type}` is the requested identifier that must correspond to a target file name in`./resources/application-service.json`, and `{id}` is the specific instance's identifier. The instance representation will be deleted according to the `JSON-LD` file defined for adding a new instance. More information on the required schema can be found in [this section](./resources/README.md#21-instantiation).

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "IRI",
    "message": "Instance has been successfully deleted!",
    "deleted": true
  }
}
```

#### 2.5.3 Update route

To update an instance, users must send a PUT request with their corresponding parameters to

```
<baseURL>/vis-backend-agent/{type}/{id}
```

where `{type}` is the requested identifier that must correspond to a target file name in`./resources/application-service.json`, and `{id}` is the specific instance's identifier. The request parameters will depend on the `JSON-LD` file defined for adding a new instance. More information on the required schema can be found in [this section](./resources/README.md#21-instantiation).

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "IRI",
    "message": "type has been successfully updated for id!"
  }
}
```

#### 2.5.4 Get route

There are several routes for retrieving instances associated with a specific `type` to populate the records in the registry. The agent will automatically generate the query and parameters based on the SHACL restrictions developed. The agent will return **EITHER** a `JSON` array containing entities as their corresponding `JSON` object **OR** one Entity `JSON` object depending on which `GET` route is executed.

1. Get the count of all instances
2. Get all instances
3. Get a specific instance
4. Get all instances with human readable fields
5. Get all instances associated with a specific parent instance
6. Get all instances matching the search criteria

##### Get the count of all instances

Users can send a `GET` request to

```
<baseURL>/vis-backend-agent/{type}/count
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`.

##### Get all instances

Users can send a `GET` request to

```
<baseURL>/vis-backend-agent/{type}
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`.

##### Get a instance

Users can send a `GET` request to

```
<baseURL>/vis-backend-agent/{type}/{id}
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`, and `{id}` is the specific instance's identifier.

To retrieve an instance with human-readable fields, users can send a `GET` request to `<baseURL>/vis-backend-agent/{type}/label/{id}`, where parameters are the same as the default route.

##### Get all instances with human readable fields

This route retrieves all instances with human-readable fields. Users can send a `GET` request to

```
<baseURL>/vis-backend-agent/{type}/label?page={page}&limit={limit}&sort_by={sortby}
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

##### Get all instances associated with a specific parent instance

Users can send a `GET` request to:

```
<baseURL>/vis-backend-agent/{parent}/{id}/{type}
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`, `{parent}` is the requested parent identifier that is linked to the type, and `{id}` is the specific parent instance's identifier to retrieve all instances associated with.

##### Get all instances matching the search criteria

Users can send a `POST` request with search criterias to:

```
<baseURL>/vis-backend-agent/{type}/search
```

where `{type}`is the requested identifier that must correspond to a target class in`./resources/application-form.json`. The search criterias should be sent as a `JSON` request body:

```json
{
  "parameter": "criteria",
  "parameter-two": "criteria-two"
}
```

### 2.6 Service Lifecycle Route

This `<baseURL>/vis-backend-agent/contracts/` route serves as an endpoint to manage the lifecycle of contracts and their associated services. Note that the following scheduled tasks are available and will occur at 6am everyday if `TASKS_ENABLED` is true:

- Discharge of expired active contracts

#### 2.6.1 Status route

This endpoint serves to retrieve the status of a contract using a `GET` request at the following endpoint:

```
<baseURL>/vis-backend-agent/contracts/status/{id}
```

where `{id}`is the requested contract ID. A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "Contract IRI",
    "message": "Pending, Active, or Archived status"
  }
}
```

#### 2.6.2 Draft route

This endpoint serves to draft a new contract, inclusive of its lifecycle and the schedule, or retrieve all draft contracts that are awaiting approval.

> New/Edit draft contract

Users can _EITHER_ send a `POST` request to create a new instance _OR_ send a `PUT` request to update the draft contract at the following endpoint:

```
<baseURL>/vis-backend-agent/contracts/draft
```

Note that this route will interact with the [schedule route](#263-schedule-route) directly, and users should not sent a separate request to the schedule route unless they wish to interact with the schedule. The draft route will require the following `JSON` request parameters:

```json
{
  /* parameters */
  "id": "An identifier for the lifecycle",
  "contract": "The target contract IRI",
  "start date": "Date when the first service is to be delivered in the YYYY-MM-DD format",
  "end date": "Date of the final service in the YYYY-MM-DD format; If optional, use empty string",
  "time slot start": "Beginning of the time window during which the service is scheduled to be delivered in the HH:MM format",
  "time slot end": "End of the time window during which the service is scheduled to be delivered in the HH:MM format",
  "recurrence": "Service interval in the ISO 8601 format eg P1D P7D P2D; If optional, use empty string",
  "monday": "A boolean indicating if the service should occur on a monday",
  "tuesday": "A boolean indicating if the service should occur on a tuesday",
  "wednesday": "A boolean indicating if the service should occur on a wednesday",
  "thursday": "A boolean indicating if the service should occur on a thursday",
  "friday": "A boolean indicating if the service should occur on a friday",
  "saturday": "A boolean indicating if the service should occur on a saturday",
  "sunday": "A boolean indicating if the service should occur on a sunday"
}
```

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "Contract IRI",
    "message": "Contract has been successfully drafted/updated!"
  }
}
```

> Reset draft contract status

When users edit a draft contract, this will move the status to amended. Users can reset this to the original status by sending a `PUT` request to the `<baseURL>/vis-backend-agent/contracts/draft/reset` endpoint. Note that this route does require the following `JSON` request parameters:

```json
{
  "contract": "Either one contract IRI or an array of contract IRIs",
}
```

> Get all draft contracts

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/draft/count?type={type}` endpoint to retrieve the number of draft contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`.

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/draft?type={type}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all draft contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`.

There is also an additional optional parameter `label` to retrieve draft contracts with only human readable values. Users may pass in `yes` if the response should all be labelled and `no` otherwise.

> Copy contract as a draft

Users can send a `POST` request to the `<baseURL>/vis-backend-agent/contracts/draft/copy` endpoint to clone an existing contract as a new draft contract that is pending approval. This route will require the following `JSON` request parameters:

```json
{
  /* parameters */
  "id": "Either a string literal of the existing target contract ID or an array of the target contract IDs",
  "type": "The requested identifier that must correspond to the target contract class in `./resources/application-form.json`",
  "recurrence": "Number of copies required. If an array is given in the id, all selected contracts will be copied according to the number",
}
```

#### 2.6.3 Schedule route

> Get contract schedule

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/schedule/{id}` endpoint to retrieve the schedule details for the specified contract, where `{id}`is the requested contract ID. This route serves to support form generation for the viewing of existing contracts. If successful, the request will return the following `JSON` fields:

1. `start_date`: Service start date in the `YYYY-MM-DD` format
2. `end_date`: Service end date in the `YYYY-MM-DD` format
3. `start_time`: The expected starting time of the time slot that services are delivered in the `HH:MM` format
4. `end_time`: The expected ending time of the time slot that services are delivered in the `HH:MM` format
5. `recurrence`: The recurrence interval between services eg `P1D`, `P2D`, `P7D`, `P14D`...
6. `monday` ~ `sunday`: Variables indicating if the service should be delivered on the corresponding day of week

> Schedule upcoming tasks

This endpoint serves to assign the upcoming schedule for the services for the specified contract. **WARNING**: It is not intended that this route is called directly, as the [draft route](#262-draft-route) will call this route when a request is received. Users can _EITHER_ send a `POST` request to create a new instance _OR_ send a `PUT` request to update the draft lifecycle at the following endpoint:

```

<baseURL>/vis-backend-agent/contracts/schedule

```

Note that this route does require the following `JSON` request parameters:

```json
{
  /* parameters */
  "id": "An identifier for the scheduler",
  "contract": "The target contract IRI",
  "time slot start": "Beginning of the time window during which the service is scheduled to be delivered in the HH:MM format",
  "time slot end": "End of the time window during which the service is scheduled to be delivered in the HH:MM format",
  "recurrence": "Service interval in the ISO 8601 format eg P1D P7D P2D",
  "monday": "A boolean indicating if the service should occur on a monday",
  "tuesday": "A boolean indicating if the service should occur on a tuesday",
  "wednesday": "A boolean indicating if the service should occur on a wednesday",
  "thursday": "A boolean indicating if the service should occur on a thursday",
  "friday": "A boolean indicating if the service should occur on a friday",
  "saturday": "A boolean indicating if the service should occur on a saturday",
  "sunday": "A boolean indicating if the service should occur on a sunday"
}
```

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "Contract IRI",
    "message": "Schedule has been successfully drafted for the contract! _OR_ Draft schedule has been successfully updated!"
  }
}
```

#### 2.6.4 Service commencement route

The endpoint serves to commence the delivery of services by approving the specified contract. This endpoint will also generate the occurrences on their scheduled dates. Users must send a `POST` request to approve the contract at the following endpoint:

```
<baseURL>/vis-backend-agent/contracts/service/commence
```

Note that this route does require the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "Either one contract IRI or an array of contract IRIs",
  "remarks": "Remarks for the approval"
}
```

A successful request will return:

```json
{
  "apiVersion": "1.0.0",
  "data": {
    "id": "Contract IRI",
    "message": "Contract has been approved for service execution!"
  }
}
```

#### 2.6.5 Service order route

This `<baseURL>/vis-backend-agent/contracts/service` endpoint serves to interact with all active contracts and tasks.

> Active contracts

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/count?type={type}` endpoint to retrieve the number of active contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`.

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service?type={type}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all active contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

There is also an additional optional parameter `label` to retrieve in progress contracts with only human readable values. Users may pass in `yes` if the response should all be labelled and `no` otherwise.

> Records of service tasks

For tasks associated with a contract, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/{contract}?type={contractType}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all tasks for the target contract, where `contract` is the contract's identifier and `contractType` is the resource ID of the contract type, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

For outstanding tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/outstanding?type={contractType}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all outstanding open tasks, where `contractType` is the resource ID of the contract type, `{page}` is the current page number (with 1-index), and `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

To get the count of outstanding tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/outstanding/count?type={contractType}` endpoint.

For upcoming scheduled tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/scheduled?type={contractType}&startTimestamp={start}&endTimestamp={end}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all scheduled tasks for the target date range, where `contractType` is the resource ID of the contract type, `start` and `end` are the UNIX timestamps for the corresponding starting and ending date of a period that the users are interested in. The start date must occur after today, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

To get the count of upcoming scheduled tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/scheduled/count?type={contractType}}&startTimestamp={start}&endTimestamp={end}` endpoint.

For closed tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/closed?type={contractType}&startTimestamp={start}&endTimestamp={end}&page={page}&limit={limit}&sort_by={sortby}` endpoint to retrieve all closed tasks for the target date range, where `contractType` is the resource ID of the contract type, `start` and `end` are the UNIX timestamps for the corresponding starting and ending date of a period that the users are interested in, `{page}` is the current page number (with 1-index), `{limit}` is the number of results per page, and `{sortby}` specifies one or more fields for sorting. 

> [!TIP]  
> `sort_by` accepts a comma-separated string of field names, each prefixed by a direction indicator (+ or -). `+` indicates ascending order, while `-` indicates descending order. Example: `+name,-id`

To get the count of closed tasks, users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/closed/count?type={contractType}}&startTimestamp={start}&endTimestamp={end}` endpoint.

> Service dispatch

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/dispatch/{id}` endpoint to retrieve the form template associated with the dispatch event, where `id` is either the identifier of the occurrence of interest, which may be a dispatch or subsequent succeeding instance, or `form` for an empty form template. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ServiceDispatchOccurrenceShape` is defined in `./resources/shacl.ttl`, which can be extended for your specific requirements.

Users can send a `PUT` request to the `<baseURL>/vis-backend-agent/contracts/service/dispatch` endpoint to assign dispatch details for a target order. The details are configurable using the `ServiceDispatchOccurrenceShape` and an additional `dispatch.jsonld` file with the corresponding identifier as `dispatch` in the `application-service.json`. A sample file is defined in `./resources/jsonld/dispatch.jsonld`, with line 1 - 32 being required. It is recommended that the id field comes with a prefix, following the frontend actions. Note that this route does require the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "The target contract IRI",
  "order": "The target order IRI",
  "date": "Scheduled date of the order delivery in the YYYY-MM-DD format"
}
```

> Service completion

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/complete/{id}` endpoint to retrieve the form template associated with the service completion event, where `id` is either the identifier of the service completion occurrence of interest, or `form` for an empty form template. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ServiceOrderCompletedOccurrenceShape` is defined in `./resources/shacl.ttl`, which can be extended for your specific requirements. Note that if you require any form of computation for the completion details, it is recommended to define a separate group using `sh:node` as evident by `WeightLogShape`.

Users can send a `PUT` request to the `<baseURL>/vis-backend-agent/contracts/service/saved` endpoint to update and save the completion details for a target order without completing the order. This route serves to save the values if the order is to be executed over several days. The details are configurable as per the above.

Users can send a `PUT` request to the `<baseURL>/vis-backend-agent/contracts/service/complete` endpoint to update the completion details for a target order and complete this task. The details are configurable as per the above.

Users can also send a `POST` request to the `<baseURL>/vis-backend-agent/contracts/service/continue` endpoint **AFTER completing the task** to continue the task on the next working day (typically Saturday and Sunday will be excluded). This route will require the following parameters:

1. `id`: the current completed task identifier
2. `contract`: the identifier for the contract

> Report unfulfilled service tasks

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/report/form` endpoint to retrieve the form template associated with the report event. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ServiceReportOccurrenceShape` is defined in `./resources/shacl.ttl`. At the moment, properties may ONLY include remarks.

Users can send a `POST` request to the `<baseURL>/vis-backend-agent/contracts/service/report` endpoint to report an unfulfilled service of a specified contract. Note that this route does require the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "The target contract IRI",
  "remarks": "Remarks for the report",
  "date": "Date of the unfulfilled service in the YYYY-MM-DD format; Date must be in the past or today"
}
```

> Cancel service tasks

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/service/cancel/form` endpoint to retrieve the form template associated with the cancellation event. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ServiceTerminationOccurrenceShape` is defined in `./resources/shacl.ttl`. At the moment, properties may ONLY include remarks.

Users can send a `POST` request to the `<baseURL>/vis-backend-agent/contracts/service/cancel` endpoint to cancel an upcoming service of a specified contract. Note that this route does require the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "The target contract IRI",
  "remarks": "Remarks for the cancellation",
  "date": "Upcoming service date to be cancelled in the YYYY-MM-DD format; Date must be today or in future"
}
```

#### 2.6.6 Archive contract route

The endpoint serves to archive in progress contracts as well as retrieve all contracts that have expired and are in archive.

> Get all archived contracts

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/archive/count?type={type}` endpoint to retrieve the number of archived contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`.

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/archive?type={type}` endpoint to retrieve all archived contracts, where `{type}`is the requested identifier that must correspond to the target contract class in`./resources/application-form.json`.

There is also an additional optional parameter `label` to retrieve archived contracts with only human readable values. Users may pass in `yes` if the response should all be labelled and `no` otherwise.

> Rescind an ongoing contract

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/archive/rescind/form` endpoint to retrieve the form template associated with the contract rescission event. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ContractRescissionOccurrenceShape` is defined in `./resources/shacl.ttl`. At the moment, properties may ONLY include remarks.

Users must send a `POST` request to rescind an ongoing contract at the `<baseURL>/vis-backend-agent/contracts/archive/rescind` endpoint, with the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "The target contract IRI",
  "remarks": "Reasons for the rescindment"
}
```

> Terminate an ongoing contract

Users can send a `GET` request to the `<baseURL>/vis-backend-agent/contracts/archive/terminate/form` endpoint to retrieve the form template associated with the contract rescission event. Note that this will require `SHACL` restrictions to be defined and instantiated into the knowledge graph. A sample `ContractTerminationOccurrenceShape` is defined in `./resources/shacl.ttl`. At the moment, properties may ONLY include remarks.

Users must send a `POST` request to terminate an ongoing contract at the `<baseURL>/vis-backend-agent/contracts/archive/terminate` endpoint, with the following `JSON` request parameters:

```json
{
  /* parameters */
  "contract": "The target contract IRI",
  "remarks": "Reasons for the early termination"
}
```
