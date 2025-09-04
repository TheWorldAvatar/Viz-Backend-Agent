package com.cmclinnovations.agent.model;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

public class ShaclPropertyBinding {
    private String property;
    private String group;
    private String branch;

    private String instanceClass;
    private String nestedClass;

    private String subject;
    private String predicate;
    private String labelPredicate;
    private String subjectFilter;

    private boolean isArray;
    private boolean isOptional;
    private boolean isClazz;

    /**
     * A class representing the SHACL property shape with additional variables to
     * support the generation of a SPARQL query line.
     * 
     * @param binding Initial variables queried from the KG.
     */
    public ShaclPropertyBinding(SparqlBinding binding) {
        this.parseBinding(binding);
    }

    /**
     * Appends the new predicate in the binding to the existing predicates.
     * 
     * @param binding Additional bindings containing the new predicates.
     */
    public void appendPred(SparqlBinding binding) {
        String multiPartPredicate = this.getPredicate(binding, ShaclResource.MULTIPATH_VAR);
        this.predicate = this.parsePredicate(this.predicate, multiPartPredicate);
        String multiPartLabelPredicate = this.getPredicate(binding, ShaclResource.MULTI_NAME_PATH_VAR);
        this.labelPredicate = this.parsePredicate(this.labelPredicate, multiPartLabelPredicate);
    }

    /**
     * Retrieves the name of the property.
     */
    public String getName() {
        return this.property;
    }

    /**
     * Retrieves the group of the property if available. Defaults to empty string.
     */
    public String getGroup() {
        return this.group != null ? this.group : "";
    }

    /**
     * Retrieves the branch of the property if available. Defaults to empty string.
     */
    public String getBranch() {
        return this.branch != null ? this.branch : "";
    }

    /**
     * Indicates if the property is an array.
     */
    public boolean isArray() {
        return this.isArray;
    }

    /**
     * Indicates if the property is optional.
     */
    public boolean isOptional() {
        return this.isOptional;
    }

    /**
     * Writes out the SPARQL query line(s) based on the stored state.
     * 
     * @param isGroup Indicates if the content is intended to be for a group or not
     */
    public String write(boolean isGroup) {
        StringBuilder currentLine = new StringBuilder();
        String jointPredicate = this.parsePredicate(this.predicate, this.labelPredicate);
        // Add a final rdfs:label if it is a class to retrieve the label
        if (this.isClazz) {
            jointPredicate = this.parsePredicate(jointPredicate, ShaclResource.RDFS_LABEL_PREDICATE);
        }

        // Simply bind the iri as the id if it is self-targeting
        if (this.property.equals(StringResource.ID_KEY) && this.verifySelfTargetIdField(jointPredicate)) {
            return "BIND(?iri AS ?id)";
        }

        String queryProperty = StringResource.parseQueryVariable(this.property);
        StringResource.appendTriple(currentLine,
                ShaclResource.VARIABLE_MARK + StringResource.parseQueryVariable(this.subject),
                jointPredicate,
                ShaclResource.VARIABLE_MARK + queryProperty);
        // If sh:node targetClass is available and not a group, append the class
        // restriction
        if (!isGroup && !this.instanceClass.isEmpty()) {
            // If this is an instance, add a statement targeting the exact class
            // Inverse the label predicate if it exist
            String inverseLabelPred = !this.labelPredicate.isEmpty()
                    ? "^(" + this.labelPredicate + ")/"
                    : "";
            StringResource.appendTriple(currentLine,
                    ShaclResource.VARIABLE_MARK + queryProperty,
                    inverseLabelPred + StringResource.RDF_TYPE,
                    Rdf.iri(this.instanceClass).getQueryString());
            // Append nested class only if this is a group and there is a value
        } else if (isGroup && !this.nestedClass.isEmpty()) {
            StringResource.appendTriple(currentLine,
                    ShaclResource.VARIABLE_MARK + queryProperty,
                    StringResource.RDF_TYPE + StringResource.REPLACEMENT_PLACEHOLDER,
                    Rdf.iri(this.nestedClass).getQueryString());
        }

        // If the value must conform to a specific subject variable,
        // a filter needs to be added directly to the same optional clause
        if (!this.subjectFilter.isEmpty()) {
            currentLine.append("FILTER(STR(?")
                    .append(queryProperty)
                    .append(") = \"")
                    .append(this.subjectFilter)
                    .append("\")");
        }

        return currentLine.toString();
    }

    /**
     * Parses and stores the binding into this object's private variables.
     * 
     * @param binding An individual binding queried from SHACL restrictions.
     */
    private void parseBinding(SparqlBinding binding) {
        this.property = binding.getFieldValue(ShaclResource.NAME_PROPERTY);
        this.group = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
        this.branch = binding.getFieldValue(ShaclResource.BRANCH_VAR);

        this.subject = this.group != null ? this.group : LifecycleResource.IRI_KEY;
        this.predicate = this.getPredicate(binding, ShaclResource.MULTIPATH_VAR);
        this.labelPredicate = this.getPredicate(binding, ShaclResource.MULTI_NAME_PATH_VAR);

        this.subjectFilter = binding.getFieldValue(ShaclResource.SUBJECT_VAR, "");

        this.instanceClass = binding.getFieldValue(ShaclResource.INSTANCE_CLASS_VAR, "");
        this.nestedClass = binding.getFieldValue(ShaclResource.NESTED_CLASS_VAR, "");

        this.isArray = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_ARRAY_VAR));
        this.isClazz = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_CLASS_VAR));
        this.isOptional = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_OPTIONAL_VAR));
    }

    /**
     * Gets the predicate associated with the input variable. Returns an empty
     * string if not found.
     * 
     * @param binding              An individual binding queried from SHACL
     *                             restrictions.
     * @param propertyPathVariable The current property path part variable name.
     */
    private String getPredicate(SparqlBinding binding, String propertyPathVariable) {
        if (binding.containsField(propertyPathVariable)) {
            String predPath = binding.getFieldValue(propertyPathVariable);
            // Do not process any paths without the http protocol as it is likely to be a
            // blank node
            if (predPath.startsWith("http")) {
                String parsedPredPath = Rdf.iri(predPath).getQueryString();
                // Check if there are path prefixes in the SHACL restrictions
                // Each clause should be separated as we may use other path prefixes in future
                if (binding.containsField(propertyPathVariable + ShaclResource.PATH_PREFIX)) {
                    // For inverse paths, simply append a ^ before the parsed IRI
                    if (binding.getFieldValue(propertyPathVariable + ShaclResource.PATH_PREFIX)
                            .equals(ShaclResource.SHACL_PREFIX + "inversePath")) {
                        return "^" + parsedPredPath;
                    }
                }
                // If no path prefixes are available, simply return the <predicate>
                return parsedPredPath;
            }
        }
        return "";
    }

    /**
     * Parses the predicate to concatenante the current and next predicate in a
     * SPARQL compliant format.
     * 
     * @param currentPredicate Current predicate in the existing mapping
     * @param nextPredicate    Next predicate for appending.
     */
    private String parsePredicate(String currentPredicate, String nextPredicate) {
        if (nextPredicate.isEmpty()) {
            return currentPredicate;
        }
        if (currentPredicate.isEmpty()) {
            return nextPredicate;
        } else {
            return currentPredicate + "/" + nextPredicate;
        }
    }

    /**
     * Verifies if the ID field is targeting the IRI.
     * 
     * @param predicate The predicate string of the ID field.
     */
    private boolean verifySelfTargetIdField(String predicate) {
        // Compile the potential patterns to match
        Pattern pattern1 = Pattern.compile("<([^>]+)>/\\^<\\1>");
        Pattern pattern2 = Pattern.compile("\\^<([^>]+)>/<\\1>");

        // Create matchers for both patterns
        Matcher matcher1 = pattern1.matcher(predicate);
        Matcher matcher2 = pattern2.matcher(predicate);

        // Return true if input matches either pattern 1 or pattern 2
        return matcher1.matches() || matcher2.matches();
    }
}
