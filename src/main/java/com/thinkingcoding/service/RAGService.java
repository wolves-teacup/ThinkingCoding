package com.thinkingcoding.service;

import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.service.rag.HttpVectorStoreQueryAdapter;
import com.thinkingcoding.service.rag.HttpVectorStoreWriteAdapter;
import com.thinkingcoding.service.rag.QueryMatch;
import com.thinkingcoding.service.rag.QueryResult;
import com.thinkingcoding.service.rag.VectorQueryRequest;
import com.thinkingcoding.service.rag.VectorStoreQueryAdapter;
import com.thinkingcoding.service.rag.VectorStoreWriteAdapter;
import com.thinkingcoding.service.rag.VectorWriteRequest;
import com.thinkingcoding.service.rag.WriteResult;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.DocumentSplitter;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * RAG service: index source code with Bailian embeddings, keep vectors in memory,
 * upsert vectors to cloud, and fallback to cloud query when local hits are insufficient.
 */
public class RAGService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of(
            ".java", ".kt", ".md", ".txt", ".xml", ".yml", ".yaml", ".properties"
    );

    private final EmbeddingModel embeddingModel;
    private final InMemoryEmbeddingStore<TextSegment> memoryStore;
    private final AppConfig.RagConfig ragConfig;
    private final VectorStoreWriteAdapter cloudWriter;
    private final VectorStoreQueryAdapter cloudQuery;

    public RAGService(AppConfig.RagConfig ragConfig) {
        this.ragConfig = ragConfig;
        this.embeddingModel = OpenAiEmbeddingModel.builder()
                .baseUrl(ragConfig.getBaseUrl())
                .apiKey(ragConfig.getApiKey())
                .modelName(ragConfig.getEmbeddingModel())
                .build();
        this.memoryStore = new InMemoryEmbeddingStore<>();
        this.cloudWriter = HttpVectorStoreWriteAdapter.fromConfig(ragConfig);
        this.cloudQuery = HttpVectorStoreQueryAdapter.fromConfig(ragConfig);
    }

    public int indexSourceCode(String workspacePath) {
        int indexedSegments = 0;
        DocumentSplitter splitter = DocumentSplitters.recursive(
                ragConfig.getChunkSize(), ragConfig.getChunkOverlap()
        );

        try (Stream<Path> paths = Files.walk(Paths.get(workspacePath))) {
            List<Path> files = paths
                    .filter(Files::isRegularFile)
                    .filter(this::isSupportedFile)
                    .toList();

            for (Path file : files) {
                String content = Files.readString(file, StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    continue;
                }

                Metadata metadata = Metadata.from("filePath", file.toAbsolutePath().toString());
                Document document = Document.from(content, metadata);
                List<TextSegment> segments = splitter.split(document);

                for (TextSegment segment : segments) {
                    Embedding embedding = embeddingModel.embed(segment).content();
                    memoryStore.add(embedding, segment);
                    upsertToCloud(embedding, segment);
                    indexedSegments++;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to index source code: " + e.getMessage(), e);
        }

        return indexedSegments;
    }

    public List<TextSegment> search(String query, int topK) {
        int targetTopK = Math.max(1, topK);
        Embedding queryEmbedding = embeddingModel.embed(query).content();

        List<TextSegment> local = localSearch(queryEmbedding, targetTopK);
        if (local.size() >= targetTopK) {
            return local;
        }

        List<TextSegment> merged = new ArrayList<>(local);
        List<TextSegment> cloud = cloudSearch(queryEmbedding, targetTopK);
        mergeDedup(merged, cloud, targetTopK);

        return merged;
    }

    private List<TextSegment> localSearch(Embedding queryEmbedding, int topK) {
        List<EmbeddingMatch<TextSegment>> matches = memoryStore.search(
                        EmbeddingSearchRequest.builder()
                                .queryEmbedding(queryEmbedding)
                                .maxResults(topK)
                                .build())
                .matches();

        List<TextSegment> result = new ArrayList<>();
        for (EmbeddingMatch<TextSegment> match : matches) {
            if (match != null && match.embedded() != null) {
                result.add(match.embedded());
            }
        }
        return result;
    }

    private List<TextSegment> cloudSearch(Embedding queryEmbedding, int topK) {
        VectorQueryRequest request = new VectorQueryRequest(
                toFloatList(queryEmbedding.vector()),
                topK,
                ragConfig.getCloud().getMinScore()
        );
        QueryResult result = cloudQuery.query(request);
        if (!result.isSuccess() || result.getMatches().isEmpty()) {
            return new ArrayList<>();
        }

        List<TextSegment> segments = new ArrayList<>();
        for (QueryMatch match : result.getMatches()) {
            segments.add(toTextSegment(match));
        }
        return segments;
    }

    private TextSegment toTextSegment(QueryMatch match) {
        String text = match.getText() == null ? "" : match.getText();
        Map<String, String> metadataMap = match.getMetadata() == null ? Map.of() : match.getMetadata();
        String filePath = metadataMap.getOrDefault("filePath", "");
        Metadata metadata = Metadata.from("filePath", filePath);
        return TextSegment.from(text, metadata);
    }

    private void mergeDedup(List<TextSegment> base, List<TextSegment> incoming, int limit) {
        Set<String> seen = new LinkedHashSet<>();
        for (TextSegment segment : base) {
            seen.add(segmentKey(segment));
        }

        for (TextSegment segment : incoming) {
            if (base.size() >= limit) {
                break;
            }
            String key = segmentKey(segment);
            if (!seen.contains(key)) {
                base.add(segment);
                seen.add(key);
            }
        }
    }

    private String segmentKey(TextSegment segment) {
        String filePath = segment.metadata() != null ? segment.metadata().getString("filePath") : "";
        return filePath + "::" + (segment.text() == null ? "" : segment.text());
    }

    private boolean isSupportedFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        for (String ext : SUPPORTED_EXTENSIONS) {
            if (name.endsWith(ext)) {
                return true;
            }
        }
        return false;
    }

    private void upsertToCloud(Embedding embedding, TextSegment segment) {
        String filePath = segment.metadata() != null ? segment.metadata().getString("filePath") : "";
        String text = segment.text() == null ? "" : segment.text();

        Map<String, String> metadata = new LinkedHashMap<>();
        metadata.put("filePath", filePath);

        VectorWriteRequest request = new VectorWriteRequest(
                UUID.randomUUID().toString(),
                toFloatList(embedding.vector()),
                text,
                metadata
        );

        WriteResult result = cloudWriter.upsert(request);
        if (!result.isSuccess()) {
            // Keep local retrieval available even if cloud write fails.
        }
    }

    private List<Float> toFloatList(float[] vector) {
        List<Float> list = new ArrayList<>(vector.length);
        for (float v : vector) {
            list.add(v);
        }
        return list;
    }
}
