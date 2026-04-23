package com.thinkingcoding.skill.autotest;

public final class TestPromptBuilder {
    private TestPromptBuilder() {
    }

    public static String buildInitialPrompt(String sourcePath, String sourceCode, String targetTestPath) {
        return """
                你是一个 Java 测试生成器。
                任务：为下面的源代码生成 JUnit 5 + Mockito 测试。

                强约束：
                1) 只输出一个 Java 代码块，不要输出解释。
                2) 必须使用 JUnit 5（org.junit.jupiter）和 Mockito（org.mockito）。
                3) 测试类名、包名、import 必须可编译。
                4) 尽量覆盖主要分支和异常场景。
                5) 仅生成测试代码，不要修改生产代码。

                源文件路径：%s
                目标测试文件：%s

                源代码：
                ```java
                %s
                ```
                """.formatted(sourcePath, targetTestPath, sourceCode);
    }

    public static String buildFixPrompt(String sourcePath, String sourceCode, String testPath, String testCode, String errorLog) {
        return """
                你是一个 Java 测试修复器。
                任务：基于编译/测试错误日志修复测试代码。

                强约束：
                1) 只输出一个 Java 代码块，不要解释。
                2) 保持测试文件路径对应的 package 与类名一致。
                3) 仅修改测试代码，不修改生产代码。
                4) 继续使用 JUnit 5 + Mockito。
                5) 优先根据 [error_summary] 判断根因，再参考 [recent_log_tail]。

                源文件路径：%s
                测试文件路径：%s

                源代码：
                ```java
                %s
                ```

                当前测试代码：
                ```java
                %s
                ```

                错误上下文（可能包含 [error_summary] 与 [recent_log_tail]）：
                ```text
                %s
                ```
                """.formatted(sourcePath, testPath, sourceCode, testCode, errorLog);
    }
}

