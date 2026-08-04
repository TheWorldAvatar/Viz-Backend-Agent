package com.cmclinnovations.agent.schedule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.service.application.LifecycleContractService;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;
import com.cmclinnovations.agent.service.core.AuthenticationService;

@Component
@ConditionalOnProperty(name = "tasks.enabled", havingValue = "true", matchIfMissing = false)
public class ScheduledTasks {
  private final AuthenticationService authService;
  private final LifecycleContractService lifecycleContractService;
  private final LifecycleTaskService lifecycleTaskService;

  private static final Logger LOGGER = LogManager.getLogger(ScheduledTasks.class);

  public ScheduledTasks(AuthenticationService authService, LifecycleContractService lifecycleService,
      LifecycleTaskService lifecycleTaskService) {
    this.authService = authService;
    this.lifecycleContractService = lifecycleService;
    this.lifecycleTaskService = lifecycleTaskService;
  }

  @Scheduled(cron = "0 0 0 * * *")
  public void runDaily() {
    LOGGER.info("Performing daily cron job...");
    try {
      this.genOrderActiveContracts();
    } catch (Exception e) {
      LOGGER.error("Failed to generate new active tasks in daily cron job", e);
    }

    try {
      this.dischargeExpiredContracts();
    } catch (Exception e) {
      LOGGER.error("Failed to discharge expired contracts in daily cron job", e);
    }
    LOGGER.info("Daily cron job has completed...");
  }

  private void genOrderActiveContracts() {
    try {
      this.authService.setInternalAuthentication();
      this.lifecycleTaskService.genOrderActiveContracts();
    } finally {
      SecurityContextHolder.clearContext();
    }
  }

  private void dischargeExpiredContracts() {
    LOGGER.info("Discharging the active contracts that have expired today...");
    this.lifecycleContractService.dischargeExpiredContracts();
    LOGGER.info("Scheduled task for service discharge has been completed successfully!");
  }
}
