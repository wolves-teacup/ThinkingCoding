package com.thinkingcoding.rag.codegraph;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class IndexOptions {
    private final boolean includeTests;
    private final long maxFileSizeBytes;
    private final Set<String> languages;
    private final boolean treeSitterEnabled;
    private final String treeSitterCommand;
    private final Set<String> includeExtensions;
    private final Set<String> excludeExtensions;

    public IndexOptions(boolean includeTests, long maxFileSizeBytes) {
        this(includeTests, maxFileSizeBytes, Collections.emptySet(), true, "tree-sitter",
                Collections.emptySet(), Collections.emptySet());
    }

    public IndexOptions(boolean includeTests,
                        long maxFileSizeBytes,
                        Set<String> languages,
                        boolean treeSitterEnabled,
                        String treeSitterCommand,
                        Set<String> includeExtensions,
                        Set<String> excludeExtensions) {
        this.includeTests = includeTests;
        this.maxFileSizeBytes = maxFileSizeBytes;
        this.languages = normalizeLowercase(languages);
        this.treeSitterEnabled = treeSitterEnabled;
        this.treeSitterCommand = treeSitterCommand == null ? "tree-sitter" : treeSitterCommand;
        this.includeExtensions = normalizeExtensions(includeExtensions);
        this.excludeExtensions = normalizeExtensions(excludeExtensions);
    }

    public boolean isIncludeTests() {
        return includeTests;
    }

    public long getMaxFileSizeBytes() {
        return maxFileSizeBytes;
    }

    public Set<String> getLanguages() {
        return languages;
    }

    public boolean isTreeSitterEnabled() {
        return treeSitterEnabled;
    }

    public String getTreeSitterCommand() {
        return treeSitterCommand;
    }

    public Set<String> getIncludeExtensions() {
        return includeExtensions;
    }

    public Set<String> getExcludeExtensions() {
        return excludeExtensions;
    }

    private Set<String> normalizeLowercase(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                normalized.add(value.toLowerCase(Locale.ROOT));
            }
        }
        return Collections.unmodifiableSet(normalized);
    }

    private Set<String> normalizeExtensions(Set<String> values) {
        if (values == null || values.isEmpty()) {
            return Collections.emptySet();
        }
        Set<String> normalized = new HashSet<>();
        for (String value : values) {
            if (value == null || value.isBlank()) {
                continue;
            }
            String trimmed = value.trim().toLowerCase(Locale.ROOT);
            if (!trimmed.startsWith(".")) {
                trimmed = "." + trimmed;
            }
            normalized.add(trimmed);
        }
        return Collections.unmodifiableSet(normalized);
    }
}
