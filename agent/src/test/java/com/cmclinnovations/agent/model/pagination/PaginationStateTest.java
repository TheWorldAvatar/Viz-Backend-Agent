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
        assertNull(sample.limit());
        assertEquals(sample.offset(), 0);
        assertTrue(sample.filters().isEmpty());
        assertTrue(sample.sortDirectives().isEmpty());
        assertTrue(sample.sortedFields().isEmpty());
    }

    @ParameterizedTest
    @CsvSource({ "0,5,5,0", "1,5,5,5", "2,5,5,10", "5,10,10,50" })
    void testOffsetComputation(int pageIndex, int limit, int expectedLimit, int expectedOffset) {
        PaginationState sample = new PaginationState(pageIndex, limit, "", new HashMap<>());
        assertEquals(sample.limit(), expectedLimit);
        assertEquals(sample.offset(), expectedOffset);
        assertTrue(sample.filters().isEmpty());
        assertTrue(sample.sortDirectives().isEmpty());
        assertTrue(sample.sortedFields().isEmpty());
    }

    @Test
    void testSortPaginationState() {
        String varName = "id";
        PaginationState sample = new PaginationState(0, null, "-" + varName, new HashMap<>());
        assertTrue(sample.filters().isEmpty());
        assertTrue(sample.sortedFields().contains(varName));
        assertEquals(sample.sortDirectives().size(), 1);
        SortDirective sampleDirective = sample.sortDirectives().poll();
        assertEquals(sampleDirective.field().getVarName(), varName);
        assertEquals(sampleDirective.order().getQueryString(), "DESC( ?" + varName + " )");
    }
}
