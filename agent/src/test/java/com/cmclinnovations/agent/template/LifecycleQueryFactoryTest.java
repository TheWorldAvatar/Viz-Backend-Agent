package com.cmclinnovations.agent.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.cmclinnovations.agent.model.type.LifecycleEventType;

public class LifecycleQueryFactoryTest {
    private static final String EXPECTED_SCHEDULE_TEMPLATE = "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage>/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule> ?schedule.?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?start_date;^<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?end_date;<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasStart>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?start_time;<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndTime>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?end_time;<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?recurrence.BIND(IF(?recurrence=\"P1D\",\"Single Service\",IF(?recurrence=\"P2D\",\"Alternate Day Service\", \"Regular Service\")) AS ?schedule_type)";
    private static LifecycleQueryFactory SAMPLE_FACTORY;

    @BeforeAll
    static void init() {
        SAMPLE_FACTORY = new LifecycleQueryFactory();
    }

    private static Stream<Arguments> provideParametersForLifecycleFilterStatements() {
        return Stream.of(
                Arguments.of(LifecycleEventType.APPROVED,
                        EXPECTED_SCHEDULE_TEMPLATE
                                + "MINUS { ?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises / fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/ContractApproval> . }"),
                Arguments.of(LifecycleEventType.SERVICE_EXECUTION,
                        EXPECTED_SCHEDULE_TEMPLATE
                                + "FILTER EXISTS { ?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises / fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/ContractApproval> . }MINUS { ?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage ?stage_archived ;    fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/ExpirationStage> ;    cmns-col:comprises ?event . }"),
                Arguments.of(LifecycleEventType.ARCHIVE_COMPLETION,
                        EXPECTED_SCHEDULE_TEMPLATE
                                + "?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises / fibo-fnd-rel-rel:exemplifies ?event .BIND(IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractDischarge>,\"Completed\",IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractRescission>,\"Rescinded\",IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractTermination>,\"Terminated\",\"Unknown\"))) AS ?status)FILTER(?status!=\"Unknown\")"),
                Arguments.of(LifecycleEventType.SERVICE_ORDER_RECEIVED, EXPECTED_SCHEDULE_TEMPLATE));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForLifecycleFilterStatements")
    void testGenLifecycleFilterStatements(LifecycleEventType eventType, String expected) throws Exception {
        String query = SAMPLE_FACTORY.genLifecycleFilterStatements(eventType);
        assertEquals(expected, query.replace("\n", ""));
    }

    @Test
    void testGetReadableScheduleQuery() {
        String query = SAMPLE_FACTORY.getReadableScheduleQuery();
        assertEquals(EXPECTED_SCHEDULE_TEMPLATE, query);
    }
}
