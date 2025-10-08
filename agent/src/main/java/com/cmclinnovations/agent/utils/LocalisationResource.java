package com.cmclinnovations.agent.utils;

public class LocalisationResource {
  private static final String ERROR_PREFIX = "error.";
  private static final String MESSAGE_PREFIX = "message.";
  private static final String SUCCESS_PREFIX = "success.";
  private static final String SUCCESS_CONTRACT_PREFIX = SUCCESS_PREFIX + "contract.";
  private static final String LABEL_PREFIX = "label.";
  private static final String VAR_PREFIX = "variable.";

  public static final String STATUS_KEY = "status";
  public static final String MESSAGE_NO_ADDRESS_KEY = MESSAGE_PREFIX + "noaddress";
  public static final String MESSAGE_NO_COORDINATE_KEY = MESSAGE_PREFIX + "nocoordinate";
  public static final String MESSAGE_DUPLICATE_APPROVAL_KEY = MESSAGE_PREFIX + "duplicate.approval";
  public static final String SUCCESS_ADD_KEY = SUCCESS_PREFIX + "add";
  public static final String SUCCESS_ADD_REPORT_KEY = SUCCESS_PREFIX + "add.report";
  public static final String SUCCESS_CONTRACT_DRAFT_KEY = SUCCESS_CONTRACT_PREFIX + "draft";
  public static final String SUCCESS_SCHEDULE_DRAFT_KEY = SUCCESS_PREFIX + "schedule.draft";
  public static final String SUCCESS_CONTRACT_DRAFT_UPDATE_KEY = SUCCESS_CONTRACT_DRAFT_KEY + ".update";
  public static final String SUCCESS_SCHEDULE_DRAFT_UPDATE_KEY = SUCCESS_SCHEDULE_DRAFT_KEY + ".update";
  public static final String SUCCESS_CONTRACT_APPROVED_KEY = SUCCESS_CONTRACT_PREFIX + "approved";
  public static final String SUCCESS_CONTRACT_RESCIND_KEY = SUCCESS_CONTRACT_PREFIX + "rescind";
  public static final String SUCCESS_CONTRACT_TERMINATE_KEY = SUCCESS_CONTRACT_PREFIX + "terminate";
  public static final String SUCCESS_CONTRACT_TASK_ASSIGN_KEY = SUCCESS_CONTRACT_PREFIX + "task.assign";
  public static final String SUCCESS_CONTRACT_TASK_COMPLETE_KEY = SUCCESS_CONTRACT_PREFIX + "task.complete";
  public static final String SUCCESS_CONTRACT_TASK_CANCEL_KEY = SUCCESS_CONTRACT_PREFIX + "task.cancel";
  public static final String SUCCESS_CONTRACT_TASK_REPORT_KEY = SUCCESS_CONTRACT_PREFIX + "task.report";
  public static final String SUCCESS_DELETE_KEY = SUCCESS_PREFIX + "delete";
  public static final String SUCCESS_UPDATE_KEY = SUCCESS_PREFIX + "update";
  public static final String ERROR_ADD_KEY = ERROR_PREFIX + "add";
  public static final String ERROR_CONTACT_KEY = ERROR_PREFIX + "contact";
  public static final String ERROR_DELETE_KEY = ERROR_PREFIX + "delete";
  public static final String ERROR_MISSING_FIELD_KEY = ERROR_PREFIX + "missing.field";
  public static final String ERROR_MISSING_FILE_KEY = ERROR_PREFIX + "missing.file";
  public static final String ERROR_ORDERS_PARTIAL_KEY = ERROR_PREFIX + "orders.partial";
  public static final String ERROR_INVALID_DATE_CHRONOLOGY_KEY = ERROR_PREFIX + "invalid.date.chronology";
  public static final String ERROR_INVALID_DATE_SCHEDULED_PRESENT_KEY = ERROR_PREFIX + "invalid.date.scheduledpresent";
  public static final String ERROR_INVALID_DATE_CANCEL_KEY = ERROR_PREFIX + "invalid.date.cancel";
  public static final String ERROR_INVALID_DATE_REPORT_KEY = ERROR_PREFIX + "invalid.date.report";
  public static final String ERROR_INVALID_EVENT_TYPE_KEY = ERROR_PREFIX + "invalid.event.type";
  public static final String ERROR_INVALID_GEOCODE_PARAMS_KEY = ERROR_PREFIX + "invalid.geocoding";
  public static final String ERROR_INVALID_INSTANCE_KEY = ERROR_PREFIX + "invalid.instance";
  public static final String ERROR_INVALID_MULTIPLE_INSTANCE_KEY = ERROR_PREFIX + "invalid.multiple.instance";
  public static final String ERROR_INVALID_ROUTE_KEY = ERROR_PREFIX + "invalid.route";
  public static final String ERROR_INVALID_SERVER_KEY = ERROR_PREFIX + "invalid.server";
  public static final String LABEL_SINGLE_SERVICE_KEY = LABEL_PREFIX + "single.service";
  public static final String LABEL_ALTERNATE_DAY_SERVICE_KEY = LABEL_PREFIX + "alt.day.service";
  public static final String LABEL_PERPETUAL_SERVICE_KEY = LABEL_PREFIX + "perpetual.service";
  public static final String LABEL_REGULAR_SERVICE_KEY = LABEL_PREFIX + "regular.service";
  public static final String VAR_SCHEDULE_TYPE_KEY = VAR_PREFIX + "schedule.type";
  public static final String VAR_STATUS_KEY = VAR_PREFIX + STATUS_KEY;

  // Private constructor to prevent instantiation
  private LocalisationResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }
}