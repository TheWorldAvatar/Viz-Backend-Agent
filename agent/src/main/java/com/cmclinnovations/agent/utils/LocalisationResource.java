package com.cmclinnovations.agent.utils;

public class LocalisationResource {
  private static final String ERROR_PREFIX = "error.";
  private static final String SUCCESS_PREFIX = "success.";
  private static final String STATUS_PREFIX = "status.";
  private static final String VAR_PREFIX = "variable.";

  public static final String STATUS_KEY = "status";
  public static final String STATUS_CANCEL_KEY = STATUS_PREFIX + "cancel";
  public static final String STATUS_COMPLETED_KEY = STATUS_PREFIX + "completed";
  public static final String STATUS_DISPATCH_KEY = STATUS_PREFIX + "dispatch";
  public static final String STATUS_ORDER_KEY = STATUS_PREFIX + "new.order";
  public static final String STATUS_REPORT_KEY = STATUS_PREFIX + "report";
  public static final String SUCCESS_ADD_KEY = SUCCESS_PREFIX + "add";
  public static final String SUCCESS_DELETE_KEY = SUCCESS_PREFIX + "delete";
  public static final String SUCCESS_UPDATE_KEY = SUCCESS_PREFIX + "update";
  public static final String ERROR_CONTACT_KEY = ERROR_PREFIX + "contact";
  public static final String ERROR_DELETE_KEY = ERROR_PREFIX + "delete";
  public static final String ERROR_TIMESTAMP_KEY = ERROR_PREFIX + "timestamp";
  public static final String VAR_STATUS_KEY = VAR_PREFIX + STATUS_KEY;

  // Private constructor to prevent instantiation
  private LocalisationResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }
}