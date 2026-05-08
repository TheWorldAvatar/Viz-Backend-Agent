package com.cmclinnovations.agent.exception;

public class ParallelInterruptedException extends RuntimeException {
    public ParallelInterruptedException(String message) {
        super(message);
    }

    public ParallelInterruptedException(String message, Throwable cause) {
        super(message, cause);
    }
}
