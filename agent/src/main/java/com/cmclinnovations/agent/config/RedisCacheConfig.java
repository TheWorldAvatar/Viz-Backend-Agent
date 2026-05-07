package com.cmclinnovations.agent.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;

import tools.jackson.databind.DefaultTyping;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.jsontype.BasicPolymorphicTypeValidator;
import tools.jackson.databind.jsontype.PolymorphicTypeValidator;

@Configuration
public class RedisCacheConfig {
    @Value("${REDIS}")
    private String redisUrl;

    private static final String PASSWORD_SECRET = "/run/secrets/redis_password";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        PolymorphicTypeValidator ptv = BasicPolymorphicTypeValidator.builder()
                .allowIfBaseType(Object.class)
                .build();
        JsonMapper om = JsonMapper.builder()
                // Makes all public and private fields visible for serialisation/deserialisation
                .changeDefaultVisibility(vc -> vc
                        .withVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY))
                // Transform type information during serialisation
                .activateDefaultTyping(
                        ptv,
                        DefaultTyping.NON_FINAL)
                .findAndAddModules()
                .build();
        JacksonJsonRedisSerializer<Object> jacksonJsonRedisSerializer = new JacksonJsonRedisSerializer<>(om,
                Object.class);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(7))
                .disableCachingNullValues()
                // String Serializer for keys
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                // Jackson JSON Serializer for values
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jacksonJsonRedisSerializer));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(config)
                .build();
    }

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            URI uri = new URI(redisUrl);
            String host = uri.getHost();
            int port = (uri.getPort() != -1) ? uri.getPort() : 6379;

            RedisStandaloneConfiguration config = new RedisStandaloneConfiguration(host, port);
            String password = Files.readString(Paths.get(PASSWORD_SECRET)).trim();
            config.setPassword(password);
            return new LettuceConnectionFactory(config, LettuceClientConfiguration.defaultConfiguration());
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read Redis secret file", e);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Invalid Redis URL: " + redisUrl, e);
        }
    }
}