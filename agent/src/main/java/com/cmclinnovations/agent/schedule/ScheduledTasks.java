package com.cmclinnovations.agent.schedule;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.service.application.LifecycleContractService;
import com.cmclinnovations.agent.service.application.LifecycleTaskService;

@Component
@ConditionalOnProperty(name = "tasks.enabled", havingValue = "true", matchIfMissing = false)
public class ScheduledTasks {
  private final LifecycleContractService lifecycleContractService;
  private final LifecycleTaskService lifecycleTaskService;

  private static final Logger LOGGER = LogManager.getLogger(ScheduledTasks.class);

  public ScheduledTasks(LifecycleContractService lifecycleService, LifecycleTaskService lifecycleTaskService) {
    this.lifecycleContractService = lifecycleService;
    this.lifecycleTaskService = lifecycleTaskService;
  }

  @Scheduled(cron = "0 0 6 * * *")
  public void dischargeExpiredContracts() {
    LOGGER.info("Discharging the active contracts that have expired today...");
    this.lifecycleContractService.dischargeExpiredContracts();
    LOGGER.info("Scheduled task for service discharge has been completed successfully!");
  }

  public void genOrderActiveContracts() {
    this.lifecycleTaskService.genOrderActiveContracts();
  }
}
