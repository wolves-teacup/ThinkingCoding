package com.thinkingcoding.agentloop.v2;

import com.thinkingcoding.agentloop.v2.execute.ToolResolver;
import com.thinkingcoding.model.ToolCall;
import com.thinkingcoding.tools.BaseTool;
import com.thinkingcoding.tools.ToolRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * ToolResolver 单元测试
 * 验证仅原生工具名解析行为
 */
public class ToolResolverTest {

    private ToolResolver toolResolver;

    @Mock
    private ToolRegistry toolRegistry;

    @Mock
    private BaseTool fileManagerTool;

    @Mock
    private BaseTool commandExecutorTool;

    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
        toolResolver = new ToolResolver();

        // 模拟工具注册
        when(toolRegistry.getTool("file_manager")).thenReturn(fileManagerTool);
        when(toolRegistry.getTool("command_executor")).thenReturn(commandExecutorTool);
    }

    @Test
    public void testResolveNativeFileManager() {
        // 准备
        Map<String, Object> params = new HashMap<>();
        params.put("command", "write");
        params.put("path", "test.java");
        params.put("content", "public class Test {}");
        ToolCall toolCall = new ToolCall("file_manager", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("file_manager", resolved.executableToolName());
        assertEquals(fileManagerTool, resolved.tool());
        assertEquals("write", resolved.parameters().get("command"));
        assertEquals("test.java", resolved.parameters().get("path"));
    }

    @Test
    public void testResolveNativeCommandExecutor() {
        // 准备
        Map<String, Object> params = new HashMap<>();
        params.put("command", "ls -la");
        ToolCall toolCall = new ToolCall("command_executor", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("command_executor", resolved.executableToolName());
        assertEquals(commandExecutorTool, resolved.tool());
        assertEquals("ls -la", resolved.parameters().get("command"));
    }

    @Test
    public void testResolveAliasWriteFileShouldFail() {
        // 准备
        Map<String, Object> params = new HashMap<>();
        params.put("path", "test.java");
        params.put("content", "public class Test {}");
        ToolCall toolCall = new ToolCall("write_file", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("write_file", resolved.executableToolName());
        assertNull(resolved.tool());
    }

    @Test
    public void testResolveAliasBashShouldFail() {
        // 准备
        Map<String, Object> params = new HashMap<>();
        params.put("command", "ls -la");
        ToolCall toolCall = new ToolCall("bash", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("bash", resolved.executableToolName());
        assertNull(resolved.tool());
    }

    @Test
    public void testResolveDirectTool() {
        // 准备 - 直接调用已存在的工具
        BaseTool directTool = mock(BaseTool.class);
        when(toolRegistry.getTool("direct_tool")).thenReturn(directTool);
        
        Map<String, Object> params = new HashMap<>();
        ToolCall toolCall = new ToolCall("direct_tool", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("direct_tool", resolved.executableToolName());
        assertEquals(directTool, resolved.tool());
    }

    @Test
    public void testResolveNonExistentTool() {
        // 准备
        Map<String, Object> params = new HashMap<>();
        ToolCall toolCall = new ToolCall("non_existent", params, null, false, 0, true);

        // 执行
        ToolResolver.ResolvedTool resolved = toolResolver.resolve(toolCall, toolRegistry);

        // 验证
        assertEquals("non_existent", resolved.executableToolName());
        assertNull(resolved.tool());
    }
}
