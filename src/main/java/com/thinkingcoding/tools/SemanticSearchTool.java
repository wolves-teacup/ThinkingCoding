package com.thinkingcoding.tools;

import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.model.ToolResult;
import com.thinkingcoding.service.RAGService;
import dev.langchain4j.data.segment.TextSegment;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Semantic search tool used by the agent to query indexed code chunks.
 */
public class SemanticSearchTool extends BaseTool {

    private final RAGService ragService;
    private final AppConfig appConfig;

    public SemanticSearchTool(RAGService ragService, AppConfig appConfig) {
        super("semantic_search", "Search codebase semantically using vector retrieval");
        this.ragService = ragService;
        this.appConfig = appConfig;
    }

    @Override
    public ToolResult execute(String input) {
        long startTime = System.currentTimeMillis();

        try {
            QueryRequest request = parseInput(input);
            List<TextSegment> relevantSegments = ragService.search(request.query, request.topK);

            if (relevantSegments.isEmpty()) {
                return success("No relevant code snippets found for query: " + request.query,
                        System.currentTimeMillis() - startTime);
            }

            String output = relevantSegments.stream()
                    .map(this::formatSegment)
                    .collect(Collectors.joining("\n-------------------\n"));

            return success(output, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return error("Semantic search failed: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public String getCategory() {
        return "search";
    }

    @Override
    public boolean isEnabled() {
        return appConfig != null
                && appConfig.getTools() != null
                && appConfig.getTools().getSemanticSearch().isEnabled();
    }

    private String formatSegment(TextSegment segment) {
        String filePath = segment.metadata() != null ? segment.metadata().getString("filePath") : "unknown";
        return "[file]: " + filePath + "\n" + "[snippet]:\n" + segment.text();
    }

    private QueryRequest parseInput(String input) throws Exception {
        if (input == null || input.trim().isEmpty()) {
            throw new IllegalArgumentException("Query cannot be empty");
        }

        String query = input.trim();
        int topK = appConfig.getRag().getTopK();

        if (query.startsWith("{")) {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            java.util.Map<String, Object> params = mapper.readValue(query, java.util.Map.class);
            Object q = params.get("query");
            if (q == null || q.toString().isBlank()) {
                throw new IllegalArgumentException("JSON input requires non-empty 'query'");
            }
            query = q.toString().trim();

            Object k = params.get("topK");
            if (k != null) {
                topK = Integer.parseInt(k.toString());
            }
        }

        return new QueryRequest(query, Math.max(1, topK));
    }

    public int indexWorkspace(String workspacePath) {
        return ragService.indexSourceCode(workspacePath);
    }

    private static class QueryRequest {
        private final String query;
        private final int topK;

        private QueryRequest(String query, int topK) {
            this.query = query;
            this.topK = topK;
        }
    }
}

