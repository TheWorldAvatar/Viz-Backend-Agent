package com.cmclinnovations.agent.component;

import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Subtask;

import org.springframework.security.concurrent.DelegatingSecurityContextCallable;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.exception.ParallelInterruptedException;
import com.cmclinnovations.agent.model.util.ParallelTableQueryManifest;

@Component
public class ParallelTaskExecutor {
    private ParallelTaskExecutor() {
    }

    /**
     * Executes data retrieval and counting tasks in parallel using Structured
     * Concurrency.
     * 
     * @param dataTask          Task to retrieve the data records.
     * @param filteredCountTask Task to retrieve the count of records matching the
     *                          current filters.
     * @param totalCountTask    Task to retrieve the total number of records in the
     *                          dataset.
     */
    public static <T> ParallelTableQueryManifest<T> execParallelQueryTasks(
            Callable<T> dataTask,
            Callable<Integer> filteredCountTask,
            Callable<Integer> totalCountTask) {

        SecurityContext context = SecurityContextHolder.getContext();

        try (var scope = StructuredTaskScope.open()) {
            var dataSubtask = scope.fork(new DelegatingSecurityContextCallable<>(dataTask, context));

            Subtask<Integer> filteredSubtask = scope
                    .fork(new DelegatingSecurityContextCallable<>(filteredCountTask, context));
            Subtask<Integer> totalSubtask = scope
                    .fork(new DelegatingSecurityContextCallable<>(totalCountTask, context));

            try {
                scope.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ParallelInterruptedException("One of the parallel task has been interrupted: ", e);
            }

            return new ParallelTableQueryManifest<>(dataSubtask.get(), filteredSubtask.get(), totalSubtask.get());
        }
    }
}
