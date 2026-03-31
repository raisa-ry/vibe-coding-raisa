package com.hiking.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hiking.model.ParsedConditions;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Service
public class HikeParserService {

    private static final String MCP_URL = "http://localhost:3001/mcp";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    public ParsedConditions parseDescription(String description) throws Exception {
        // Step 1: initialize — get session ID
        Map<String, Object> initBody = Map.of(
                "jsonrpc", "2.0",
                "id", 1,
                "method", "initialize",
                "params", Map.of(
                        "protocolVersion", "2024-11-05",
                        "capabilities", new HashMap<>(),
                        "clientInfo", Map.of("name", "hikepack-java-client", "version", "1.0")
                )
        );

        HttpRequest initRequest = HttpRequest.newBuilder()
                .uri(URI.create(MCP_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(initBody)))
                .build();

        HttpResponse<String> initResponse = httpClient.send(initRequest, HttpResponse.BodyHandlers.ofString());

        if (initResponse.statusCode() != 200) {
            throw new RuntimeException("MCP initialize failed (" + initResponse.statusCode() + "): " + initResponse.body());
        }

        String sessionId = initResponse.headers().firstValue("Mcp-Session-Id").orElse(null);

        // Step 2: tools/call
        Map<String, Object> callBody = Map.of(
                "jsonrpc", "2.0",
                "id", 2,
                "method", "tools/call",
                "params", Map.of(
                        "name", "parse_hike_conditions",
                        "arguments", Map.of("description", description)
                )
        );

        HttpRequest.Builder callBuilder = HttpRequest.newBuilder()
                .uri(URI.create(MCP_URL))
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream");
        if (sessionId != null) {
            callBuilder.header("Mcp-Session-Id", sessionId);
        }
        callBuilder.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(callBody)));

        HttpResponse<String> callResponse = httpClient.send(callBuilder.build(), HttpResponse.BodyHandlers.ofString());

        if (callResponse.statusCode() != 200) {
            throw new RuntimeException("MCP tools/call failed (" + callResponse.statusCode() + "): " + callResponse.body());
        }

        JsonNode root = objectMapper.readTree(extractJson(callResponse));

        if (root.has("error")) {
            throw new RuntimeException("MCP error: " + root.get("error").toString());
        }

        String conditionsJson = root
                .path("result")
                .path("content")
                .get(0)
                .path("text")
                .asText();

        return objectMapper.readValue(conditionsJson, ParsedConditions.class);
    }

    /**
     * The MCP StreamableHTTP transport may respond with either plain JSON
     * (Content-Type: application/json) or SSE (Content-Type: text/event-stream).
     * SSE lines look like:
     *   event: message
     *   data: {"jsonrpc":"2.0","result":...}
     * Extract the first "data:" line and return it as the JSON string.
     */
    private String extractJson(HttpResponse<String> response) {
        String contentType = response.headers().firstValue("Content-Type").orElse("");
        if (contentType.contains("text/event-stream")) {
            return response.body().lines()
                    .filter(line -> line.startsWith("data:"))
                    .map(line -> line.substring(5).strip())
                    .filter(data -> data.startsWith("{"))
                    .findFirst()
                    .orElseThrow(() -> new RuntimeException("No JSON data line found in SSE response: " + response.body()));
        }
        return response.body();
    }
}
