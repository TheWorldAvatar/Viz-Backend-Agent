package com.cmclinnovations.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.application.ViewPresentationService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.QueryResource;

/**
 * Routes for the presentation settings for any views
 */
@RestController
@RequestMapping("/views")
public class ViewPresentationController {
  private final ConcurrencyService concurrencyService;
  private final ViewPresentationService viewPresentationService;

  private static final Logger LOGGER = LogManager.getLogger(ViewPresentationController.class);

  public ViewPresentationController(ConcurrencyService concurrencyService,
      ViewPresentationService viewPresentationService) {
    this.concurrencyService = concurrencyService;
    this.viewPresentationService = viewPresentationService;
  }

  /**
   * Retrieves all the cell formatting rules for a specific resource.
   */
  @GetMapping("/rules/{id}")
  public ResponseEntity<StandardApiResponse<?>> getCellFormattingRules(@PathVariable String id) {
    LOGGER.info("Received request to get the cell formatting rules...");
    return this.concurrencyService.executeInOptimisticReadLock(QueryResource.VIEWS_CELL_RULES_RESOURCE,
        () -> this.viewPresentationService.getCellFormattingRules(id));
  }
}
