package com.cmclinnovations.agent.config;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;

import javax.annotation.Nonnull;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.fasterxml.jackson.annotation.JsonAutoDetect;
import com.fasterxml.jackson.annotation.PropertyAccessor;
import com.fasterxml.jackson.databind.ObjectMapper;

@Configuration
public class RedisCacheConfig {
    @Value("${REDIS}")
    private String redisUrl;

    private static final String PASSWORD_SECRET = "/run/secrets/redis_password";

    @Bean
    public RedisCacheConfiguration cacheConfiguration() {
        ObjectMapper om = new ObjectMapper();
        // Makes all public and private fields visible for serialisation/deserialisation
        om.setVisibility(PropertyAccessor.FIELD, JsonAutoDetect.Visibility.ANY);
        // Transform type information during serialisation
        om.activateDefaultTyping(om.getPolymorphicTypeValidator(), ObjectMapper.DefaultTyping.NON_FINAL);
        @Nonnull
        Jackson2JsonRedisSerializer<@Nonnull Object> jackson2JsonRedisSerializer = new Jackson2JsonRedisSerializer<>(om,
                Object.class);
        return RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofDays(7))
                .disableCachingNullValues()
                // String Serializer for keys
                .serializeKeysWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(new StringRedisSerializer()))
                // Jackson JSON Serializer for values
                .serializeValuesWith(
                        RedisSerializationContext.SerializationPair
                                .fromSerializer(jackson2JsonRedisSerializer));
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