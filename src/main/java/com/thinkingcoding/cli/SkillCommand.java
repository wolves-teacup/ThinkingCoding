package com.thinkingcoding.cli;

import com.thinkingcoding.core.ThinkingCodingContext;
import com.thinkingcoding.skill.Skill;
import com.thinkingcoding.skill.SkillExecutionContext;
import com.thinkingcoding.skill.SkillResult;
import com.thinkingcoding.skill.SkillContextLoader;
import com.thinkingcoding.config.AppConfig.SkillConfig;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

import java.util.concurrent.Callable;

@Command(name = "skill", description = "Run built-in skills")
public class SkillCommand implements Callable<Integer> {
    private final ThinkingCodingContext context;

    @Option(names = {"--name"}, description = "Skill name", defaultValue = "autotest")
    private String name;

    @Option(names = {"--source"}, description = "Target source file path", required = true)
    private String source;

    @Option(names = {"--test"}, description = "Target test file path (optional)")
    private String test;

    @Option(names = {"--model"}, description = "Model name override")
    private String model;

    @Option(names = {"--max-retries"}, description = "Max fix retries (0 uses config value)", defaultValue = "0")
    private int maxRetries;

    public SkillCommand(ThinkingCodingContext context) {
        this.context = context;
    }

    @Override
    public Integer call() {
        // 🔥 创建并初始化 SkillRegistry
        com.thinkingcoding.skill.SkillRegistry skillRegistry = new com.thinkingcoding.skill.SkillRegistry();
        
        // 🔥 注册 AutoTestSkill
        com.thinkingcoding.skill.autotest.AutoTestSkill autoTestSkill = new com.thinkingcoding.skill.autotest.AutoTestSkill(
            context.getAiService(),
            context.getToolRegistry(),
            context.getAppConfig()
        );
        skillRegistry.register(autoTestSkill);
        
        // 🔥 获取 skill
        Skill skill = skillRegistry.getSkill(name);
        if (skill == null) {
            context.getUi().displayError("Unknown skill: " + name);
            return 1;
        }

        SkillConfig skillConfig = context.getAppConfig().getSkills().stream().filter(s -> name.equalsIgnoreCase(s.getName())).findFirst().orElse(null);

        if (skillConfig != null && !skillConfig.isEnabled()) {
            context.getUi().displayError(name + " skill is disabled in config.yaml");
            return 1;
        }

        context.getUi().displayInfo("Running skill: " + name);
        int configRetries = (skillConfig != null && skillConfig.getConfig().containsKey("maxRetries")) ? (Integer) skillConfig.getConfig().get("maxRetries") : 3;
        int retries = maxRetries > 0 ? maxRetries : configRetries;
        String skillContext = SkillContextLoader.resolveFullContext(skillConfig);
        SkillExecutionContext executionContext = new SkillExecutionContext(source, test, model, retries, skillContext);
        SkillResult result = skill.execute(executionContext);

        if (result.isSuccess()) {
            context.getUi().displaySuccess(result.getMessage());
            if (result.getTestFilePath() != null) {
                context.getUi().displayInfo("Generated test file: " + result.getTestFilePath());
            }
            context.getUi().displayInfo("Attempts: " + result.getAttempts());
            return 0;
        }

        context.getUi().displayError(result.getMessage());
        if (result.getTestFilePath() != null) {
            context.getUi().displayInfo("Last test file: " + result.getTestFilePath());
        }
        if (result.getLastErrorLog() != null && !result.getLastErrorLog().isBlank()) {
            context.getUi().displayError("Last error log:\n" + result.getLastErrorLog());
        }
        return 1;
    }
}

