package com.cmclinnovations.agent.template;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.MessageSource;

import com.cmclinnovations.agent.component.LocalisationTranslator;
import com.cmclinnovations.agent.model.type.LifecycleEventType;

@ExtendWith(MockitoExtension.class)

public class LifecycleQueryFactoryTest {
    private static final String EXPECTED_SCHEDULE_TEMPLATE = "?iri <https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasLifecycle>/<https://spec.edmcouncil.org/fibo/ontology/FND/Arrangements/Lifecycles/hasStage>/<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule> ?schedule.?schedule <https://www.omg.org/spec/Commons/DatesAndTimes/hasStartDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?start_date;<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasStart>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?start_time;<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimePeriod>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndTime>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasTimeValue> ?end_time.OPTIONAL{?schedule ^<https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasSchedule>/<https://www.omg.org/spec/Commons/PartiesAndSituations/holdsDuring>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasEndDate>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDateValue> ?end_date.}OPTIONAL{?schedule <https://spec.edmcouncil.org/fibo/ontology/FND/DatesAndTimes/FinancialDates/hasRecurrenceInterval>/<https://www.omg.org/spec/Commons/DatesAndTimes/hasDurationValue> ?recurrence.}BIND(IF(BOUND(?recurrence),?recurrence,\"\") AS ?recurrences)";
    private static LifecycleQueryFactory SAMPLE_FACTORY;

    @Mock
    private MessageSource messageSource;

    @InjectMocks
    private LocalisationTranslator localisationTranslator;

    @BeforeAll
    static void init() {
        SAMPLE_FACTORY = new LifecycleQueryFactory();
    }

    private static Stream<Arguments> provideParametersForLifecycleFilterStatements() {
        return Stream.of(
                Arguments.of(LifecycleEventType.APPROVED,
                        "?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises ?event .?event fibo-fnd-rel-rel:exemplifies ontoservice:ContractCreation ;    cmns-dsg:describes / <http://www.w3.org/2000/01/rdf-schema#label> ?status ."
                                + "MINUS { ?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises / fibo-fnd-rel-rel:exemplifies <https://www.theworldavatar.com/kg/ontoservice/ContractApproval> . }"
                                + EXPECTED_SCHEDULE_TEMPLATE
                                + "?event fibo-fnd-dt-oc:hasEventDate ?lastModified ."),
                Arguments.of(LifecycleEventType.SERVICE_EXECUTION,
                        EXPECTED_SCHEDULE_TEMPLATE),
                Arguments.of(LifecycleEventType.ARCHIVE_COMPLETION,
                        "?iri fibo-fnd-arr-lif:hasLifecycle / fibo-fnd-arr-lif:hasStage / cmns-col:comprises / fibo-fnd-rel-rel:exemplifies ?event .BIND(IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractDischarge>,\"Completed\",IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractRescission>,\"Rescinded\",IF(?event=<https://www.theworldavatar.com/kg/ontoservice/ContractTermination>,\"Terminated\",\"Unknown\"))) AS ?status)FILTER(?status!=\"Unknown\")"
                                + EXPECTED_SCHEDULE_TEMPLATE),
                Arguments.of(LifecycleEventType.SERVICE_ORDER_RECEIVED, EXPECTED_SCHEDULE_TEMPLATE));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForLifecycleFilterStatements")
    void testGenLifecycleFilterStatements(LifecycleEventType eventType, String expected) throws Exception {
        // Only introduce stubbing for approved event type
        if (eventType.equals(LifecycleEventType.APPROVED)) {
            Mockito.when(messageSource.getMessage(Mockito.any(), Mockito.any(), Mockito.any()))
                    .thenReturn("status");
        }

        Map<String, String> query = SAMPLE_FACTORY.genLifecycleFilterStatements(eventType);
        assertEquals(expected, query.values().stream().collect(Collectors.joining("")).replace("\n", ""));
    }
}