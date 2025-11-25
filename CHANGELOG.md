# Change Log

## 1.19.0

### Changes

- Implemented server-side pagination and filters
- Added routes for retrieving count and filter options


## 1.18.1

### Changes

- Allow customised JSON-LD for cancelled job and job with issue to be used

## 1.18.0

### Changes

- Added a route to copy contracts X number of times based on parameter
- Added an `@optional` key in JSONLD to denote optional parameters

## 1.17.0

### Changes

- Extended contract commence route to allow users to commence multiple contracts
- Refactored the draft contract reset route into a new reset route to reset multiple contracts

## 1.16.2

### Changes

- Provide a custom data property for text area inputs

## 1.16.1

### Bug fixes

- Fix to update date modified for draft contract changes

## 1.16.0

### Changes

- Added lock mechanism for most routes
- Added checks to prevent duplicate approval, modifications of approved contracts, and scheduling a duplicate task entry on next working date

## 1.15.3

### Changes

- Added date provenance for draft and active contracts using date time values
- Date to execute task is now retrieved only from the order created occurrence date

## 1.15.2

### Changes

- Added ordering for form branching

## 1.15.1

### Bug fixes

- Fix form branching to be compliant with `sh:and` constraints and group them

## 1.15.0

### Changes

- Extended to include perpetual service type
- General code maintenance

## 1.14.0

### Changes

- Changes to requirements: SPARQL endpoints **MUST** have inference enabled
- Refactor agent to work with inference engine to find subclasses rather than relying on `rdfs:subClassOf*`
- If inference is not enabled, some desired results may not be returned

## 1.13.1

### Changes

- Hides any group fields
- Add function to clean up previous derivations before adding new derivations
- General code maintenance

### Bug fixes

- Fixed amending of completed assignments and completion details
- Fixed saving of completion details
- Fixed retrieval of previous details, especially within `sh:node` groups
- Fixed derivation of literals

## 1.13.0

### Changes

- Added statuses for draft contract to differentiate pending and amended draft contracts
- Added a route to reset amended statuses back to pending
- Modified dispatch assignment behaviours so that dates will follow the previous dates instead of being updated to today
- General code maintenance

## 1.12.4

### Bug fix

- Array replacement objects are correctly parsed for deletion

## 1.12.3

### Bug fix

- Fix the bug for missing dispatch details when duplicating tasks

## 1.12.2

### Bug fix

- Allow users to specify alternate labels for classes in queries

## 1.12.1

### Changes

- Refactor query generation to implement RDF4J SparqlBuilder
- Improve DELETE query template to utilise variables instead of exact instance matches
- Improve logging statement for missing fields

## 1.12.0

### Features

- Added a route to continue task on the next working day
- Added default `log4j.properties`
- Updated the standard `complete` and `dispatch` JSON-LD

## 1.11.0

### Features

- Standardised the predicates of identifiers using `dcterm:identifier` by default
- Include an ID property shape by default for form templates
- Id will be instantiated by default for all new instances
- Update queries to retrieve by this ID property instead of using `FILTER`

### Bug fix

- Fixed mismatched queries with different IDs and IRIs

## 1.10.0

### Features

- Added a saved route to save completion data if required
- Extended SHACL rules for service lifecycle
- Increase filter ID accuracy with REGEX instead of STRENDS

### Bug fix

- Fix query for mixed endpoints
- Fix the query to return only the event for the specific date for the target contract
- Updated stack client version and package registry

## 1.9.2

### Bug fix

- Cancel, report, assign, and complete actions are now instantiated with the current date whether for adding or updating

## 1.9.1

### Features

- Update translations

## 1.9.0

### Features

- Changed tasks route for a specific date into outstanding, scheduled, and closed routes
- API version is directly extracted from `pom.xml` to remain updated

## 1.8.2

### Features

- Users can set a default value for each form field in the SHACL shapes

### Bug fix

- Fixed optional IRI fields being added as blank nodes

## 1.8.1

### Bug fix

- Fixed translated german status being uppercase

## 1.8.0

### Features

- Added translations for the backend
- Restructured JSON body for responses to follow the Google API style guide
- Removed deprecated csv and calculation route
- Improved security configuration

## 1.7.0

### Features

- Added authentication workflows
- Added role-based data access functionality

## 1.6.1

### Features

- Use exact ID matching instead of STRENDS qualifiers for both IRI and string literal matching

## 1.6.0

### Features

- Modified the requirements to group arrays in SHACL constraints
- Simplified the code for generating queries
- Simplified the dynamically generated SPARQL queries for branches, arrays, and groups
- Complete task is changed from a POST to PUT route

## 1.5.2

### Features

- Added derivation of additional triples when instantiating triples through SHACL rules

## 1.5.1

### Features

- Add query statement to extract steps from the SHACL constraints

## 1.5.0

### Features

- Improved the retrieval of order dispatch details
- Added the retrieval of the order completion records as a form and individually
- General code improvements to consolidate code and separate concerns

### Bug Fixes

- Fix calculation requiring exact match for parameter names that are nested

## 1.4.2

### Bug Fixes

- Fix failure to ignore missing order key when optional

## 1.4.1

### Bug Fixes

- Fix the inability to complete tasks due to wrong event query

## 1.4.0

### Features

- Added more informative task details
- Added logging of queries before execution
- Added simple unit tests
- General code improvements

## 1.3.0

### Features

- Added form branch capability
- Added form array fields capability
- Enable an array of values for the `sh:in` property for forms
- General improvements to query generation for GET and DELETE queries
- Added sample pricing model configurations

### Bug Fixes

- Fix lifecycle related queries not returning the right occurrences

## 1.2.2

### Features

- Simplifies dependent form fields

### Bug Fixes

- Fix conflicting independent form fields with similar names

## 1.2.1

### Bug Fixes

- Fixes error when retrieval form template with no default values for dispatch
- Fixes error for accepting the wrong field parameter when instantiating

## 1.2.0

### Features

- Added capabilities to manage lifecycle states and transitions

## 1.1.0

### Features

- Extension of search functionality to general data and time series
- Added geocoding capabilities

## 1.0.0

### Features

- Introduced the initial version of VisBackend Agent to add, delete, update, or get instances within the registry using SHACL
