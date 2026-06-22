package com.cmclinnovations.agent.component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.StructuredTaskScope;
import java.util.concurrent.StructuredTaskScope.Joiner;
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

        try (var scope = StructuredTaskScope.open(
                Joiner.<Object>allSuccessfulOrThrow(),
                config -> config.withTimeout(Duration.ofMinutes(1)))) {
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

    public static <T> List<T> execDualParallelQueries(Callable<T> task1, Callable<T> task2) {
        SecurityContext context = SecurityContextHolder.getContext();

        try (var scope = StructuredTaskScope.open(
                Joiner.<T>allSuccessfulOrThrow(),
                config -> config.withTimeout(Duration.ofMinutes(1)))) {

            // Fork both tasks asynchronously
            Subtask<T> subtask1 = scope.fork(new DelegatingSecurityContextCallable<>(task1, context));
            Subtask<T> subtask2 = scope.fork(new DelegatingSecurityContextCallable<>(task2, context));

            try {
                scope.join(); // Block until both complete or timeout
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new ParallelInterruptedException("One of the parallel tasks has been interrupted: ", e);
            }

            // Return the results in the exact requested order
            return List.of(subtask1.get(), subtask2.get());
        }
    }
}
