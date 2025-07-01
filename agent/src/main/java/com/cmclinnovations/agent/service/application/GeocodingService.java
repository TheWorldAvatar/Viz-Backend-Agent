package com.cmclinnovations.agent.service.application;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.component.ResponseEntityBuilder;
import com.cmclinnovations.agent.model.SparqlBinding;
import com.cmclinnovations.agent.model.response.StandardApiResponse;
import com.cmclinnovations.agent.model.type.GeoLocationType;
import com.cmclinnovations.agent.model.type.SparqlEndpointType;
import com.cmclinnovations.agent.service.core.FileService;
import com.cmclinnovations.agent.service.core.KGService;
import com.cmclinnovations.agent.utils.LocalisationResource;
import com.cmclinnovations.agent.utils.ShaclResource;
import com.cmclinnovations.agent.utils.StringResource;

@Service
public class GeocodingService {
  private final FileService fileService;
  private final KGService kgService;

  private static final String COORDINATE_KEY = "coordinates";
  private static final String ADDRESS_VAR = "address";
  private static final String LOCATION_VAR = "location";
  private static final String BLOCK_VAR = "block";
  private static final String CITY_VAR = "city";
  private static final String COUNTRY_VAR = "country";
  private static final String STREET_VAR = "street";
  private static final Logger LOGGER = LogManager.getLogger(GeocodingService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param fileService File service for accessing file resources.
   * @param kgService   KG service for performing the query.
   */
  public GeocodingService(FileService fileService, KGService kgService) {
    this.fileService = fileService;
    this.kgService = kgService;
  }

  /**
   * Retrieve the address based on the postal code.
   * 
   * @param postalCode Postal code identifier.
   */
  public ResponseEntity<StandardApiResponse> getAddress(String postalCode) {
    LOGGER.debug("Retrieving geocoding endpoint...");
    // The geocoding endpoint must be added as the value of the "geocode" field
    String geocodingEndpoint = this.fileService.getTargetFileName("geocode");
    LOGGER.debug("Generating query template to search for address...");
    String query = this.genSearchQueryTemplate(postalCode);
    LOGGER.debug("Retrieving address for postal code: {} ...", postalCode);
    Queue<SparqlBinding> results = this.kgService.query(query, geocodingEndpoint);
    if (results.isEmpty()) {
      LOGGER.info("No address found!");
      return ResponseEntityBuilder
          .success(LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_NO_ADDRESS_KEY));
    } else {
      LOGGER.info("Found address(es) associated with the request!");
      List<Map<String, String>> parsedResults = new ArrayList<>();
      while (!results.isEmpty()) {
        SparqlBinding addressInstance = results.poll();
        Map<String, String> address = new HashMap<>();
        // Block is optional which results in a null
        if (addressInstance.getFieldValue(BLOCK_VAR) != null) {
          address.put(BLOCK_VAR, addressInstance.getFieldValue(BLOCK_VAR));
        }
        address.put(STREET_VAR, addressInstance.getFieldValue(STREET_VAR));
        address.put(CITY_VAR, addressInstance.getFieldValue(CITY_VAR));
        address.put(COUNTRY_VAR, addressInstance.getFieldValue(COUNTRY_VAR));
        parsedResults.add(address);
      }
      Map<String, Object> responseFields = new HashMap<>();
      responseFields.put(ADDRESS_VAR, parsedResults);
      return ResponseEntityBuilder.success(null, responseFields);
    }
  }

  /**
   * Retrieve the coordinates based on the location instance.
   * 
   * @param location The IRI of the location.
   */
  public ResponseEntity<StandardApiResponse> getCoordinates(String location) {
    LOGGER.debug("Querying for coordinates...");
    String query = this.genCoordinateQuery(location);
    List<String> endpoints = this.kgService.getEndpoints(SparqlEndpointType.BLAZEGRAPH);
    Queue<SparqlBinding> allResults = new ArrayDeque<>();
    endpoints.forEach((endpoint) -> {
      Queue<SparqlBinding> results = this.kgService.query(query, endpoint);
      allResults.addAll(results);
    });
    return this.parseCoordinates(allResults);
  }

  /**
   * Retrieve the coordinates based on the street address or postal code.
   * 
   * @param block      The street block identifier.
   * @param street     The street name.
   * @param city       The city name.
   * @param country    The country IRI based on
   *                   https://www.omg.org/spec/LCC/Countries/ISO3166-1-CountryCodes.
   * @param postalCode Postal code identifier.
   */
  public ResponseEntity<StandardApiResponse> getCoordinates(String block, String street, String city, String country,
      String postalCode) {
    LOGGER.debug("Retrieving geocoding endpoint...");
    // The geocoding endpoint must be added as the value of the "geocode" field
    String geocodingEndpoint = this.fileService.getTargetFileName("geocode");
    LOGGER.debug("Generating query template for retrieving coordinates...");
    String query = this.genCoordinateQueryTemplate(block, street, city, country, postalCode);
    LOGGER.debug("Retrieving coordinates for postal code: {} ...", postalCode);
    Queue<SparqlBinding> results = this.kgService.query(query, geocodingEndpoint);
    return this.parseCoordinates(results);
  }

  /**
   * Generates the query template for searching addresses based on the postal
   * code.
   * 
   * @param postalCode Search parameter for postal code.
   */
  private String genSearchQueryTemplate(String postalCode) {
    String selectVars = ShaclResource.VARIABLE_MARK + CITY_VAR +
        ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + COUNTRY_VAR +
        ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + STREET_VAR +
        ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + BLOCK_VAR;
    String queryFilters = GeoLocationType.POSTAL_CODE.getPred() + ShaclResource.WHITE_SPACE
        + StringResource.parseLiteral(postalCode) + ";";
    queryFilters += GeoLocationType.CITY.getPred() + ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK
        + CITY_VAR + ";";
    queryFilters += GeoLocationType.COUNTRY.getPred() + ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK
        + COUNTRY_VAR + ";";
    queryFilters += GeoLocationType.STREET.getPred() + ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK
        + STREET_VAR + ShaclResource.FULL_STOP;
    // Block numbers are optional
    queryFilters += StringResource.genOptionalClause(ShaclResource.VARIABLE_MARK + ADDRESS_VAR
        + ShaclResource.WHITE_SPACE + GeoLocationType.BLOCK.getPred() + ShaclResource.WHITE_SPACE
        + ShaclResource.VARIABLE_MARK + BLOCK_VAR + ShaclResource.FULL_STOP);
    return this.genQueryTemplate(selectVars, queryFilters);
  }

  /**
   * Generates the query template with the specific location identifiers such as
   * postal code, street names, block, city, and country.
   * 
   * @param block      The street block identifier.
   * @param street     The street name.
   * @param city       The city name.
   * @param country    The country IRI based on
   *                   https://www.omg.org/spec/LCC/Countries/ISO3166-1-CountryCodes.
   * @param postalCode Postal code identifier.
   */
  private String genCoordinateQueryTemplate(String block, String street, String city, String country,
      String postalCode) {
    String queryFilters = "fibo-fnd-arr-id:isIndexTo/geo:asWKT " + ShaclResource.VARIABLE_MARK + LOCATION_VAR + ";";
    String filterStatements = "";
    if (postalCode != null) {
      queryFilters += GeoLocationType.POSTAL_CODE.getPred();
      queryFilters += ShaclResource.WHITE_SPACE + StringResource.parseLiteral(postalCode) + ";";
    }
    if (city != null) {
      // check city name via lowercase
      queryFilters += GeoLocationType.CITY.getPred();
      queryFilters += ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + CITY_VAR + ";";
      filterStatements += this.genLowercaseFilterStatement(CITY_VAR, city);
    }
    if (country != null) {
      queryFilters += GeoLocationType.COUNTRY.getPred();
      queryFilters += ShaclResource.WHITE_SPACE + StringResource.parseIriForQuery(country) + ";";
    }

    if (street != null) {
      // Block will only be included if there is a corresponding street
      if (block != null) {
        queryFilters += GeoLocationType.BLOCK.getPred();
        // Blocks may contain strings and should be match in lower case
        queryFilters += ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + BLOCK_VAR + ";";
        filterStatements += this.genLowercaseFilterStatement(BLOCK_VAR, block);
      }
      // check street name via lowercase
      queryFilters += GeoLocationType.STREET.getPred();
      queryFilters += ShaclResource.WHITE_SPACE + ShaclResource.VARIABLE_MARK + STREET_VAR + ";";
      filterStatements += this.genLowercaseFilterStatement(STREET_VAR, street);
    }
    String selectVar = ShaclResource.VARIABLE_MARK + LOCATION_VAR;
    // Filter statements must be added to the very end to prevent any bugs
    return this.genQueryTemplate(selectVar, queryFilters + filterStatements)
        // Limit the query return to one result to improve performance
        + "LIMIT 1";
  }

  /**
   * Generates a filter statement to match string in lowercase.
   * 
   * @param variable     The variable name for filtering.
   * @param literalValue The literal value that should be matched.
   */
  private String genLowercaseFilterStatement(String variable, String literalValue) {
    return "FILTER(LCASE(" + ShaclResource.VARIABLE_MARK + variable + ")="
        + StringResource.parseLiteral(literalValue.toLowerCase()) + ")";
  }

  /**
   * Generates the query template with the required variables and where clause
   * lines.
   * 
   * @param selectVariables  The variables in the SELECT clause.
   * @param whereClauseLines The query lines for the WHERE clause.
   */
  private String genQueryTemplate(String selectVariables, String whereClauseLines) {
    return StringResource.QUERY_TEMPLATE_PREFIX +
        "SELECT DISTINCT " + selectVariables + " WHERE {" +
        ShaclResource.VARIABLE_MARK + ADDRESS_VAR + " a fibo-fnd-plc-adr:ConventionalStreetAddress;" +
        whereClauseLines +
        "}";
  }

  /**
   * Generates the query to retrieve the coordinates associated with a location
   * instance.
   * 
   * @param location The IRI of the location.
   */
  private String genCoordinateQuery(String location) {
    String locationVar = ShaclResource.VARIABLE_MARK + LOCATION_VAR;
    return StringResource.QUERY_TEMPLATE_PREFIX +
        "SELECT DISTINCT " + locationVar + "{" +
        StringResource.parseIriForQuery(location) + " a fibo-fnd-plc-loc:PhysicalLocation;" +
        "geo:hasGeometry/geo:asWKT " + locationVar + "." +
        "}";
  }

  /**
   * Parses the coordinates from query results into longitude and latitude.
   * 
   * @param results The query results.
   */
  private ResponseEntity<StandardApiResponse> parseCoordinates(Queue<SparqlBinding> results) {
    if (results.isEmpty()) {
      LOGGER.info("No coordinates found...");
      return ResponseEntityBuilder
          .success(LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_NO_COORDINATE_KEY));
    } else {
      LOGGER.info("Found the associated geocoordinates!");
      // Returns the first geoPoint as the same location may have multiple results
      String geoPoint = results.poll().getFieldValue(LOCATION_VAR);
      Map<String, Object> responseFields = new HashMap<>();
      responseFields.put(COORDINATE_KEY, this.parseCoordinates(geoPoint));
      return ResponseEntityBuilder.success(null, responseFields);
    }
  }

  /**
   * Parses the coordinates into longitude and latitude.
   * 
   * @param geoPoint The coordinates stored as a geoPoint.
   */
  private double[] parseCoordinates(String geoPoint) {
    // REGEX for `POINT(Longitude Latitude)` format
    Pattern pattern = Pattern.compile("POINT\\((\\d+\\.\\d+),? ?(\\d+\\.\\d+)\\)");
    Matcher matcher = pattern.matcher(geoPoint);

    if (matcher.matches()) {
      double longitude = Double.parseDouble(matcher.group(1));
      double latitude = Double.parseDouble(matcher.group(2));
      return new double[] { longitude, latitude };
    }
    LOGGER.warn("Unable to parse geoPoint into valid coordinates...");
    return new double[] {}; // Returns empty array if no result is found
  }
}