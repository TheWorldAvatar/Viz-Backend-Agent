package com.cmclinnovations.agent.model;

import java.util.ArrayList;
import java.util.List;

import org.eclipse.rdf4j.model.vocabulary.RDF;
import org.eclipse.rdf4j.model.vocabulary.RDFS;
import org.eclipse.rdf4j.sparqlbuilder.constraint.Expressions;
import org.eclipse.rdf4j.sparqlbuilder.constraint.propertypath.builder.PropertyPathBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPattern;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.TriplePattern;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.eclipse.rdf4j.sparqlbuilder.rdf.RdfSubject;

import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.ShaclResource;

public class ShaclPropertyBinding {
    private Variable property;
    private Variable group;
    private Variable branch;

    private Iri instanceClass;
    private Iri nestedClass;

    private RdfSubject subject;
    private PropertyPathBuilder predicate;
    private PropertyPathBuilder labelPredicate;
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
        this.predicate = this.parsePredicate(binding, ShaclResource.MULTIPATH_VAR, this.predicate);
        this.labelPredicate = this.parsePredicate(binding, ShaclResource.MULTI_NAME_PATH_VAR, this.labelPredicate);
    }

    /**
     * Retrieves the name of the property.
     */
    public String getName() {
        return this.property.getVarName();
    }

    /**
     * Retrieves the group of the property if available. Defaults to empty string.
     */
    public String getGroup() {
        return this.group != null ? this.group.getVarName() : "";
    }

    /**
     * Retrieves the branch of the property if available. Defaults to empty string.
     */
    public String getBranch() {
        return this.branch != null ? this.branch.getVarName() : "";
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
     * Writes out the SPARQL query line(s) as graph pattern.
     * 
     * @param isGroup Indicates if the content is intended to be for a group
     *                or not
     */
    public List<GraphPattern> write(boolean isGroup) {
        PropertyPathBuilder jointPredicate = this.labelPredicate == null ? this.predicate
                : this.predicate.then(this.labelPredicate.build());
        List<GraphPattern> contents = new ArrayList<>();
        // Add a final rdfs:label if it is a class without other labels
        if (this.isClazz && this.labelPredicate == null) {
            jointPredicate = jointPredicate.then(RDFS.LABEL);
        }

        TriplePattern primaryTriples = this.subject.has(jointPredicate.build(), this.property);
        if (this.subjectFilter.isEmpty()) {
            contents.add(primaryTriples);
        } else {
            // If the value must conform to a specific subject variable,
            // a filter needs to be added directly to the same clause
            contents.add(
                    primaryTriples.filter(
                            Expressions.equals(this.property, Rdf.literalOf(this.subjectFilter))));
        }

        // If sh:node targetClass is available and not a group, append the class
        // restriction
        if (!isGroup && this.instanceClass != null) {
            // If this is an instance, add a statement targeting the exact class
            // Inverse the label predicate if it exist
            GraphPattern classPattern = this.property.isA(this.instanceClass);
            if (this.labelPredicate != null) {
                classPattern = this.property.has(this.labelPredicate.inv().then(RDF.TYPE).build(),
                        this.instanceClass);
            }
            contents.add(classPattern);
            // Append nested class only if this is a group and there is a value
        } else if (isGroup && this.nestedClass != null) {
            // For SPARQL endpoints which support property paths
            contents.add(
                    this.property.isA(this.nestedClass));
        }
        return contents;
    }

    /**
     * Parses and stores the binding into this object's private variables.
     * 
     * @param binding An individual binding queried from SHACL restrictions.
     */
    private void parseBinding(SparqlBinding binding) {
        this.property = QueryResource.genVariable(binding.getFieldValue(ShaclResource.NAME_PROPERTY));
        String groupValue = binding.getFieldValue(ShaclResource.NODE_GROUP_VAR);
        this.group = groupValue != null ? QueryResource.genVariable(groupValue) : null;
        String branchValue = binding.getFieldValue(ShaclResource.BRANCH_VAR);
        this.branch = branchValue != null ? QueryResource.genVariable(branchValue) : null;

        this.subject = this.group != null ? this.group : QueryResource.IRI_VAR;
        this.appendPred(binding);

        this.subjectFilter = binding.getFieldValue(ShaclResource.SUBJECT_VAR, "");

        String instanceClassResult = binding.getFieldValue(ShaclResource.INSTANCE_CLASS_VAR, "");
        if (!instanceClassResult.isEmpty()) {
            this.instanceClass = Rdf.iri(instanceClassResult);
        }

        String nestedClassResult = binding.getFieldValue(ShaclResource.NESTED_CLASS_VAR, "");
        if (!nestedClassResult.isEmpty()) {
            this.nestedClass = Rdf.iri(nestedClassResult);
        }

        this.isArray = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_ARRAY_VAR));
        this.isClazz = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_CLASS_VAR));
        this.isOptional = Boolean.parseBoolean(binding.getFieldValue(ShaclResource.IS_OPTIONAL_VAR));
    }

    /**
     * Parses the predicate associated with the input variable to the requested
     * property builder.
     * 
     * @param binding              An individual binding queried from SHACL
     *                             restrictions.
     * @param propertyPathVariable The current property path part variable name.
     * @param propertyBuilder      The current property path builder.
     */
    private PropertyPathBuilder parsePredicate(SparqlBinding binding, String propertyPathVariable,
            PropertyPathBuilder propertyBuilder) {
        if (binding.containsField(propertyPathVariable)) {
            String predPath = binding.getFieldValue(propertyPathVariable);
            // Do not process any paths without the http protocol as it is likely to be a
            // blank node
            Iri currentPredicate = Rdf.iri(predPath);
            if (!currentPredicate.getQueryString().startsWith("<http")) {
                return propertyBuilder;
            }
            // Build new property paths based on path prefixes in the SHACL restrictions
            // Each clause should be separated as we may use other path prefixes in future
            PropertyPathBuilder pathPrefixBuilder = null;
            if (binding.containsField(propertyPathVariable + ShaclResource.PATH_PREFIX)) {
                // For inverse paths, simply append a ^ before the parsed IRI
                if (binding.getFieldValue(propertyPathVariable + ShaclResource.PATH_PREFIX)
                        .equals(ShaclResource.SHACL_PREFIX + "inversePath")) {
                    pathPrefixBuilder = PropertyPathBuilder.of(currentPredicate).inv();
                }
            }
            // Defaults to property path builders if available, else, attach them
            if (propertyBuilder == null) {
                propertyBuilder = pathPrefixBuilder == null ? PropertyPathBuilder.of(currentPredicate)
                        : pathPrefixBuilder;
            } else {
                propertyBuilder = pathPrefixBuilder == null ? propertyBuilder.then(currentPredicate)
                        : propertyBuilder.then(pathPrefixBuilder.build());
            }
        }
        return propertyBuilder;
    }
}
