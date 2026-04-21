package com.thinkingcoding.service.rag;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Generic HTTP reader for cloud vector query.
 */
public class HttpVectorStoreQueryAdapter implements VectorStoreQueryAdapter {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String authType;
    private final String authKey;
    private final String authHeader;
    private final String indexName;

    public HttpVectorStoreQueryAdapter(HttpClient client,
                                       ObjectMapper objectMapper,
                                       String endpoint,
                                       String authType,
                                       String authKey,
                                       String authHeader,
                                       String indexName) {
        this.client = client;
        this.objectMapper = objectMapper;
        this.endpoint = endpoint;
        this.authType = authType;
        this.authKey = authKey;
        this.authHeader = authHeader;
        this.indexName = indexName;
    }

    public static VectorStoreQueryAdapter fromConfig(AppConfig.RagConfig ragConfig) {
        AppConfig.RagCloudConfig cloud = ragConfig.getCloud();
        if (!cloud.isQueryEnabled()) {
            return new NoopVectorStoreQueryAdapter();
        }

        String endpoint = resolveQueryEndpoint(ragConfig);
        if (endpoint == null || endpoint.isBlank()) {
            return new NoopVectorStoreQueryAdapter();
        }

        String authType = cloud.getAuthType();
        String authHeader = cloud.getAuthHeader();
        String authKey = cloud.getAuthKey();
        if ((authKey == null || authKey.isBlank()) && ragConfig.getApiKey() != null && !ragConfig.getApiKey().isBlank()) {
            authKey = ragConfig.getApiKey();
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(3, cloud.getTimeoutSeconds())))
                .build();

        return new HttpVectorStoreQueryAdapter(
                client,
                new ObjectMapper(),
                endpoint,
                authType,
                authKey,
                authHeader,
                cloud.getIndexName()
        );
    }

    @Override
    public QueryResult query(VectorQueryRequest request) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("index", indexName);
            payload.put("vector", request.getVector());
            payload.put("topK", request.getTopK());
            payload.put("minScore", request.getMinScore());

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

            applyAuth(builder);

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status < 200 || status >= 300) {
                return QueryResult.failure(status, response.body());
            }

            List<QueryMatch> matches = parseMatches(response.body(), request.getMinScore());
            return QueryResult.success(status, "query ok", matches);
        } catch (Exception e) {
            return QueryResult.failure(500, e.getMessage());
        }
    }

    private List<QueryMatch> parseMatches(String body, double minScore) throws Exception {
        JsonNode root = objectMapper.readTree(body);
        JsonNode matchesNode;
        if (root.isArray()) {
            matchesNode = root;
        } else if (root.has("matches")) {
            matchesNode = root.get("matches");
        } else if (root.has("data") && root.get("data").isArray()) {
            matchesNode = root.get("data");
        } else {
            matchesNode = objectMapper.createArrayNode();
        }

        List<QueryMatch> matches = new ArrayList<>();
        for (JsonNode node : matchesNode) {
            String text = textFromNode(node);
            Map<String, String> metadata = metadataFromNode(node);
            double score = scoreFromNode(node);
            if (score >= minScore && text != null && !text.isBlank()) {
                matches.add(new QueryMatch(text, metadata, score));
            }
        }
        return matches;
    }

    private String textFromNode(JsonNode node) {
        if (node.has("text")) {
            return node.get("text").asText("");
        }
        if (node.has("payload") && node.get("payload").has("text")) {
            return node.get("payload").get("text").asText("");
        }
        if (node.has("metadata") && node.get("metadata").has("text")) {
            return node.get("metadata").get("text").asText("");
        }
        return "";
    }

    private Map<String, String> metadataFromNode(JsonNode node) {
        Map<String, String> metadata = new LinkedHashMap<>();
        JsonNode metaNode = node.has("metadata") ? node.get("metadata") : null;
        if (metaNode != null && metaNode.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = metaNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                metadata.put(field.getKey(), field.getValue().asText(""));
            }
        }
        return metadata;
    }

    private double scoreFromNode(JsonNode node) {
        if (node.has("score")) {
            return node.get("score").asDouble(0.0);
        }
        if (node.has("similarity")) {
            return node.get("similarity").asDouble(0.0);
        }
        return 0.0;
    }

    private void applyAuth(HttpRequest.Builder builder) {
        if (authKey == null || authKey.isBlank()) {
            return;
        }

        if ("api-key".equalsIgnoreCase(authType)) {
            String headerName = (authHeader == null || authHeader.isBlank()) ? "x-api-key" : authHeader;
            builder.header(headerName, authKey);
            return;
        }

        builder.header("Authorization", "Bearer " + authKey);
    }

    private static String resolveQueryEndpoint(AppConfig.RagConfig ragConfig) {
        AppConfig.RagCloudConfig cloud = ragConfig.getCloud();
        if (cloud.getQueryEndpoint() != null && !cloud.getQueryEndpoint().isBlank()) {
            return cloud.getQueryEndpoint();
        }
        if (cloud.getEndpoint() != null && !cloud.getEndpoint().isBlank()) {
            return cloud.getEndpoint();
        }
        return ragConfig.getCloudStoreUrl();
    }
}

