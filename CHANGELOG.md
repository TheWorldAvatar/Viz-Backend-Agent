# Change Log

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
