package com.thinkingcoding.service.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.config.AppConfig;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Generic HTTP writer for cloud vector databases.
 */
public class HttpVectorStoreWriteAdapter implements VectorStoreWriteAdapter {

    private final HttpClient client;
    private final ObjectMapper objectMapper;
    private final String endpoint;
    private final String authType;
    private final String authKey;
    private final String authHeader;
    private final String indexName;

    public HttpVectorStoreWriteAdapter(HttpClient client,
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

    public static VectorStoreWriteAdapter fromConfig(AppConfig.RagConfig ragConfig) {
        String endpoint = resolveEndpoint(ragConfig);
        if (endpoint == null || endpoint.isBlank()) {
            return new NoopVectorStoreWriteAdapter();
        }

        AppConfig.RagCloudConfig cloud = ragConfig.getCloud();
        String authType = cloud.getAuthType();
        String authHeader = cloud.getAuthHeader();
        String authKey = cloud.getAuthKey();
        if ((authKey == null || authKey.isBlank()) && ragConfig.getApiKey() != null && !ragConfig.getApiKey().isBlank()) {
            authKey = ragConfig.getApiKey();
        }

        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(Math.max(3, cloud.getTimeoutSeconds())))
                .build();

        return new HttpVectorStoreWriteAdapter(
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
    public WriteResult upsert(VectorWriteRequest request) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("index", indexName);
            payload.put("id", request.getId());
            payload.put("vector", request.getVector());
            payload.put("text", request.getText());
            payload.put("metadata", request.getMetadata());

            String json = objectMapper.writeValueAsString(payload);
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .timeout(Duration.ofSeconds(30))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));

            applyAuth(builder);

            HttpResponse<String> response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            int status = response.statusCode();
            if (status >= 200 && status < 300) {
                return WriteResult.success(status, "upsert ok");
            }
            return WriteResult.failure(status, response.body());
        } catch (Exception e) {
            return WriteResult.failure(500, e.getMessage());
        }
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

    private static String resolveEndpoint(AppConfig.RagConfig ragConfig) {
        AppConfig.RagCloudConfig cloud = ragConfig.getCloud();
        if (cloud != null && cloud.getEndpoint() != null && !cloud.getEndpoint().isBlank()) {
            return cloud.getEndpoint();
        }
        return ragConfig.getCloudStoreUrl();
    }
}

