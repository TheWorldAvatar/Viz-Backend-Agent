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

import com.cmclinnovations.agent.utils.LocalisationResource;
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
                .map(match -> match.group(2))
                .collect(Collectors.toCollection(HashSet::new));
        this.sortedDirectives = this.parseSortDirectives(sortBy, isContract);
        this.filters = StringResource.parseFilters(filters, isContract);
    }

    public Integer limit() {
        return this.limit;
    }

    public int offset() {
        return this.offset;
    }

    public Set<String> sortedFields() {
        return this.sortedFields;
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
     * @param sortBy     The `sort_by` parameter string.
     * @param isContract Indicates if it is a contract or task otherwise.
     */
    private Queue<SortDirective> parseSortDirectives(String sortBy, Boolean isContract) {
        // REGEX will match two groups per sort directive in the url
        return SORT_PARAM_PATTERN.matcher(sortBy)
                .results()
                .map(match -> {
                    String field = match.group(2);
                    if (isContract != null) {
                        field = LocalisationResource.parseTranslationToOriginal(field, isContract);
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
