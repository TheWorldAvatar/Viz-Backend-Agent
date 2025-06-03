package com.cmclinnovations.agent.config;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;

@Import(I18nConfig.class)
@SpringBootTest
class MessageSourceIntegrationTest {
    private static final String EXPECTED_DEFAULT_MESSAGE = "Agent is ready to receive requests.";
    private static final String EXPECTED_GERMAN_MESSAGE = "Der Agent ist bereit, Anfragen zu empfangen.";

    @Autowired
    private MessageSource messageSource;

    private static Stream<Arguments> provideParametersForLocaleMessage() {
        return Stream.of(
                Arguments.of(Locale.ENGLISH, EXPECTED_DEFAULT_MESSAGE),
                Arguments.of(Locale.UK, EXPECTED_DEFAULT_MESSAGE),
                Arguments.of(Locale.GERMAN, EXPECTED_GERMAN_MESSAGE),
                Arguments.of(Locale.GERMANY, EXPECTED_GERMAN_MESSAGE));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForLocaleMessage")
    void testLocaleMessage(Locale locale,
            String expectedMessage) {
        String message = messageSource.getMessage("status", null, locale);
        assertEquals(expectedMessage, message);
    }
}
