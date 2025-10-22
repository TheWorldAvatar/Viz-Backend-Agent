package com.cmclinnovations.agent.model.function;

import org.springframework.http.ResponseEntity;

import com.cmclinnovations.agent.model.response.StandardApiResponse;

@FunctionalInterface
public interface ContractOperation {
    ResponseEntity<StandardApiResponse<?>> apply(String contractId) throws IllegalArgumentException;
}