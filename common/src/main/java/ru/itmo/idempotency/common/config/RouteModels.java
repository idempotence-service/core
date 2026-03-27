package ru.itmo.idempotency.common.config;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.Map;

public final class RouteModels {

    private RouteModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RoutesFile(ServiceConfig service, KafkaConfig kafka) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceConfig(String name) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KafkaConfig(Map<String, RouteDefinition> routes) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteDefinition(String service,
                                  RouteSideConfig sender,
                                  RouteSideConfig receiver,
                                  RouteIdempotencyConfig idempotency) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteSideConfig(RouteEndpointConfig producer, RouteEndpointConfig consumer) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteEndpointConfig(String host, String topic, String group) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RouteIdempotencyConfig(Boolean enabled) {
    }

    public record RouteChannel(String bootstrapServers, String topic, String group) {
    }

    public record RouteSnapshot(String service,
                                String integration,
                                RouteChannel inbound,
                                RouteChannel requestOut,
                                RouteChannel replyIn,
                                RouteChannel replyOut,
                                boolean idempotencyEnabled) {
    }
}
