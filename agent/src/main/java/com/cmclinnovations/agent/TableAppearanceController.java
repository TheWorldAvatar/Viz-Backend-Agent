package com.cmclinnovations.agent;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.service.application.TableAppearanceService;
import com.cmclinnovations.agent.service.core.ConcurrencyService;
import com.cmclinnovations.agent.utils.TableAppearanceResource;

/**
 * Routes for the shared appearance settings of tables in the viz frontend, such
 * as colour profiles, colsumn settings, and padding. All routes are prefixed with `/table-appearance`.
 */
@RestController
@RequestMapping("/table-appearance")
public class TableAppearanceController {
  private final ConcurrencyService concurrencyService;
  private final TableAppearanceService tableAppearanceService;

  private static final Logger LOGGER = LogManager.getLogger(TableAppearanceController.class);

  public TableAppearanceController(ConcurrencyService concurrencyService,
      TableAppearanceService tableAppearanceService) {
    this.concurrencyService = concurrencyService;
    this.tableAppearanceService = tableAppearanceService;
  }

  /**
   * Retrieves all colour profiles associated with the target table.
   */
  @GetMapping("/colour-profiles")
  public ResponseEntity<StandardApiResponse<?>> getColourProfiles(@RequestParam(required = false) String table) {
    LOGGER.info("Received request to get the colour profiles for a table...");
    // Table is verified in the service to return a bad request when missing
    return this.concurrencyService.executeInOptimisticReadLock(TableAppearanceResource.COLOUR_PROFILE_RESOURCE,
        () -> this.tableAppearanceService.getColourProfiles(table));
  }
}
