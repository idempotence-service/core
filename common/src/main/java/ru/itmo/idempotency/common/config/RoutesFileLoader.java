package ru.itmo.idempotency.common.config;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class RoutesFileLoader {

    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory())
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    public RouteModels.RoutesFile load(Path path) {
        if (!Files.exists(path)) {
            throw new IllegalStateException("Routes file not found: " + path.toAbsolutePath());
        }

        try (InputStream inputStream = Files.newInputStream(path)) {
            RouteModels.RoutesFile routesFile = yamlMapper.readValue(inputStream, RouteModels.RoutesFile.class);
            if (routesFile == null || routesFile.kafka() == null || routesFile.kafka().routes() == null) {
                throw new IllegalStateException("Routes file is empty or invalid: " + path.toAbsolutePath());
            }
            return routesFile;
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read routes file: " + path.toAbsolutePath(), exception);
        }
    }
}
