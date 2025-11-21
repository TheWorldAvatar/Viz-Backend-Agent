package com.cmclinnovations.agent.model.pagination;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class PaginationStateTest {
    @Test
    void testEmptyPaginationState() {
        PaginationState sample = new PaginationState(0, null, "", new HashMap<>());
        assertNull(sample.getLimit());
        assertEquals(sample.getOffset(), 0);
        assertTrue(sample.getFilters().isEmpty());
        assertTrue(sample.getSortDirectives().isEmpty());
        assertTrue(sample.getSortedFields().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "0,5,5,0", "1,5,5,5", "2,5,5,10", "5,10,10,50" })
    void testOffsetComputation(int pageIndex, int limit, int expectedLimit, int expectedOffset) {
        PaginationState sample = new PaginationState(pageIndex, limit, "", new HashMap<>());
        assertEquals(sample.getLimit(), expectedLimit);
        assertEquals(sample.getOffset(), expectedOffset);
        assertTrue(sample.getFilters().isEmpty());
        assertTrue(sample.getSortDirectives().isEmpty());
        assertTrue(sample.getSortedFields().isEmpty());
    }

    @Test
    void testSortPaginationState() {
        String varName = "id";
        PaginationState sample = new PaginationState(0, null, "-" + varName, new HashMap<>());
        assertTrue(sample.getFilters().isEmpty());
        assertTrue(sample.getSortedFields().contains(varName));
        assertEquals(sample.getSortDirectives().size(), 1);
        SortDirective sampleDirective = sample.getSortDirectives().poll();
        assertEquals(sampleDirective.field().getVarName(), varName);
        assertEquals(sampleDirective.order().getQueryString(), "DESC( ?" + varName + " )");
    }
}
