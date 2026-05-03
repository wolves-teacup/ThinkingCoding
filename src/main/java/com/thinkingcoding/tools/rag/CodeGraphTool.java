package com.thinkingcoding.tools.rag;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.thinkingcoding.config.AppConfig;
import com.thinkingcoding.model.ToolResult;
import com.thinkingcoding.rag.codegraph.CodeGraphIndex;
import com.thinkingcoding.rag.codegraph.CodeGraphSymbol;
import com.thinkingcoding.rag.codegraph.IndexOptions;
import com.thinkingcoding.rag.codegraph.JavaAstIndexer;
import com.thinkingcoding.rag.codegraph.MultiLanguageAstIndexer;
import com.thinkingcoding.rag.codegraph.ReferenceKind;
import com.thinkingcoding.rag.codegraph.TreeSitterCliAstIndexer;
import com.thinkingcoding.tools.BaseTool;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class CodeGraphTool extends BaseTool {
    private static final long DEFAULT_MAX_FILE_BYTES = 2 * 1024 * 1024;
    private static final int DEFAULT_MAX_DEPENDENCIES = 12;
    private static final int DEFAULT_MAX_MEMBERS = 12;

    private final AppConfig appConfig;
    private final ObjectMapper mapper = new ObjectMapper();
    private final MultiLanguageAstIndexer indexer = new MultiLanguageAstIndexer();

    private CodeGraphIndex cachedIndex;
    private Path cachedRoot;
    private boolean cachedIncludeTests;

    public CodeGraphTool(AppConfig appConfig) {
        super("code_graph", "Build and query a lightweight code graph across Java source files");
        this.appConfig = appConfig;
        this.indexer.register(new JavaAstIndexer());
        this.indexer.register(new TreeSitterCliAstIndexer());
    }

    @Override
    public ToolResult execute(String input) {
        long startTime = System.currentTimeMillis();
        try {
            Map<String, Object> params = parseParams(input);
            String target = stringParam(params, "target", null);
            if (target == null || target.isBlank()) {
                return error("Missing required parameter: target", System.currentTimeMillis() - startTime);
            }

            String workspace = stringParam(params, "workspace", resolveWorkspace());
            boolean refresh = booleanParam(params, "refresh", false);
            boolean includeTests = booleanParam(params, "includeTests", false);
            int maxDependencies = intParam(params, "maxDependencies", DEFAULT_MAX_DEPENDENCIES);
            int maxMembers = intParam(params, "maxMembers", DEFAULT_MAX_MEMBERS);

            CodeGraphIndex index = getIndex(Path.of(workspace), includeTests, refresh);
            var targetSymbol = index.findTarget(target);
            if (targetSymbol.isEmpty()) {
                return error("Target not found in code graph: " + target, System.currentTimeMillis() - startTime);
            }

            String output = render(targetSymbol.get(), index, maxDependencies, maxMembers);
            return success(output, System.currentTimeMillis() - startTime);
        } catch (Exception e) {
            return error("Code graph lookup failed: " + e.getMessage(), System.currentTimeMillis() - startTime);
        }
    }

    @Override
    public String getCategory() {
        return "rag";
    }

    @Override
    public boolean isEnabled() {
        return appConfig == null || appConfig.getTools().getCodeGraph().isEnabled();
    }

    @Override
    public Object getInputSchema() {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("description", "Query the Java code graph for a target class or file");

        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("target", field("string", "Target class name, qualified name, or .java file path"));
        properties.put("workspace", field("string", "Workspace root; defaults to rag.workspace or user.dir"));
        properties.put("refresh", field("boolean", "Rebuild the index before querying"));
        properties.put("includeTests", field("boolean", "Include src/test/java in the index"));
        properties.put("maxDependencies", field("integer", "Maximum dependency symbols to return"));
        properties.put("maxMembers", field("integer", "Maximum public members per symbol"));

        schema.put("properties", properties);
        schema.put("required", new String[]{"target"});
        schema.put("additionalProperties", false);
        return schema;
    }

    private Map<String, Object> field(String type, String description) {
        Map<String, Object> field = new LinkedHashMap<>();
        field.put("type", type);
        field.put("description", description);
        return field;
    }

    private Map<String, Object> parseParams(String input) throws Exception {
        if (input != null && input.trim().startsWith("{")) {
            return mapper.readValue(input, Map.class);
        }
        Map<String, Object> fallback = new LinkedHashMap<>();
        fallback.put("target", input);
        return fallback;
    }

    private String resolveWorkspace() {
        if (appConfig != null && appConfig.getRag() != null && appConfig.getRag().getWorkspace() != null) {
            return appConfig.getRag().getWorkspace();
        }
        return System.getProperty("user.dir");
    }

    private synchronized CodeGraphIndex getIndex(Path workspace, boolean includeTests, boolean refresh) {
        Path root = workspace.toAbsolutePath().normalize();
        if (!refresh && cachedIndex != null && root.equals(cachedRoot) && cachedIncludeTests == includeTests) {
            return cachedIndex;
        }
        IndexOptions options = buildIndexOptions(includeTests);
        CodeGraphIndex index = indexer.indexWorkspace(root, options);
        cachedIndex = index;
        cachedRoot = root;
        cachedIncludeTests = includeTests;
        return index;
    }

    private IndexOptions buildIndexOptions(boolean includeTests) {
        long maxFileSizeBytes = DEFAULT_MAX_FILE_BYTES;
        if (appConfig != null && appConfig.getTools() != null && appConfig.getTools().getCodeGraph() != null) {
            Long configured = appConfig.getTools().getCodeGraph().getMaxFileSize();
            if (configured != null && configured > 0) {
                maxFileSizeBytes = configured;
            }
        }
        Set<String> languages = readLanguages();
        AppConfig.RagCodeGraphConfig graphConfig = appConfig == null ? null : appConfig.getRag().getCodeGraph();
        boolean treeSitterEnabled = graphConfig == null || graphConfig.isTreeSitterEnabled();
        String treeSitterCommand = graphConfig == null ? null : graphConfig.getTreeSitterCommand();
        Set<String> includeExtensions = toLowerSet(graphConfig == null ? null : graphConfig.getIncludeExtensions());
        Set<String> excludeExtensions = toLowerSet(graphConfig == null ? null : graphConfig.getExcludeExtensions());
        if (!excludeExtensions.contains(".java")) {
            excludeExtensions = new LinkedHashSet<>(excludeExtensions);
            excludeExtensions.add(".java");
            excludeExtensions = Collections.unmodifiableSet(excludeExtensions);
        }
        return new IndexOptions(includeTests, maxFileSizeBytes, languages, treeSitterEnabled, treeSitterCommand,
                includeExtensions, excludeExtensions);
    }

    private Set<String> readLanguages() {
        if (appConfig == null || appConfig.getRag() == null || appConfig.getRag().getCodeGraph() == null) {
            return Collections.emptySet();
        }
        return toLowerSet(appConfig.getRag().getCodeGraph().getLanguages());
    }

    private Set<String> toLowerSet(List<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private String render(CodeGraphSymbol target,
                          CodeGraphIndex index,
                          int maxDependencies,
                          int maxMembers) {
        StringBuilder sb = new StringBuilder();
        sb.append("Target: ").append(target.getQualifiedName()).append(" (" + target.getKind() + ")\n");
        if (target.getLanguage() != null && !target.getLanguage().isBlank()) {
            sb.append("Language: ").append(target.getLanguage()).append("\n");
        }
        sb.append("File: ").append(target.getFilePath()).append("\n");
        if (target.getDeclaration() != null && !target.getDeclaration().isBlank()) {
            sb.append("Declaration: ").append(target.getDeclaration()).append("\n");
        }

        appendMembers(sb, "Public fields", target.getPublicFields(), maxMembers);
        appendMembers(sb, "Public members", target.getPublicMembers(), maxMembers);

        List<CodeGraphSymbol> dependencies = index.resolveDependencies(target, maxDependencies);
        sb.append("Dependencies (" + dependencies.size() + "):" + "\n");
        for (CodeGraphSymbol dep : dependencies) {
            EnumSet<ReferenceKind> kinds = target.getReferenceKinds().get(dep.getQualifiedName());
            if (kinds == null) {
                kinds = target.getReferenceKinds().get(dep.getName());
            }
            sb.append("- ").append(dep.getQualifiedName());
            sb.append(" [").append(formatKinds(kinds)).append("]\n");
            appendMembers(sb, "  members", dep.getPublicMembers(), Math.max(3, maxMembers / 2));
        }

        List<String> unresolved = index.unresolvedReferences(target, 8);
        if (!unresolved.isEmpty()) {
            sb.append("Unresolved references: ").append(String.join(", ", unresolved)).append("\n");
        }
        sb.append("Index symbols: ").append(index.getSymbolCount());
        return sb.toString();
    }

    private void appendMembers(StringBuilder sb, String label, List<String> members, int limit) {
        if (members == null || members.isEmpty()) {
            return;
        }
        sb.append(label).append(":\n");
        int count = 0;
        for (String member : members) {
            if (count >= limit) {
                sb.append("  ...").append("\n");
                break;
            }
            sb.append("  - ").append(member).append("\n");
            count++;
        }
    }

    private String formatKinds(EnumSet<ReferenceKind> kinds) {
        if (kinds == null || kinds.isEmpty()) {
            return "unknown";
        }
        List<String> names = new ArrayList<>();
        for (ReferenceKind kind : kinds) {
            names.add(kind.name());
        }
        return String.join(", ", names);
    }

    private String stringParam(Map<String, Object> params, String key, String defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? defaultValue : text;
    }

    private boolean booleanParam(Map<String, Object> params, String key, boolean defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }

    private int intParam(Map<String, Object> params, String key, int defaultValue) {
        Object value = params.get(key);
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return Math.max(1, number.intValue());
        }
        try {
            return Math.max(1, Integer.parseInt(String.valueOf(value)));
        } catch (Exception ignored) {
            return defaultValue;
        }
    }
}

