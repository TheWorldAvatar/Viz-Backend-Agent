package com.cmclinnovations.agent.model.pagination;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.sparqlbuilder.core.OrderCondition;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;

public class PaginationState {
    private static final Pattern SORT_PARAM_PATTERN = Pattern.compile("([-+])?([^,]+)");

    private final int limit;
    private final Integer offset;
    private final Set<String> targetFields;
    private final Map<String, Set<String>> filters;
    private final Queue<SortDirective> sortedDirectives;

    public PaginationState(int pageIndex, int limit, String sortBy, Map<String, String> filters) {
        this.limit = limit;
        // Page index starts from 0
        this.offset = pageIndex * limit;
        // REGEX will match two groups per sort directive in the url
        this.targetFields = SORT_PARAM_PATTERN.matcher(sortBy)
                .results()
                .map(match -> match.group(2))
                .collect(Collectors.toCollection(HashSet::new));
        this.sortedDirectives = this.parseSortDirectives(sortBy);
        this.filters = filters.entrySet()
                .stream()
                .map(entry -> Map.entry(
                        entry.getKey(),
                        Arrays.stream(entry.getValue().split("\\|"))
                                .map(string -> "\"" + string.trim() + "\"")
                                .collect(Collectors.toSet())))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        // Add filter fields to target fields to keep track of which variables to
        // include in the query
        this.targetFields.addAll(this.filters.keySet());
    }

    public int limit() {
        return this.limit;
    }

    public Integer offset() {
        return this.offset;
    }

    public Set<String> targetFields() {
        return this.targetFields;
    }

    public Queue<SortDirective> sortDirectives() {
        return this.sortedDirectives;
    }

    public Map<String, Set<String>> filters() {
        return this.filters;
    }

    /**
     * Parses the sort directives from the 'sort_by' parameter.
     * 
     * @param sortBy The `sort_by` parameter string.
     */
    private Queue<SortDirective> parseSortDirectives(String sortBy) {
        // REGEX will match two groups per sort directive in the url
        return SORT_PARAM_PATTERN.matcher(sortBy)
                .results()
                .map(match -> {
                    String field = match.group(2);
                    if (field.toLowerCase()
                            .equals(LocalisationTranslator.getMessage(LocalisationResource.VAR_STATUS_KEY))) {
                        field = LifecycleResource.EVENT_KEY;
                    } else if (field.toLowerCase().equals(LocalisationTranslator
                            .getMessage(LocalisationResource.VAR_SCHEDULE_TYPE_KEY).toLowerCase())) {
                        field = LifecycleResource.SCHEDULE_RECURRENCE_KEY;
                    }
                    Variable fieldVar = QueryResource.genVariable(field);
                    // First group matches the sign
                    String sign = match.group(1);
                    OrderCondition orderCondition;
                    if (sign.equals("-")) {
                        orderCondition = SparqlBuilder.desc(fieldVar);
                    } else {
                        orderCondition = SparqlBuilder.asc(fieldVar);
                    }

                    return new SortDirective(fieldVar, orderCondition);
                })
                .collect(Collectors.toCollection(ArrayDeque::new));
    }
}
