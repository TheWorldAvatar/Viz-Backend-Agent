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
import org.eclipse.rdf4j.sparqlbuilder.core.SparqlBuilder;
import org.eclipse.rdf4j.sparqlbuilder.core.Variable;
import org.eclipse.rdf4j.sparqlbuilder.core.query.SelectQuery;
import org.eclipse.rdf4j.sparqlbuilder.graphpattern.GraphPatterns;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;
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
import com.cmclinnovations.agent.utils.QueryResource;

@Service
public class GeocodingService {
  private final FileService fileService;
  private final KGService kgService;
  private final ResponseEntityBuilder responseEntityBuilder;

  private static final String COORDINATE_KEY = "coordinates";
  private static final String ADDRESS_VARNAME = "address";
  private static final Variable ADDRESS_VAR = SparqlBuilder.var(ADDRESS_VARNAME);
  private static final String LOCATION_VARNAME = "location";
  private static final Variable LOCATION_VAR = SparqlBuilder.var(LOCATION_VARNAME);
  private static final String BLOCK_VARNAME = "block";
  private static final Variable BLOCK_VAR = SparqlBuilder.var(BLOCK_VARNAME);
  private static final String CITY_VARNAME = "city";
  private static final Variable CITY_VAR = SparqlBuilder.var(CITY_VARNAME);
  private static final String COUNTRY_VARNAME = "country";
  private static final Variable COUNTRY_VAR = SparqlBuilder.var(COUNTRY_VARNAME);
  private static final String STREET_VARNAME = "street";
  private static final Variable STREET_VAR = SparqlBuilder.var(STREET_VARNAME);
  private static final Pattern GEOPOINT_PATTERN = Pattern.compile("POINT\\((\\d+\\.\\d+),? ?(\\d+\\.\\d+)\\)");
  private static final Logger LOGGER = LogManager.getLogger(GeocodingService.class);

  /**
   * Constructs a new service with the following dependencies.
   * 
   * @param fileService           File service for accessing file resources.
   * @param kgService             KG service for performing the query.
   * @param responseEntityBuilder A component to build the response entity.
   */
  public GeocodingService(FileService fileService, KGService kgService, ResponseEntityBuilder responseEntityBuilder) {
    this.fileService = fileService;
    this.kgService = kgService;
    this.responseEntityBuilder = responseEntityBuilder;
  }

  /**
   * Retrieve the address based on the postal code.
   * 
   * @param postalCode Postal code identifier.
   */
  public ResponseEntity<StandardApiResponse<?>> getAddress(String postalCode) {
    LOGGER.debug("Retrieving geocoding endpoint...");
    // The geocoding endpoint must be added as the value of the "geocode" field
    String geocodingEndpoint = this.fileService.getTargetFileName("geocode");
    LOGGER.debug("Generating query template to search for address...");
    String query = this.genSearchQueryTemplate(postalCode);
    LOGGER.debug("Retrieving address for postal code: {} ...", postalCode);
    Queue<SparqlBinding> results = this.kgService.query(query, geocodingEndpoint);
    if (results.isEmpty()) {
      LOGGER.info("No address found!");
      return this.responseEntityBuilder
          .success(null, LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_NO_ADDRESS_KEY));
    } else {
      LOGGER.info("Found address(es) associated with the request!");
      List<Map<String, Object>> parsedResults = new ArrayList<>();
      while (!results.isEmpty()) {
        SparqlBinding addressInstance = results.poll();
        Map<String, Object> address = new HashMap<>();
        // Block is optional which results in a null
        if (addressInstance.getFieldValue(BLOCK_VARNAME) != null) {
          address.put(BLOCK_VARNAME, addressInstance.getFieldValue(BLOCK_VARNAME));
        }
        address.put(STREET_VARNAME, addressInstance.getFieldValue(STREET_VARNAME));
        address.put(CITY_VARNAME, addressInstance.getFieldValue(CITY_VARNAME));
        address.put(COUNTRY_VARNAME, addressInstance.getFieldValue(COUNTRY_VARNAME));
        parsedResults.add(address);
      }
      return this.responseEntityBuilder.success(null, parsedResults);
    }
  }

  /**
   * Retrieve the coordinates based on the location instance.
   * 
   * @param location The IRI of the location.
   */
  public ResponseEntity<StandardApiResponse<?>> getCoordinates(String location) {
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
  public ResponseEntity<StandardApiResponse<?>> getCoordinates(String block, String street, String city, String country,
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
    SelectQuery queryTemplate = QueryResource.getSelectQuery(true, null);
    queryTemplate.select(CITY_VAR, COUNTRY_VAR, STREET_VAR, BLOCK_VAR)
        .where(GraphPatterns.and(
            ADDRESS_VAR.isA(QueryResource.FIBO_FND_PLC_ADR.iri("ConventionalStreetAddress"))
                .andHas(GeoLocationType.POSTAL_CODE.getPred(), Rdf.literalOf(postalCode))
                .andHas(GeoLocationType.CITY.getPred(), CITY_VAR)
                .andHas(GeoLocationType.COUNTRY.getPred(), COUNTRY_VAR)
                .andHas(GeoLocationType.STREET.getPred(), STREET_VAR),
            // Block numbers are optional
            GraphPatterns.optional(ADDRESS_VAR.has(GeoLocationType.BLOCK.getPred(), BLOCK_VAR))));
    return queryTemplate.getQueryString();
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
    SelectQuery queryTemplate = QueryResource.getSelectQuery(true, 1);
    queryTemplate.select(LOCATION_VAR)
        .where(ADDRESS_VAR
            .isA(QueryResource.FIBO_FND_PLC_ADR.iri("ConventionalStreetAddress"))
            .andHas(p -> p.pred(QueryResource.FIBO_FND_ARR_ID.iri("isIndexTo"))
                .then(QueryResource.GEO.iri("asWKT")), LOCATION_VAR));
    if (postalCode != null) {
      queryTemplate.where(ADDRESS_VAR.has(GeoLocationType.POSTAL_CODE.getPred(), Rdf.literalOf(postalCode)));
    }
    if (city != null) {
      // check city name via lowercase
      queryTemplate.where(ADDRESS_VAR.has(GeoLocationType.CITY.getPred(), CITY_VAR)
          .filter(QueryResource.genLowercaseExpression(CITY_VAR, city)));
    }
    if (country != null) {
      queryTemplate.where(ADDRESS_VAR.has(GeoLocationType.COUNTRY.getPred(), Rdf.iri(country)));
    }

    if (street != null) {
      // Block will only be included if there is a corresponding street
      if (block != null) {
        // Blocks may contain strings and should be match in lower case
        queryTemplate.where(ADDRESS_VAR.has(GeoLocationType.BLOCK.getPred(), BLOCK_VAR)
            .filter(QueryResource.genLowercaseExpression(BLOCK_VAR, block)));

      }
      // check street name via lowercase
      queryTemplate.where(ADDRESS_VAR.has(GeoLocationType.STREET.getPred(), STREET_VAR)
          .filter(QueryResource.genLowercaseExpression(STREET_VAR, street)));
    }
    return queryTemplate.getQueryString();
  }

  /**
   * Generates the query to retrieve the coordinates associated with a location
   * instance.
   * 
   * @param location The IRI of the location.
   */
  private String genCoordinateQuery(String location) {
    Iri locationIri = Rdf.iri(location);
    return QueryResource.getSelectQuery(true, null).select(LOCATION_VAR)
        .where(locationIri.isA(QueryResource.FIBO_FND_PLC_LOC.iri("PhysicalLocation"))
            .andHas(p -> p.pred(QueryResource.GEO.iri("hasGeometry"))
                .then(QueryResource.GEO.iri("asWKT")), LOCATION_VAR))
        .getQueryString();
  }

  /**
   * Parses the coordinates from query results into longitude and latitude.
   * 
   * @param results The query results.
   */
  private ResponseEntity<StandardApiResponse<?>> parseCoordinates(Queue<SparqlBinding> results) {
    if (results.isEmpty()) {
      LOGGER.info("No coordinates found...");
      return this.responseEntityBuilder
          .success(null, LocalisationTranslator.getMessage(LocalisationResource.MESSAGE_NO_COORDINATE_KEY));
    } else {
      LOGGER.info("Found the associated geocoordinates!");
      // Returns the first geoPoint as the same location may have multiple results
      String geoPoint = results.poll().getFieldValue(LOCATION_VARNAME);
      Map<String, Object> responseFields = new HashMap<>();
      responseFields.put(COORDINATE_KEY, this.parseCoordinates(geoPoint));
      return this.responseEntityBuilder.success(null, responseFields);
    }
  }

  /**
   * Parses the coordinates into longitude and latitude.
   * 
   * @param geoPoint The coordinates stored as a geoPoint.
   */
  private double[] parseCoordinates(String geoPoint) {
    // REGEX for `POINT(Longitude Latitude)` format
    Matcher matcher = GEOPOINT_PATTERN.matcher(geoPoint);

    if (matcher.matches()) {
      double longitude = Double.parseDouble(matcher.group(1));
      double latitude = Double.parseDouble(matcher.group(2));
      return new double[] { longitude, latitude };
    }
    LOGGER.warn("Unable to parse geoPoint into valid coordinates...");
    return new double[] {}; // Returns empty array if no result is found
  }
}