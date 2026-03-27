package ru.itmo.idempotency.common.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

@Component
public class RouteCatalog {

    private final Path routesFilePath;
    private final RoutesFileLoader routesFileLoader;

    @Getter
    private Map<String, RouteModels.RouteSnapshot> routes = Map.of();

    public RouteCatalog(@Value("${app.routes-file:config/routes.yaml}") String routesFile,
                        RoutesFileLoader routesFileLoader) {
        this.routesFilePath = Path.of(routesFile);
        this.routesFileLoader = routesFileLoader;
        reload();
    }

    public synchronized void reload() {
        RouteModels.RoutesFile routesFile = routesFileLoader.load(routesFilePath);
        String defaultServiceName = Optional.ofNullable(routesFile.service())
                .map(RouteModels.ServiceConfig::name)
                .orElseThrow(() -> new IllegalStateException("service.name must be configured in routes file"));

        Map<String, RouteModels.RouteSnapshot> loadedRoutes = new LinkedHashMap<>();
        for (Map.Entry<String, RouteModels.RouteDefinition> entry : routesFile.kafka().routes().entrySet()) {
            String integrationName = entry.getKey();
            RouteModels.RouteDefinition definition = entry.getValue();
            String serviceName = definition.service() != null && !definition.service().isBlank()
                    ? definition.service()
                    : defaultServiceName;
            RouteModels.RouteSnapshot snapshot = new RouteModels.RouteSnapshot(
                    serviceName,
                    integrationName,
                    toChannel(definition.sender() != null ? definition.sender().producer() : null, null),
                    toChannel(definition.sender() != null ? definition.sender().consumer() : null, null),
                    toChannel(definition.receiver() != null ? definition.receiver().producer() : null, null),
                    toChannel(definition.receiver() != null ? definition.receiver().consumer() : null, null),
                    definition.idempotency() == null || Boolean.TRUE.equals(definition.idempotency().enabled())
            );
            loadedRoutes.put(integrationName, snapshot);
        }
        this.routes = Map.copyOf(loadedRoutes);
    }

    public Collection<RouteModels.RouteSnapshot> getAllRoutes() {
        return routes.values();
    }

    public Collection<RouteModels.RouteSnapshot> getEnabledRoutes() {
        return routes.values().stream().filter(RouteModels.RouteSnapshot::idempotencyEnabled).toList();
    }

    public RouteModels.RouteSnapshot getRequiredRoute(String integrationName) {
        return Optional.ofNullable(routes.get(integrationName))
                .orElseThrow(() -> new IllegalArgumentException("Route not found: " + integrationName));
    }

    private RouteModels.RouteChannel toChannel(RouteModels.RouteEndpointConfig endpointConfig, String fallbackGroup) {
        if (endpointConfig == null || endpointConfig.host() == null || endpointConfig.topic() == null) {
            return null;
        }
        String group = endpointConfig.group() != null ? endpointConfig.group() : fallbackGroup;
        return new RouteModels.RouteChannel(endpointConfig.host(), endpointConfig.topic(), group);
    }
}
