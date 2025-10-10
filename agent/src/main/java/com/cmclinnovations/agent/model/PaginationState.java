package com.cmclinnovations.agent.model;

public record PaginationState(int pageIndex, int limit, Integer offset) {
    public PaginationState {
        // Current page number must subtract 1, as the first page should have no offset
        offset = (pageIndex - 1) * limit;
    }

    public PaginationState(int pageIndex, int limit) {
        this(pageIndex, limit, null);
    }
}
