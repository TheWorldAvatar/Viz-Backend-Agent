package com.cmclinnovations.agent.service.core;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.StampedLock;
import java.util.function.Supplier;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.stereotype.Service;

@Service
public class ConcurrencyService {
  private final ConcurrentMap<String, StampedLock> lockMap = new ConcurrentHashMap<>();
  private static final Logger LOGGER = LogManager.getLogger(ConcurrencyService.class);

  /**
   * Constructs a new service with the following dependencies.
   */
  public ConcurrencyService() {
  }

  /**
   * Executes a function (Supplier) while holding an exclusive WRITE lock.
   * This is used for all add/update/delete operations.
   * 
   * @param resource The type of resource
   * @param writer   The lambda function (Supplier) containing the exclusive
   *                 business logic.
   * @return The result returned by the writer function.
   */
  public <T> T executeInWriteLock(String resource, Supplier<T> writer) {
    StampedLock lock = this.getLock(resource);
    long stamp = lock.writeLock();
    try {
      LOGGER.info("WRITE lock for {} acquired...", resource);
      return writer.get();

    } finally {
      lock.unlockWrite(stamp);
      LOGGER.info("WRITE lock for {} released...", resource);
    }
  }

  /**
   * Executes a function (Supplier) using an Optimistic Read Lock.
   * 
   * @param resource The type of resource
   * @param reader   The lambda function (Supplier) containing the read logic.
   * @return The result returned by the reader function, possibly after a retry.
   */
  public <T> T executeInOptimisticReadLock(String resource, Supplier<T> reader) {
    StampedLock lock = this.getLock(resource);
    long stamp = lock.tryOptimisticRead();
    T result = reader.get();
    // Validate if optimistic read is successful ie has a writer modified the data
    if (lock.validate(stamp)) {
      LOGGER.info("Sucesssfully read with OPTIMISTIC lock for {}", resource);
    } else {
      LOGGER.info("Failed to read with OPTIMISTIC lock for {}", resource);
      LOGGER.info("Falling back to READ lock...");
      stamp = lock.readLock();
      try {
        result = reader.get();
      } finally {
        lock.unlockRead(stamp);
      }
    }
    return result;
  }

  /**
   * Get or create a StampedLock for a specific resource type.
   * 
   * @param resource The type of resource
   * @return The StampedLock instance for that specific resource.
   */
  private StampedLock getLock(String resource) {
    return lockMap.computeIfAbsent(resource, k -> new StampedLock());
  }
}
