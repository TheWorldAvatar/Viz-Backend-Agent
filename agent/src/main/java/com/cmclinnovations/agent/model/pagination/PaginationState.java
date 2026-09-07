package com.cmclinnovations.agent.model.pagination;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.sparqlbuilder.core.OrderCondition;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.QueryResource;
import com.cmclinnovations.agent.utils.StringResource;

public class PaginationState {
    private static final Pattern SORT_PARAM_PATTERN = Pattern.compile("([-+])?([^,]+)");

    private final Integer limit;
    private final int offset;
    private final Set<String> sortedFields;
    private final Map<String, Set<String>> filters;
    private final Queue<SortDirective> sortedDirectives;

    // Overloaded method without isConstract
    public PaginationState(int pageIndex, Integer limit, String sortBy, Map<String, String> filters) {
        this(pageIndex, limit, sortBy, null, filters);
    }

    public PaginationState(int pageIndex, Integer limit, String sortBy, Boolean isContract,
            Map<String, String> filters) {
        this.limit = limit;
        // Page index starts from 0
        if (limit == null) {
            this.offset = 0;
        } else {
            this.offset = pageIndex * limit;
        }
        // REGEX will match two groups per sort directive in the url
        this.sortedFields = SORT_PARAM_PATTERN.matcher(sortBy)
                .results()
                .map(match -> this.parseLifecycleSortFields(match.group(2), isContract))
                .collect(Collectors.toCollection(HashSet::new));
        this.sortedDirectives = this.parseSortDirectives(sortBy, isContract);
        this.filters = StringResource.parseFilters(filters, isContract);
    }

    public Integer getLimit() {
        return this.limit;
    }

    public int getOffset() {
        return this.offset;
    }

    public Set<String> getSortedFields() {
        return this.sortedFields;
    }

    public Queue<SortDirective> getSortDirectives() {
        return this.sortedDirectives;
    }

    public Map<String, Set<String>> getFilters() {
        return this.filters;
    }

    /**
     * Parses the sort directives from the 'sort_by' parameter.
     * 
     * @param sortBy     The `sort_by` parameter string.
     * @param isContract Indicates if it is a contract or task otherwise.
     */
    private Queue<SortDirective> parseSortDirectives(String sortBy, Boolean isContract) {
        // REGEX will match two groups per sort directive in the url
        Set<String> parsedFields = new HashSet<>();
        return SORT_PARAM_PATTERN.matcher(sortBy)
                .results()
                .map(match -> {
                    String field = this.parseLifecycleSortFields(match.group(2), isContract);
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
                // Keep the first direction when callers append an existing tie-breaker.
                .filter(directive -> parsedFields.add(directive.field().getVarName()))
                .collect(Collectors.toCollection(ArrayDeque::new));
    }

    /**
     * Parses the lifecycle sort fields into their true form.
     * 
     * @param field      The field of interest.
     * @param isContract Indicates if it is a contract or task otherwise.
     */
    private String parseLifecycleSortFields(String field, Boolean isContract) {
        String result = field;
        if (isContract != null) {
            result = LifecycleResource.revertLifecycleSpecialFields(field, isContract);
            // Last modified should always be the original non-string version for sorting
            if (result.equals(LifecycleResource.LAST_MODIFIED_KEY)) {
                result = StringResource.ORIGINAL_PREFIX + LifecycleResource.LAST_MODIFIED_KEY;
            }
        }
        return result;
    }
}
