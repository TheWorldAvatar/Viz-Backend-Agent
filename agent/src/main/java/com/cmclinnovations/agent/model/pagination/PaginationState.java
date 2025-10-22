package com.cmclinnovations.agent.model.pagination;

public record PaginationState(int pageIndex, int limit, Integer offset) {
    public PaginationState {
        // Page index starts from 0
        offset = pageIndex * limit;
    }

    public PaginationState(int pageIndex, int limit) {
        this(pageIndex, limit, null);
    }
}
