package com.cmclinnovations.agent.service.core;

import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;

@Service
public class LocalisationService {
  private final MessageSource messageSource;

  /**
   * Constructs a service for retrieving localised messages based on the language
   * preferences in the request.
   */
  public LocalisationService(MessageSource messageSource) {
    this.messageSource = messageSource;
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
  public String getMessage(String key, Object... replacementArgs) {
    // Locale is resolved from the current request context automatically
    Locale currentLocale = LocaleContextHolder.getLocale();
    return messageSource.getMessage(key, replacementArgs, currentLocale);
  }

  /**
   * Retrieves the localised event status.
   *
   * @param event The event of interest.
   */
  public String getEvent(String event) {
    String localisedKey;
    switch (event) {
      case LifecycleResource.EVENT_INCIDENT_REPORT:
        localisedKey = LocalisationResource.STATUS_REPORT_KEY;
        break;
      case LifecycleResource.EVENT_CANCELLATION:
        localisedKey = LocalisationResource.STATUS_CANCEL_KEY;
        break;
      case LifecycleResource.EVENT_DELIVERY:
        localisedKey = LocalisationResource.STATUS_COMPLETED_KEY;
        break;
      case LifecycleResource.EVENT_DISPATCH:
        localisedKey = LocalisationResource.STATUS_DISPATCH_KEY;
        break;
      case LifecycleResource.EVENT_ORDER_RECEIVED:
        localisedKey = LocalisationResource.STATUS_ORDER_KEY;
        break;
      default:
        throw new IllegalArgumentException("Unknown event: " + event);
    }
    return this.getMessage(localisedKey);
  }
}
