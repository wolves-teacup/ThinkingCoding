package com.thinkingcoding;

import com.thinkingcoding.cli.ThinkingCodingCommand;
import com.thinkingcoding.cli.SessionCommand;
import com.thinkingcoding.cli.ConfigCommand;
import com.thinkingcoding.core.ThinkingCodingContext;
import picocli.CommandLine;

/**
 * 创建整个应用的根上下文，作为所有组件的容器
 *
 * 调用 initialize() 方法加载配置、注册工具、连接MCP服务器
 *
 * 建立命令解析框架，为后续的命令路由做准备
 */
public class ThinkingCodingCLI {
    public static void main(String[] args) {
        // 设置默认异常处理
        Thread.setDefaultUncaughtExceptionHandler((thread, throwable) -> {
            System.err.println(" 发生未预期错误: " + throwable.getMessage());
            System.exit(1);
        });

        // 创建并初始化应用上下文
        ThinkingCodingContext context = ThinkingCodingContext.initialize();

        // 设置Picocli命令解析器，注册所有命令
        CommandLine commandLine = new CommandLine(new ThinkingCodingCommand(context));
        commandLine.addSubcommand("session", new SessionCommand(context));
        commandLine.addSubcommand("config", new ConfigCommand(context));

        // 执行命令解析和路由
        int exitCode = commandLine.execute(args);
        System.exit(exitCode);
    }
}