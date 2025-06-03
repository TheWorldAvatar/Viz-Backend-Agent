package com.cmclinnovations.agent.utils;

public class LocalisationResource {
  private static final String ERROR_PREFIX = "error.";
  private static final String SUCCESS_PREFIX = "success.";

  public static final String STATUS_KEY = "status";
  public static final String SUCCESS_ADD_KEY = SUCCESS_PREFIX + "add";
  public static final String SUCCESS_DELETE_KEY = SUCCESS_PREFIX + "delete";
  public static final String SUCCESS_UPDATE_KEY = SUCCESS_PREFIX + "update";
  public static final String ERROR_DELETE_KEY = ERROR_PREFIX + "delete";

  // Private constructor to prevent instantiation
  private LocalisationResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }
}