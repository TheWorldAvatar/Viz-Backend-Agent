package com.cmclinnovations.agent.config;

import java.util.List;
import java.util.Locale;

import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.servlet.LocaleResolver;
import org.springframework.web.servlet.i18n.AcceptHeaderLocaleResolver;

@Configuration
public class I18nConfig {
    @Bean
    public MessageSource messageSource() {
        ResourceBundleMessageSource messageSource = new ResourceBundleMessageSource();
        // Set up the base path in the resource directory
        messageSource.setBasenames("lang/messages");
        messageSource.setDefaultEncoding("UTF-8");
        messageSource.setUseCodeAsDefaultMessage(true);
        return messageSource;
    }

    @Bean
    public LocaleResolver localeResolver() {
        // Use AcceptHeaderLocaleResolver to resolve locale based on the Accept-Language
        // header; This is suitable for REST APIs, when the request is typically sent
        // from a client browser/application;
        // Online examples uses CookieLocaleResolver, that is not suitable for REST APIs
        // and are intended for Spring Boot web applications
        AcceptHeaderLocaleResolver localeResolver = new AcceptHeaderLocaleResolver();
        // Locale options are available as either language and country. We are using the
        // language format in our message.properties for simplicity
        localeResolver.setDefaultLocale(Locale.ENGLISH);
        localeResolver.setSupportedLocales(List.of(Locale.GERMANY, Locale.GERMAN, Locale.UK));
        return localeResolver;
    }
}
