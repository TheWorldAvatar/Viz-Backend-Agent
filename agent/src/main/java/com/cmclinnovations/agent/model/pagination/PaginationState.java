package com.cmclinnovations.agent.model.pagination;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.eclipse.rdf4j.sparqlbuilder.core.OrderCondition;
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.QueryResource;

public record PaginationState(int pageIndex, int limit, String sortBy, Integer offset) {
    private static final Pattern SORT_PARAM_PATTERN = Pattern.compile("([-+])?([^,]+)");

    public PaginationState {
        // Page index starts from 0
        offset = pageIndex * limit;
    }

    public PaginationState(int pageIndex, int limit, String sortBy) {
        this(pageIndex, limit, sortBy, null);
    }

    /**
     * Retrieve the sort directives from the 'sort_by' parameter.
     */
    public Queue<SortDirective> getSortDirectives() {
        // REGEX will match two groups per sort directive in the url
        return SORT_PARAM_PATTERN.matcher(this.sortBy)
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
