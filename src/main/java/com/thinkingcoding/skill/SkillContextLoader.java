package com.thinkingcoding.skill;

import com.thinkingcoding.config.AppConfig;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class SkillContextLoader {
    private SkillContextLoader() {
    }

    public static String resolveBriefContext(AppConfig.SkillConfig skillConfig) {
        if (skillConfig == null) {
            return null;
        }
        String brief = skillConfig.getBriefContext();
        if (brief != null && !brief.isBlank()) {
            return brief;
        }
        String description = skillConfig.getDescription();
        return (description == null || description.isBlank()) ? null : description;
    }

    public static String resolveFullContext(AppConfig.SkillConfig skillConfig) {
        if (skillConfig == null) {
            return null;
        }
        String path = skillConfig.getFullContextPath();
        if (path != null && !path.isBlank()) {
            try {
                Path fullPath = Paths.get(path).toAbsolutePath().normalize();
                if (Files.exists(fullPath)) {
                    String content = Files.readString(fullPath);
                    return content == null || content.isBlank() ? null : content;
                }
            } catch (Exception ignored) {
                return null;
            }
        }
        String full = skillConfig.getFullContext();
        return (full == null || full.isBlank()) ? null : full;
    }
}

