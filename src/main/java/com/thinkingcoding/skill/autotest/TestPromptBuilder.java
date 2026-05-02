package com.thinkingcoding.skill.autotest;

public final class TestPromptBuilder {
    private TestPromptBuilder() {
    }

    private static String getLanguage(String sourcePath) {
        if (sourcePath == null) return "未知语言";
        if (sourcePath.endsWith(".java")) return "Java";
        if (sourcePath.endsWith(".py")) return "Python";
        if (sourcePath.endsWith(".go")) return "Go";
        if (sourcePath.endsWith(".js") || sourcePath.endsWith(".ts")) return "JavaScript/TypeScript";
        if (sourcePath.endsWith(".cpp") || sourcePath.endsWith(".cc") || sourcePath.endsWith(".cxx") || sourcePath.endsWith(".hpp")) return "C++";
        if (sourcePath.endsWith(".c") || sourcePath.endsWith(".h")) return "C";
        if (sourcePath.endsWith(".cs")) return "C#";
        if (sourcePath.endsWith(".rs")) return "Rust";
        if (sourcePath.endsWith(".php")) return "PHP";
        if (sourcePath.endsWith(".rb")) return "Ruby";
        return "代码";
    }

    public static String buildInitialPrompt(String sourcePath, String sourceCode, String targetTestPath) {
        String lang = getLanguage(sourcePath);
        return """
                你是一个 %s 测试生成器。
                任务：为下面的源代码生成主流测试框架的测试。

                强约束：
                1) 只输出一个代码块，不要输出解释。
                2) 使用该语言主流的测试框架和 Mock 库。
                3) 测试必须可执行/可编译。
                4) 尽量覆盖主要分支和异常场景。
                5) 测试必须验证真实行为，禁止使用无意义断言（如 assertTrue(true)）。
                6) 必须调用被测类的公开方法并断言其输出/副作用。
                7) 仅生成测试代码，不要修改生产代码。

                源文件路径：%s
                目标测试文件：%s

                源代码：
                ```%s
                %s
                ```
                """.formatted(lang, sourcePath, targetTestPath, lang.toLowerCase(), sourceCode);
    }

    public static String buildFixPrompt(String sourcePath, String sourceCode, String testPath, String testCode, String errorLog) {
        String lang = getLanguage(sourcePath);
        return """
                你是一个 %s 测试修复器。
                任务：基于编译/测试错误日志修复测试代码。

                强约束：
                1) 只输出一个代码块，不要解释。
                2) 保持测试文件路径和相关的命名空间/包名一致。
                3) 仅修改测试代码，不修改生产代码。
                4) 继续使用当前的测试框架和 Mock 库。
                5) 优先根据 [error_summary] 判断根因，再参考 [recent_log_tail]。
                6) 需要确保测试验证真实行为，避免无意义断言。
                7) 必须调用被测类的公开方法并断言其输出/副作用。

                源文件路径：%s
                测试文件路径：%s

                源代码：
                ```%s
                %s
                ```

                当前测试代码：
                ```%s
                %s
                ```

                错误上下文（可能包含 [error_summary] 与 [recent_log_tail]）：
                ```text
                %s
                ```
                """.formatted(lang, sourcePath, testPath, lang.toLowerCase(), sourceCode, lang.toLowerCase(), testCode, errorLog);
    }
}
