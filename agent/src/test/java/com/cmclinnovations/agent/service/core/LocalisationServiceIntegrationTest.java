package com.cmclinnovations.agent.service.core;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Import;
import org.springframework.context.i18n.LocaleContextHolder;

import com.cmclinnovations.agent.config.I18nConfig;
import com.cmclinnovations.agent.config.MessageSourceIntegrationTest;

@Import(I18nConfig.class)
@SpringBootTest
class LocalisationServiceIntegrationTest {
    @Autowired
    private MessageSource messageSource;

    private LocalisationService localisationService;

    @BeforeEach
    void setUp() {
        localisationService = new LocalisationService(messageSource);
        LocaleContextHolder.resetLocaleContext();
    }

    private static Stream<Arguments> provideParametersForLocaleMessage() {
        return Stream.of(
                Arguments.of(Locale.ENGLISH, MessageSourceIntegrationTest.EXPECTED_DEFAULT_MESSAGE),
                Arguments.of(Locale.UK, MessageSourceIntegrationTest.EXPECTED_DEFAULT_MESSAGE),
                Arguments.of(Locale.GERMAN, MessageSourceIntegrationTest.EXPECTED_GERMAN_MESSAGE),
                Arguments.of(Locale.GERMANY, MessageSourceIntegrationTest.EXPECTED_GERMAN_MESSAGE));
    }

    @ParameterizedTest
    @MethodSource("provideParametersForLocaleMessage")
    void testLocaleMessage(Locale locale, String expectedMessage) {
        LocaleContextHolder.setLocale(locale);
        String message = localisationService.getMessage("status");
        assertEquals(expectedMessage, message);
    }

    @ParameterizedTest
    @MethodSource("provideParametersForLocaleMessage")
    void testLocaleMessage_InvalidKey(Locale locale, String expectedMessage) {
        String invalidKey = "invalid";
        LocaleContextHolder.setLocale(locale);
        String message = localisationService.getMessage(invalidKey);
        assertEquals(invalidKey, message);
    }
}
