package com.cmclinnovations.agent;

import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.pagination.PaginationState;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.StringResource;

@RestController
@RequestMapping("/report")
public class ReportingController {
  private final ConcurrencyService concurrencyService;
  private final LifecycleTaskService lifecycleTaskService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final Logger LOGGER = LogManager.getLogger(ReportingController.class);

  public ReportingController(ConcurrencyService concurrencyService, LifecycleTaskService lifecycleTaskService,
      ResponseEntityBuilder responseEntityBuilder) {
    this.concurrencyService = concurrencyService;
    this.lifecycleTaskService = lifecycleTaskService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Retrieve the count of all closed tasks for the specified date range in UNIX
   * timestamp.
   */
  @GetMapping("/task/count")
  public ResponseEntity<StandardApiResponse<?>> getClosedTaskCount(
      @RequestParam Map<String, String> allRequestParams) {
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    LOGGER.info("Received request to retrieve number of scheduled contract task...");
    boolean isClosed = true;
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleTaskService.getOccurrenceCount(type, startTimestamp, endTimestamp, isClosed,
          allRequestParams);
    });
  }

  /**
   * Retrieve all closed tasks for the specified date range in UNIX timestamp.
   */
  @GetMapping("/task")
  public ResponseEntity<StandardApiResponse<?>> getAllClosedTasks(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve closed tasks for the specified dates...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    Integer page = Integer.valueOf(allRequestParams.remove(StringResource.PAGE_REQUEST_PARAM));
    Integer limit = Integer.valueOf(allRequestParams.remove(StringResource.LIMIT_REQUEST_PARAM));
    String sortBy = allRequestParams.getOrDefault(StringResource.SORT_BY_REQUEST_PARAM, StringResource.DEFAULT_SORT_BY);
    allRequestParams.remove(StringResource.SORT_BY_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.TASK_RESOURCE, () -> {
      return this.lifecycleTaskService.getOccurrences(startTimestamp, endTimestamp, type, true,
          new PaginationState(page, limit, sortBy + LifecycleResource.TASK_ID_SORT_BY_PARAMS, false, allRequestParams));
    });
  }

  /**
   * Retrieve the filter options for the completed or problematic tasks on the
   * specified date(s).
   */
  @GetMapping("/task/filter")
  public ResponseEntity<StandardApiResponse<?>> getClosedTaskFilters(
      @RequestParam Map<String, String> allRequestParams) {
    LOGGER.info("Received request to retrieve filter options for contracts in progress...");
    String type = allRequestParams.remove(StringResource.TYPE_REQUEST_PARAM);
    String field = allRequestParams.remove(StringResource.FIELD_REQUEST_PARAM);
    String search = allRequestParams.getOrDefault(StringResource.SEARCH_REQUEST_PARAM, "");
    allRequestParams.remove(StringResource.SEARCH_REQUEST_PARAM);
    String startTimestamp = allRequestParams.remove(StringResource.START_TIMESTAMP_REQUEST_PARAM);
    String endTimestamp = allRequestParams.remove(StringResource.END_TIMESTAMP_REQUEST_PARAM);
    return this.concurrencyService.executeInOptimisticReadLock(LifecycleResource.CONTRACT_KEY, () -> {
      List<String> options = this.lifecycleTaskService.getFilterOptions(type, field,
          search, startTimestamp, endTimestamp, true, allRequestParams);
      return this.responseEntityBuilder.success(options);
    });
  }
}