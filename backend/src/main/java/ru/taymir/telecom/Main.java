package ru.taymir.telecom;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.Executors;

public class Main {

    public static void main(String[] args) throws IOException {
        int port = readPort();

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", new RootController());
        server.createContext("/health", exchange ->
                sendResponse(exchange, 200, "{\"status\":\"ok\"}", "application/json")
        );

        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.start();

        System.out.println("Server started on http://0.0.0.0:" + port);
        System.out.println("Endpoints:");
        System.out.println("  GET /");
        System.out.println("  GET /health");
    }

    private static int readPort() {
        String rawPort = System.getenv().getOrDefault("SERVER_PORT", "8080");
        try {
            return Integer.parseInt(rawPort);
        } catch (NumberFormatException ex) {
            throw new IllegalStateException("SERVER_PORT must be a valid integer, got: " + rawPort, ex);
        }
    }

    static class RootController implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "Method Not Allowed", "text/plain; charset=UTF-8");
                return;
            }

            String response = "Hello from Java backend!";
            sendResponse(exchange, 200, response, "text/plain; charset=UTF-8");
        }
    }

    private static void sendResponse(HttpExchange exchange, int statusCode, String body, String contentType)
            throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);

        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);

        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }
}
