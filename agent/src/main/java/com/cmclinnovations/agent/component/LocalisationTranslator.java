package com.cmclinnovations.agent.component;

import java.util.Locale;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Component;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;

@Component
public class LocalisationTranslator {
  private static MessageSource MESSAGE_SOURCE;

  /**
   * Constructs a service for retrieving localised messages based on the language
   * preferences in the request.
   */
  LocalisationTranslator(MessageSource messageSource) {
    MESSAGE_SOURCE = messageSource;
  }

  /**
   * Retrieves a localised message for the given key. Users may provide additional
   * arguments from replacements.
   *
   * @param key             The message key (e.g., "greeting.message").
   * @param replacementArgs an array of arguments that will be filled in for
   *                        params within the message (params look like "{0}",
   *                        "{1,date}", "{2,time}" within a message), or
   *                        {@code null} if none.
   */
  public static String getMessage(String key, Object... replacementArgs) {
    // Locale is resolved from the current request context automatically
    Locale currentLocale = LocaleContextHolder.getLocale();
    return MESSAGE_SOURCE.getMessage(key, replacementArgs, currentLocale);
  }

  /**
   * Retrieves the localised event status.
   *
   * @param event The event of interest.
   */
  public static String getEvent(String event) {
    return switch (event) {
      case LifecycleResource.EVENT_INCIDENT_REPORT:
        yield LocalisationResource.EVENT_STATUS_ISSUE_KEY;
      case LifecycleResource.EVENT_CANCELLATION:
        yield LocalisationResource.EVENT_STATUS_CANCELLED_KEY;
      case LifecycleResource.EVENT_DELIVERY + ";" + LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS:
        yield LocalisationResource.EVENT_STATUS_COMPLETED_KEY;
      case LifecycleResource.EVENT_DISPATCH:
      case LifecycleResource.EVENT_DELIVERY + ";" + LifecycleResource.EVENT_PENDING_STATUS:
        yield LocalisationResource.EVENT_STATUS_ASSIGNED_KEY;
      case LifecycleResource.EVENT_ORDER_RECEIVED:
        yield LocalisationResource.EVENT_STATUS_NEW_KEY;
      default:
        throw new IllegalArgumentException("Unknown event: " + event);
    };
  }

  /**
   * Retrieves the event from the localised event status.
   *
   * @param eventStatusLocalisedKey The localised key of the event status.
   */
  public static String getEventFromLocalisedEventKey(String eventStatusLocalisedKey) {
    return switch (eventStatusLocalisedKey) {
      case LocalisationResource.EVENT_STATUS_ISSUE_KEY:
        yield Rdf.literalOf(LifecycleResource.EVENT_INCIDENT_REPORT).getQueryString();
      case LocalisationResource.EVENT_STATUS_CANCELLED_KEY:
        yield Rdf.literalOf(LifecycleResource.EVENT_CANCELLATION).getQueryString();
      case LocalisationResource.EVENT_STATUS_COMPLETED_KEY:
        yield Rdf.literalOf(
            LifecycleResource.EVENT_DELIVERY + ";" + LifecycleResource.COMPLETION_EVENT_COMPLETED_STATUS)
            .getQueryString();
      case LocalisationResource.EVENT_STATUS_ASSIGNED_KEY:
        // The white space below is NOT a delimiter and is intended to separate the
        // potential assigned key values
        yield Rdf.literalOf(LifecycleResource.EVENT_DELIVERY + ";" + LifecycleResource.EVENT_PENDING_STATUS)
            .getQueryString() + " " + Rdf.literalOf(LifecycleResource.EVENT_DISPATCH).getQueryString();
      case LocalisationResource.EVENT_STATUS_NEW_KEY:
        yield Rdf.literalOf(LifecycleResource.EVENT_ORDER_RECEIVED).getQueryString();
      default:
        throw new IllegalArgumentException("Unknown event key: " + eventStatusLocalisedKey);
    };
  }

  /**
   * Retrieve the schedule type based on the recurrence interval.
   * 
   * @param recurrence The recurrence value.
   */
  public static String getScheduleTypeFromRecurrence(String recurrence) {
    return switch (recurrence) {
      case null -> getMessage(LocalisationResource.LABEL_PERPETUAL_SERVICE_KEY);
      case "" -> getMessage(LocalisationResource.LABEL_PERPETUAL_SERVICE_KEY);
      case LifecycleResource.RECURRENCE_AD_HOC_TASK -> getMessage(LocalisationResource.LABEL_AD_HOC_SERVICE_KEY);
      case LifecycleResource.RECURRENCE_DAILY_TASK -> getMessage(LocalisationResource.LABEL_SINGLE_SERVICE_KEY);
      case LifecycleResource.RECURRENCE_ALT_DAY_TASK ->
        getMessage(LocalisationResource.LABEL_ALTERNATE_DAY_SERVICE_KEY);
      default -> getMessage(LocalisationResource.LABEL_REGULAR_SERVICE_KEY);
    };
  }
}
