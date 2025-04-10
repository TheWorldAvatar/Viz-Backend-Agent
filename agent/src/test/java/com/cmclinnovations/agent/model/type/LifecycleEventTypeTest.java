package com.cmclinnovations.agent.model.type;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.cmclinnovations.agent.utils.LifecycleResource;

class LifecycleEventTypeTest {
    @Test
    void testGetShaclReplacement() {
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_APPROVAL + ">",
                LifecycleEventType.APPROVED.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_ORDER_RECEIVED + ">",
                LifecycleEventType.SERVICE_ORDER_RECEIVED.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_DISPATCH + ">",
                LifecycleEventType.SERVICE_ORDER_DISPATCHED.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_DELIVERY + ">",
                LifecycleEventType.SERVICE_EXECUTION.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_CANCELLATION + ">",
                LifecycleEventType.SERVICE_CANCELLATION.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_INCIDENT_REPORT + ">",
                LifecycleEventType.SERVICE_INCIDENT_REPORT.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_CONTRACT_COMPLETION + ">",
                LifecycleEventType.ARCHIVE_COMPLETION.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_CONTRACT_RESCISSION + ">",
                LifecycleEventType.ARCHIVE_RESCINDMENT.getShaclReplacement());
        assertEquals(
                "<https://spec.edmcouncil.org/fibo/ontology/FBC/ProductsAndServices/FinancialProductsAndServices/ContractLifecycleEventOccurrence>;"
                        + "sh:property/sh:hasValue <" + LifecycleResource.EVENT_CONTRACT_TERMINATION + ">",
                LifecycleEventType.ARCHIVE_TERMINATION.getShaclReplacement());
    }
}
