package com.cmclinnovations.agent.utils;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import com.cmclinnovations.agent.model.type.LifecycleEventType;
import com.cmclinnovations.agent.service.FileService;

public class LifecycleResource {
  public static final String LIFECYCLE_RESOURCE = "lifecycle";
  public static final String SCHEDULE_RESOURCE = "schedule";
  public static final String OCCURRENCE_INSTANT_RESOURCE = "occurrence instant";

  public static final String CONTRACT_KEY = "contract";
  public static final String CURRENT_DATE_KEY = "current date";
  public static final String DATE_KEY = "date";
  public static final String EVENT_KEY = "event";

  // Private constructor to prevent instantiation
  private LifecycleResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }

  /**
   * Get current date in YYYY-MM-DD format.
   */
  public static String getCurrentDate() {
    // Define the date format
    DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    return LocalDate.now().format(formatter);
  }

  /**
   * Check if the date input is either before and after the current date.
   * 
   * @param dateParam   The date parameter for checking.
   * @param checkBefore Indicator if the method should check if the date is before
   *                    the current date. Use false to check if date is after the
   *                    current date.
   */
  public static boolean checkDate(String dateParam, boolean checkBefore) {
    // Parse input date
    LocalDate inputDate = LocalDate.parse(dateParam);
    LocalDate currentDate = LocalDate.now();

    if (checkBefore) {
      return inputDate.isBefore(currentDate); // Check if the date is before today
    } else {
      return inputDate.isAfter(currentDate); // Check if the date is after today
    }
  }

  /**
   * Retrieve the event class associated with the event type.
   * 
   * @param eventType The target event type.
   */
  public static String getEventClass(LifecycleEventType eventType) {
    switch (eventType) {
      case LifecycleEventType.APPROVED:
        return "https://www.theworldavatar.com/kg/ontoservice/ContractApproval";
      case LifecycleEventType.SERVICE_EXECUTION:
        return "https://www.theworldavatar.com/kg/ontoservice/ServiceDeliveryEvent";
      case LifecycleEventType.SERVICE_CANCELLATION:
        return "https://www.theworldavatar.com/kg/ontoservice/TerminatedServiceEvent";
      case LifecycleEventType.SERVICE_MISS_REPORT:
        return "https://www.theworldavatar.com/kg/ontoservice/MissedServiceEvent";
      default:
        throw new IllegalArgumentException("Invalid event type!");
    }
  }

  /**
   * Retrieve the lifecycle resource file path associated with the resource ID.
   * 
   * @param resourceID The identifier for the resource.
   */
  public static String getLifecycleResourceFilePath(String resourceID) {
    switch (resourceID) {
      case LifecycleResource.LIFECYCLE_RESOURCE:
        return FileService.LIFECYCLE_JSON_LD_RESOURCE;
      case LifecycleResource.OCCURRENCE_INSTANT_RESOURCE:
        return FileService.OCCURRENCE_INSTANT_JSON_LD_RESOURCE;
      case LifecycleResource.SCHEDULE_RESOURCE:
        return FileService.SCHEDULE_JSON_LD_RESOURCE;
      default:
        return null;
    }
  }
}