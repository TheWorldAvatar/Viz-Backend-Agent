package com.cmclinnovations.agent.service.core;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.exception.InvalidRouteException;
import com.cmclinnovations.agent.utils.BillingResource;
import com.cmclinnovations.agent.utils.LifecycleResource;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class FileService {
  private final ObjectMapper objectMapper;
  private final ResourceLoader resourceLoader;

  public static final String SPRING_FILE_PATH_PREFIX = "file:/";
  private static final String RESOURCE_DIR = "usr/local/tomcat/resources/";
  public static final String APPLICATION_FORM_RESOURCE = RESOURCE_DIR + "application-form.json";
  public static final String APPLICATION_SERVICE_RESOURCE = RESOURCE_DIR + "application-service.json";
  public static final String JSON_LD_DIR = RESOURCE_DIR + "jsonld/";

  private static final String CLASS_PATH_DIR = "classpath:";
  private static final String QUERY_DIR = CLASS_PATH_DIR + "query/";
  private static final String QUERY_CONSTR_DIR = QUERY_DIR + "construct/";
  private static final String QUERY_GET_DIR = QUERY_DIR + "get/";
  private static final String QUERY_GET_BILLING_DIR = QUERY_GET_DIR + "billing/";
  private static final String QUERY_GET_LIFECYCLE_DIR = QUERY_GET_DIR + "lifecycle/";
  public static final String FORM_QUERY_RESOURCE = QUERY_CONSTR_DIR + "form.sparql";
  public static final String SHACL_RULE_QUERY_RESOURCE = QUERY_CONSTR_DIR + "shacl_rule.sparql";
  public static final String ENDPOINT_QUERY_RESOURCE = QUERY_GET_DIR + "endpoint.sparql";
  public static final String INSTANCE_QUERY_RESOURCE = QUERY_GET_DIR + "instance.sparql";
  public static final String SHACL_PATH_QUERY_RESOURCE = QUERY_GET_DIR + "property_path.sparql";
  public static final String SHACL_PATH_LABEL_QUERY_RESOURCE = QUERY_GET_DIR + "property_path_label.sparql";
  public static final String SHACL_PROPERTY_OPTIONAL_RESOURCE = QUERY_GET_DIR + "property_optional.sparql";
  public static final String CHANGELOG_RESOURCE = QUERY_GET_DIR + "changelog/changes.sparql";
  public static final String CHANGELOG_TASK_RESOURCE = QUERY_GET_DIR + "changelog/task_changes.sparql";

  public static final String LIFECYCLE_JSON_LD_RESOURCE = CLASS_PATH_DIR + "jsonld/lifecycle.jsonld";
  public static final String CUSTOMER_ACCOUNT_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/accounts/customer_account.jsonld";
  public static final String ACCOUNT_PRICING_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/accounts/account_pricing.jsonld";
  public static final String TRANSACTION_RECORD_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/accounts/transaction_record.jsonld";
  public static final String TRANSACTION_INVOICE_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/accounts/individual_transaction.jsonld";
  public static final String OCCURRENCE_INSTANT_JSON_LD_RESOURCE = CLASS_PATH_DIR + "jsonld/occurrence_instant.jsonld";
  public static final String OCCURRENCE_LINK_JSON_LD_RESOURCE = CLASS_PATH_DIR + "jsonld/occurrence_link.jsonld";
  public static final String SCHEDULE_JSON_LD_RESOURCE = CLASS_PATH_DIR + "jsonld/schedule.jsonld";
  public static final String FIXED_DATE_SCHEDULE_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/fixed_date_schedule.jsonld";
  public static final String HISTORY_ACTIVITY_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/history/activity.jsonld";
  public static final String HISTORY_AGENT_JSON_LD_RESOURCE = CLASS_PATH_DIR
      + "jsonld/history/agent.jsonld";

  public static final String ACCOUNT_AGREEMENT_QUERY_RESOURCE = QUERY_GET_BILLING_DIR + "account_agreement.sparql";
  public static final String ACCOUNT_PRICING_QUERY_RESOURCE = QUERY_GET_BILLING_DIR + "account_pricing.sparql";
  public static final String ACCOUNT_BILL_QUERY_RESOURCE = QUERY_GET_BILLING_DIR + "bill.sparql";
  public static final String CONTRACT_PRICING_QUERY_RESOURCE = QUERY_GET_BILLING_DIR + "contract_pricing.sparql";
  public static final String CONTRACT_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "contract.sparql";
  public static final String CONTRACT_STATUS_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "contract_status.sparql";
  public static final String CONTRACT_STAGE_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "contract_stage.sparql";
  public static final String CONTRACT_EVENT_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "contract_event.sparql";
  public static final String CONTRACT_PREV_EVENT_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR
      + "contract_prev_event.sparql";
  public static final String CONTRACT_SCHEDULE_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "schedule.sparql";
  public static final String FIXED_DATE_CONTRACT_SCHEDULE_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR
      + "fixed_date_schedule.sparql";
  public static final String TASK_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "task.sparql";
  public static final String RESCHEDULE_QUERY_RESOURCE = QUERY_GET_LIFECYCLE_DIR + "reschedule.sparql";

  public static final String REPLACEMENT_TARGET = "\\[target\\]";
  public static final String REPLACEMENT_SHAPE = "[shape]";
  public static final String REPLACEMENT_PATH = "[path]";
  public static final String REPLACEMENT_FILTER = "[filter]";

  private static final Logger LOGGER = LogManager.getLogger(FileService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param resourceLoader ResourceLoader instance for loading file resources.
   * @param objectMapper   The JSON object mapper.
   */
  public FileService(ResourceLoader resourceLoader, ObjectMapper objectMapper) {
    this.resourceLoader = resourceLoader;
    this.objectMapper = objectMapper;
  }

  /**
   * Retrieve the target file contents with replacement for [target].
   * 
   * @param resourceFilePath File path to resource.
   * @param replacements     A variable list values to replace [target] with.
   */
  public String getContentsWithReplacement(String resourceFilePath, String... replacements) {
    LOGGER.debug("Retrieving the contents at {}...", resourceFilePath);
    String contents = "";
    try (InputStream inputStream = this.resourceLoader.getResource(resourceFilePath).getInputStream()) {
      contents = this.parseSparqlFile(inputStream);
      for (String replacement : replacements) {
        contents = contents.replaceFirst(REPLACEMENT_TARGET, replacement);
      }
    } catch (FileNotFoundException e) {
      throw new FileSystemNotFoundException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FILE_KEY, resourceFilePath));
    } catch (IOException e) {
      LOGGER.error(e);
      throw new UncheckedIOException(e);
    }
    return contents;
  }

  /**
   * Retrieve the target file contents as a JSON object.
   * 
   * @param resourceFilePath File path to resource.
   */
  public JsonNode getJsonContents(String resourceFilePath) {
    LOGGER.debug("Retrieving the JSON contents at {}...", resourceFilePath);
    JsonNode resourceNode;
    try (InputStream inputStream = this.resourceLoader.getResource(resourceFilePath).getInputStream()) {
      resourceNode = this.objectMapper.readTree(inputStream);
    } catch (FileNotFoundException e) {
      throw new FileSystemNotFoundException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FILE_KEY, resourceFilePath));
    } catch (IOException e) {
      LOGGER.error(e);
      throw new UncheckedIOException(e);
    }
    return resourceNode;
  }

  /**
   * Gets the target file name as a response entity if there is an associated
   * identifier in the file resource, or else, return a bad response.
   * 
   * @param resourceID The target resource identifier for the instance class.
   */
  public String getTargetFileName(String resourceID) {
    LOGGER.debug("Retrieving the target class associated with the resource identifier: {} ...", resourceID);
    String targetFileName = this.getResourceTarget(resourceID,
        FileService.SPRING_FILE_PATH_PREFIX + FileService.APPLICATION_SERVICE_RESOURCE);
    // Handle invalid target type
    if (targetFileName.isEmpty()) {
      throw new FileSystemNotFoundException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FILE_KEY, resourceID));
    }
    return targetFileName;
  }

  /**
   * Gets the target IRI as a response entity if there is an associated identifier
   * in the file resource. This function also validates if the route is enabled
   * depending on if the user has set an identifier.
   * 
   * @param resourceID The target resource identifier for the instance class.
   */
  public Iri getTargetIri(String resourceID) {
    LOGGER.debug("Retrieving the target class associated with the resource identifier: {} ...", resourceID);
    if (resourceID.equals(LifecycleResource.OCCURRENCE_INSTANT_RESOURCE)) {
      return Rdf.iri(LifecycleResource.EVENT_OCCURRENCE_IRI);
    } else if (resourceID.equals(BillingResource.TRANSACTION_RECORD_RESOURCE)) {
      return BillingResource.PAYMENT_OBLIGATION_IRI;
    } else if (resourceID.equals(BillingResource.TRANSACTION_BILL_RESOURCE)) {
      return BillingResource.INDIVIDUAL_TRANSACTION_IRI;
    }
    String targetClass = this.getResourceTarget(resourceID,
        FileService.SPRING_FILE_PATH_PREFIX + FileService.APPLICATION_FORM_RESOURCE);
    // Handle invalid target type
    if (targetClass.isEmpty()) {
      throw new InvalidRouteException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_INVALID_ROUTE_KEY, resourceID));
    }
    // For valid target type, return the associated target class
    return Rdf.iri(targetClass);
  }

  /**
   * Find the resource target value within a JSON file associated with the
   * identifier.
   * 
   * @param target           The identifier for the target class.
   * @param resourceFilePath File path to resource.
   */
  public String getResourceTarget(String target, String resourceFilePath) {
    LOGGER.debug("Finding the target class for the identifier {}...", target);
    Resource resource = this.resourceLoader.getResource(resourceFilePath);
    try (InputStream inputStream = resource.getInputStream()) {
      JsonNode resourceNode = this.objectMapper.readTree(inputStream).findValue(target);
      if (resourceNode == null) {
        LOGGER.error("No valid identifier found for {}!", target);
        return "";
      }
      return this.objectMapper.treeToValue(resourceNode, String.class);
    } catch (FileNotFoundException e) {
      throw new FileSystemNotFoundException(
          LocalisationTranslator.getMessage(LocalisationResource.ERROR_MISSING_FILE_KEY, resourceFilePath));
    } catch (IOException e) {
      LOGGER.info(e.getMessage());
      throw new UncheckedIOException(e);
    }
  }

  /**
   * Parse the SPARQL content by removing all comments starting with #.
   * 
   * @param inputStream File contents as an input stream.
   */
  private String parseSparqlFile(InputStream inputStream) throws IOException {
    try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
      return reader.lines()
          .filter(line -> !line.trim().startsWith("#")) // Remove lines starting with "#"
          .collect(Collectors.joining("\n")); // Append each line with a newline character
    }
  }
}