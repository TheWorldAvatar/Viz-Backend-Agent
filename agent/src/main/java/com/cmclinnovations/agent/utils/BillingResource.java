package com.cmclinnovations.agent.utils;

import org.eclipse.rdf4j.sparqlbuilder.rdf.Iri;
import org.eclipse.rdf4j.sparqlbuilder.rdf.Rdf;

public class BillingResource {
  public static final String CUSTOMER_ACCOUNT_RESOURCE = "customer account";
  public static final String CUSTOMER_ACCOUNT_PRICING_RESOURCE = "customer pricing plans";
  public static final String CONTRACT_PRICING_RESOURCE = "customer pricing contract";

  public static final String AMOUNT_KEY = "amount";
  public static final String CHARGE_KEY = "charge";
  public static final String DISCOUNT_KEY = "discount";

  public static final String PAYMENT_OBLIGATION = "https://spec.edmcouncil.org/fibo/ontology/FND/ProductsAndServices/PaymentsAndSchedules/PaymentObligation";
  public static final Iri PAYMENT_OBLIGATION_IRI = Rdf.iri(PAYMENT_OBLIGATION);

  // Private constructor to prevent instantiation
  private BillingResource() {
    throw new UnsupportedOperationException("This class cannot be instantiated!");
  }
}