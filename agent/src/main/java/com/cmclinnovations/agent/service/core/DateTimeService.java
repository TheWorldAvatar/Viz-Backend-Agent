package com.cmclinnovations.agent.service.core;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.SparqlResponseField;
import com.cmclinnovations.agent.utils.LocalisationResource;

@Service
public class DateTimeService {
  private final DateTimeFormatter formatter;
  private final DateTimeFormatter timeFormatter;
  private static final Pattern WEEKLY_INTERVAL_PATERN = Pattern.compile("P(\\d+)D");

  /**
   * Constructs a new service with the following dependencies.
   */
  public DateTimeService() {
    this.formatter = DateTimeFormatter.ISO_LOCAL_DATE;
    this.timeFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
  }

  /**
   * Checks if the input date is a future date after today.
   * 
   * @param input The target date in YYYY-MM-DD format.
   */
  public boolean isFutureDate(String input) {
    return isFutureDate(input, this.getCurrentDate());
  }

  /**
   * Checks if the input date occurs after the target date.
   * 
   * @param inputDate  The input date in YYYY-MM-DD format, which is intended to
   *                   be compared to the benchmark.
   * @param targetDate The target date in YYYY-MM-DD format, which acts as the
   *                   benchmark of comparison.
   */
  public boolean isFutureDate(String inputDate, String targetDate) {
    return this.parseDate(inputDate).isAfter(this.parseDate(targetDate));
  }

  /**
   * Get current date in YYYY-MM-DD format.
   */
  public String getCurrentDate() {
    // Define the date format
    return LocalDate.now().format(this.formatter);
  }

  /**
   * Get current day of the week as full lowercase english.
   */
  public String getCurrentDayOfWeek() {
    return LocalDate.now().getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase();
  }

  /**
   * Get current date time in YYYY-MM-DDTHH:MM:SS format.
   */
  public String getCurrentDateTime() {
    return LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
  }

  /**
   * Get next working date (excluding weekend) in YYYY-MM-DD time format.
   */
  public String getNextWorkingDate() {
    LocalDate today = LocalDate.now();
    long daysToAdd = switch (today.getDayOfWeek()) {
      case DayOfWeek.FRIDAY -> 3;
      case DayOfWeek.SATURDAY -> 2;
      default -> 1;
    };
    return today.plusDays(daysToAdd).atStartOfDay().format(this.timeFormatter);
  }

  /**
   * Parses the date input string into a LocalDate object with the specified
   * formatter.
   * 
   * @param date The date input string.
   */
  public LocalDate parseDate(String date) {
    return LocalDate.parse(date, this.formatter);
  }

  /**
   * Retrieve the date component of the date time string.
   * 
   * @param dateTime The input in date time format.
   */
  public String getDateFromDateTime(String dateTime) {
    return LocalDate.parse(dateTime, this.timeFormatter).format(this.formatter);
  }

  /**
   * Retrieve the date as a date time string at start of day.
   * 
   * @param date The input in YYYY-MM-DD format.
   */
  public String getDateTimeFromDate(String date) {
    return this.parseDate(date).atStartOfDay().format(this.timeFormatter);
  }

  /**
   * Retrieve the date as a string in the YYYY-MM-DD format from the timestamp
   * input.
   * 
   * @param timestamp The timestamp input in UNIX seconds.
   */
  public String getDateFromTimestamp(String timestamp) {
    long parsedTimestamp = Long.parseLong(timestamp);
    // Convert Unix timestamp (seconds) to LocalDate
    return Instant.ofEpochSecond(parsedTimestamp)
        .atZone(ZoneId.systemDefault()) // Adjust to the system default time zone
        .toLocalDate()
        .format(this.formatter);
  }

  /**
   * Retrieve the weekly interval from the recurrence input.
   * 
   * @param recurrence The recurrence interval in P*D format, eg P7D, P14D.
   */
  public int getWeeklyInterval(String recurrence) {
    // Regex to extract numbers from a string
    Matcher matcher = WEEKLY_INTERVAL_PATERN.matcher(recurrence);
    if (matcher.matches()) {
      int number = Integer.parseInt(matcher.group(1));
      return number / 7;
    } else {
      throw new IllegalArgumentException("Input format is incorrect: " + recurrence);
    }
  }

  /**
   * Get start and end date from the input timestamps.
   * 
   * @param startTimestamp Start timestamp in UNIX format.
   * @param endTimestamp   End timestamp in UNIX format.
   * @param isClosed       Indicates whether to retrieve closed tasks.
   */
  public String[] getStartEndDate(String startTimestamp, String endTimestamp, boolean isClosed) {
    String startDate = "";
    String endDate = "";
    if (startTimestamp == null && endTimestamp == null) {
      endDate = this.getCurrentDate();
    } else {
      startDate = this.getDateFromTimestamp(startTimestamp);
      endDate = this.getDateFromTimestamp(endTimestamp);
      // Verify that the end date occurs after the start date
      if (this.isFutureDate(startDate, endDate)) {
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_CHRONOLOGY_KEY));
      }
      // Users can only view upcoming scheduled tasks after today
      if (!isClosed && !this.isFutureDate(startDate)) {
        throw new IllegalArgumentException(
            LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_DATE_SCHEDULED_PRESENT_KEY));
      }
    }
    return new String[] { startDate, endDate };
  }

  /**
   * Retrieve dates of occurrences within the period based on the specified
   * interval.
   * 
   * @param startDate    The start date in YYYY-MM-DD format.
   * @param endDateInput The end date in YYYY-MM-DD format.
   * @param interval     The interval of occurrences in days.
   */
  public Queue<String> getOccurrenceDates(String startDate, String endDateInput, int interval) {
    Queue<String> occurrenceDates = new ArrayDeque<>();
    LocalDate currentDate = this.parseDate(startDate);
    LocalDate endDate = this.parseDate(endDateInput);
    // Loop to calculate all occurrence dates
    while (!currentDate.isAfter(endDate)) {
      String currentDateString = currentDate.atStartOfDay().format(this.timeFormatter);
      occurrenceDates.offer(currentDateString);
      currentDate = currentDate.plusDays(interval); // Increment by the interval
    }
    return occurrenceDates;
  }

  /**
   * Retrieve dates of occurrences within the period based on the days of the week
   * scheduled and the weekly interval.
   * 
   * @param startDateInput The start date in YYYY-MM-DD format.
   * @param endDateInput   The end date in YYYY-MM-DD format.
   * @param bindings       The result set containing the days of week.
   * @param weekInterval   The interval of occurrences in weeks. 1: occur every
   *                       week, 2: occur every 2 weeks
   */
  public Queue<String> getOccurrenceDates(String startDateInput, String endDateInput, SparqlBinding bindings,
      int weekInterval) {
    Queue<String> occurrenceDates = new ArrayDeque<>();
    LocalDate startDate = this.parseDate(startDateInput);
    LocalDate endDate = this.parseDate(endDateInput);
    LocalDate currentDate = this.parseDate(startDateInput);
    // Retrieve scheduled days of week for occurrence
    Set<DayOfWeek> daysOfWeek = this.getScheduledDaysOfWeek(bindings);

    // Loop to calculate all occurrence dates
    while (!currentDate.isAfter(endDate)) {
      // For each week, check all specified days of the week
      for (DayOfWeek dayOfWeek : daysOfWeek) {
        LocalDate occurrenceDate = currentDate.with(dayOfWeek);

        // Ensure the calculated date is within the range [startDate, endDate]
        if (!occurrenceDate.isBefore(startDate) && !occurrenceDate.isAfter(endDate)) {
          String occurrenceDateString = occurrenceDate.atStartOfDay().format(this.timeFormatter);
          occurrenceDates.offer(occurrenceDateString);
        }
      }
      currentDate = currentDate.plusWeeks(weekInterval); // Increment by the interval
    }
    return occurrenceDates;
  }

  /**
   * Retrieve the scheduled days of the week from the SPARQL binding results.
   * 
   * @param bindings The results for retrieval.
   */
  private Set<DayOfWeek> getScheduledDaysOfWeek(SparqlBinding bindings) {
    Map<String, DayOfWeek> dayMapping = Map.of(
        "monday", DayOfWeek.MONDAY,
        "tuesday", DayOfWeek.TUESDAY,
        "wednesday", DayOfWeek.WEDNESDAY,
        "thursday", DayOfWeek.THURSDAY,
        "friday", DayOfWeek.FRIDAY,
        "saturday", DayOfWeek.SATURDAY,
        "sunday", DayOfWeek.SUNDAY);
    Set<DayOfWeek> daysOfWeek = new HashSet<>();
    for (Map.Entry<String, DayOfWeek> entry : dayMapping.entrySet()) {
      if (!bindings.getFieldValue(entry.getKey()).isEmpty()) {
        daysOfWeek.add(entry.getValue());
      }
    }
    return daysOfWeek;
  }

  /**
   * Retrieves a list of lowercase day keys (e.g., "monday") from the provided
   * schedule
   * map that satisfy a specific validation criteria.
   * The criteria is: the map must contain a key matching the lowercase day name,
   * and the value of that SparqlResponseField must exactly match the proper-cased
   * display name of the day (e.g., "Monday").
   *
   * @param schedule The map containing lowercase day keys and their
   *                 SparqlResponseField values.
   * @return A map of weekday name as keys and Boolean as values.
   */

  public Map<String, Boolean> getRecurringWeekday(Map<String, SparqlResponseField> schedule) {

    // Check for null/empty map input defensively
    if (schedule == null || schedule.isEmpty()) {
      return Collections.emptyMap(); // Return an empty map instead of an empty list
    }

    return Arrays.stream(DayOfWeek.values())
        // 2. Collect the results into a Map<String, Boolean>
        .collect(Collectors.toMap(
            // Key Mapper: The weekday's lowercase name (e.g., "monday")
            day -> day.getDisplayName(TextStyle.FULL, Locale.ENGLISH).toLowerCase(),
            // Value Mapper: The boolean true
            day -> {
              // Get the full proper case name (e.g., "Monday")
              String properCaseName = day.getDisplayName(TextStyle.FULL, Locale.ENGLISH);
              // Get the lowercase key expected in the input map (e.g., "monday")
              String lowercaseKey = properCaseName.toLowerCase();

              // Look up the value in the schedule map
              SparqlResponseField field = schedule.get(lowercaseKey);

              // Check if the key exists AND the value of the field matches the proper case
              // name
              return field != null && field.value() != null && field.value().equals(properCaseName);
            }));
  }
}
