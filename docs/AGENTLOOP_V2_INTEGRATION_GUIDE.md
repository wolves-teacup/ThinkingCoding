# AgentLoop V2 集成指南

## 🎯 目标

在 `ThinkingCodingCommand` 中集成 V2 AgentLoop，支持运行时切换 legacy/v2。

## 📝 集成步骤

### 步骤 1: 添加字段

在 `ThinkingCodingCommand` 类中添加以下字段：

```java
// V2 AgentLoop 工厂
private AgentLoopFactory agentLoopFactory;
```

### 步骤 2: 初始化 Factory

在构造函数或 `call()` 方法开始时初始化：

```java
public ThinkingCodingCommand(ThinkingCodingContext context) {
    this.context = context;
    this.directCommandExecutor = new DirectCommandExecutor(context);
    
    // 初始化 V2 Factory（默认使用 legacy）
    this.agentLoopFactory = new AgentLoopFactory(context);
}
```

### 步骤 3: 添加命令行选项

添加用于切换 V2 的选项：

```java
@CommandLine.Option(names = {"--agent-loop"}, description = "AgentLoop version: legacy|v2 (default: legacy)")
private String agentLoopVersion = "legacy";

@CommandLine.Option(names = {"--max-react-steps"}, description = "Max ReAct steps per turn (V2 only)")
private int maxReactSteps = 6;

@CommandLine.Option(names = {"--auto-approve"}, description = "Enable auto-approve mode for tools (V2 only)")
private boolean autoApprove = false;
```

### 步骤 4: 修改 AgentLoop 创建逻辑

找到第 167 行附近的代码：

**原代码：**
```java
currentAgentLoop = new AgentLoop(context, currentSessionId, modelToUse);
currentAgentLoop.loadHistory(history);
```

**修改为：**
```java
// 根据配置选择 AgentLoop 版本
if ("v2".equalsIgnoreCase(agentLoopVersion)) {
    // 启用 V2
    agentLoopFactory.enableV2();
    
    // 配置 V2 参数
    AgentConfig config = agentLoopFactory.getV2Config();
    config.setMaxReActStepsPerTurn(maxReactSteps);
    config.setAutoApproveDefault(autoApprove);
    
    // 创建 V2 AgentOrchestrator
    Object agent = agentLoopFactory.createAgentLoop(currentSessionId, modelToUse);
    
    if (agent instanceof AgentOrchestrator) {
        AgentOrchestrator orchestrator = (AgentOrchestrator) agent;
        orchestrator.loadHistory(history);
        
        ui.displaySuccess("✅ 已启用 AgentLoop V2 (Plan → Execute + ReAct → Steering)");
        ui.displayInfo("   - Max ReAct Steps: " + maxReactSteps);
        ui.displayInfo("   - Auto Approve: " + (autoApprove ? "ON" : "OFF"));
    }
} else {
    // 使用 Legacy
    currentAgentLoop = new AgentLoop(context, currentSessionId, modelToUse);
    currentAgentLoop.loadHistory(history);
    
    if ("v2".equalsIgnoreCase(agentLoopVersion)) {
        ui.displayWarning("⚠️  V2 请求失败，回退到 Legacy 模式");
    }
}
```

### 步骤 5: 修改输入处理逻辑

在 `startInteractiveMode` 方法中，需要区分处理 V2 和 Legacy：

**原代码：**
```java
currentAgentLoop.processInput(input);
```

**修改为：**
```java
// 检查是否是 V2 AgentOrchestrator
if (agentLoopFactory.isV2Enabled()) {
    // 获取当前的 AgentOrchestrator
    Object agent = agentLoopFactory.createAgentLoop(currentSessionId, modelToUse);
    if (agent instanceof AgentOrchestrator) {
        ((AgentOrchestrator) agent).onUserInput(input);
    }
} else {
    // 使用 Legacy
    currentAgentLoop.processInput(input);
}
```

### 步骤 6: 添加 Steering 命令处理（可选）

如果需要在交互模式中支持 steering 命令（如 `/stop`, `/cancel`），可以添加特殊命令检测：

```java
private void handleSpecialCommands(String input, AgentOrchestrator orchestrator) {
    if (input.startsWith("/")) {
        String command = input.substring(1).trim().toLowerCase();
        
        switch (command) {
            case "stop":
                orchestrator.onSteeringCommand(SteeringCommand.STOP_GENERATION);
                break;
            case "cancel":
                orchestrator.onSteeringCommand(SteeringCommand.CANCEL_TURN);
                break;
            case "auto-approve-on":
                orchestrator.onSteeringCommand(SteeringCommand.SET_AUTO_APPROVE_ON);
                context.getUi().displaySuccess("Auto-approve enabled");
                break;
            case "auto-approve-off":
                orchestrator.onSteeringCommand(SteeringCommand.SET_AUTO_APPROVE_OFF);
                context.getUi().displaySuccess("Auto-approve disabled");
                break;
            default:
                context.getUi().displayError("Unknown command: " + command);
        }
    }
}
```

## 🚀 完整示例

以下是修改后的关键代码段：

```java
package com.thinkingcoding.cli;

import com.thinkingcoding.agentloop.v2.orchestrator.AgentConfig;
import com.thinkingcoding.agentloop.v2.orchestrator.AgentLoopFactory;
import com.thinkingcoding.agentloop.v2.orchestrator.AgentOrchestrator;
import com.thinkingcoding.core.AgentLoop;
// ... 其他 imports

@CommandLine.Command(name = "thinking", mixinStandardHelpOptions = true,
        description = "ThinkingCoding CLI - Interactive Code Assistant")
public class ThinkingCodingCommand implements Callable<Integer> {

    private final ThinkingCodingContext context;
    private AgentLoop currentAgentLoop;
    private AgentLoopFactory agentLoopFactory;  // 🔥 新增
    private String currentSessionId;
    
    // ... 其他字段

    @CommandLine.Option(names = {"--agent-loop"}, description = "AgentLoop version: legacy|v2")
    private String agentLoopVersion = "legacy";

    @CommandLine.Option(names = {"--max-react-steps"}, description = "Max ReAct steps (V2)")
    private int maxReactSteps = 6;

    @CommandLine.Option(names = {"--auto-approve"}, description = "Auto-approve tools (V2)")
    private boolean autoApprove = false;

    public ThinkingCodingCommand(ThinkingCodingContext context) {
        this.context = context;
        this.directCommandExecutor = new DirectCommandExecutor(context);
        this.agentLoopFactory = new AgentLoopFactory(context);  // 🔥 初始化
    }

    @Override
    public Integer call() {
        try {
            // ... 前面的代码保持不变
            
            // 🔥 创建 AgentLoop（支持 V2）
            createAgentLoop(modelToUse, history);
            
            // ... 后面的代码保持不变
        }
    }
    
    /**
     * 🔥 创建 AgentLoop（支持 legacy/v2 切换）
     */
    private void createAgentLoop(String modelToUse, List<ChatMessage> history) {
        if ("v2".equalsIgnoreCase(agentLoopVersion)) {
            // 启用 V2
            agentLoopFactory.enableV2();
            
            // 配置 V2
            AgentConfig config = agentLoopFactory.getV2Config();
            config.setMaxReActStepsPerTurn(maxReactSteps);
            config.setAutoApproveDefault(autoApprove);
            
            // 创建 V2
            Object agent = agentLoopFactory.createAgentLoop(currentSessionId, modelToUse);
            
            if (agent instanceof AgentOrchestrator) {
                AgentOrchestrator orchestrator = (AgentOrchestrator) agent;
                orchestrator.loadHistory(history);
                
                context.getUi().displaySuccess("✅ AgentLoop V2 已启用");
                context.getUi().displayInfo("   架构: Plan → Execute + ReAct → Steering");
                context.getUi().displayInfo("   - Max ReAct Steps: " + maxReactSteps);
                context.getUi().displayInfo("   - Auto Approve: " + (autoApprove ? "ON" : "OFF"));
            }
        } else {
            // 使用 Legacy
            currentAgentLoop = new AgentLoop(context, currentSessionId, modelToUse);
            currentAgentLoop.loadHistory(history);
        }
    }
    
    /**
     * 🔥 处理用户输入（区分 V2/Legacy）
     */
    private void processUserInput(String input) {
        if (agentLoopFactory.isV2Enabled()) {
            // V2 处理
            Object agent = agentLoopFactory.createAgentLoop(currentSessionId, 
                model != null ? model : context.getAppConfig().getDefaultModel());
            
            if (agent instanceof AgentOrchestrator) {
                // 检查特殊命令
                if (input.startsWith("/")) {
                    handleSteeringCommand(input, (AgentOrchestrator) agent);
                } else {
                    ((AgentOrchestrator) agent).onUserInput(input);
                }
            }
        } else {
            // Legacy 处理
            currentAgentLoop.processInput(input);
        }
    }
    
    /**
     * 🔥 处理 Steering 命令
     */
    private void handleSteeringCommand(String input, AgentOrchestrator orchestrator) {
        String command = input.substring(1).trim().toLowerCase();
        
        switch (command) {
            case "stop":
                orchestrator.onSteeringCommand(SteeringCommand.STOP_GENERATION);
                break;
            case "cancel":
                orchestrator.onSteeringCommand(SteeringCommand.CANCEL_TURN);
                break;
            case "auto-approve-on":
                orchestrator.setAutoApprove(true);
                context.getUi().displaySuccess("Auto-approve ON");
                break;
            case "auto-approve-off":
                orchestrator.setAutoApprove(false);
                context.getUi().displaySuccess("Auto-approve OFF");
                break;
            default:
                context.getUi().displayError("Unknown command: " + command);
        }
    }
}
```

## 🧪 测试命令

### 使用 Legacy（默认）
```bash
./thinking --prompt "创建一个 Hello World 程序"
```

### 使用 V2
```bash
./thinking --agent-loop v2 --prompt "创建一个 Hello World 程序"
```

### 使用 V2 + 自定义配置
```bash
./thinking --agent-loop v2 --max-react-steps 10 --auto-approve --prompt "创建并运行一个 Java 程序"
```

### 交互模式中使用 V2
```bash
./thinking --agent-loop v2
thinking> 创建一个计算器程序
thinking> /stop          # 停止当前生成
thinking> /auto-approve-on  # 开启自动批准
thinking> 删除临时文件      # 将自动执行，无需确认
```

## ⚠️ 注意事项

1. **向后兼容**: 默认使用 legacy，确保现有功能不受影响
2. **状态管理**: V2 的 AgentOrchestrator 是无状态的，每次调用需要重新创建或保持引用
3. **会话保存**: V2 会自动保存会话，与 legacy 行为一致
4. **错误处理**: V2 有更详细的错误追踪，便于调试

## 🎨 最佳实践

1. **渐进式迁移**: 先在测试环境启用 V2，验证稳定后再推广
2. **监控日志**: 记录 V2 的使用情况和性能指标
3. **用户反馈**: 收集用户对三阶段流水线的反馈
4. **配置优化**: 根据实际使用情况调整 maxReActSteps 等参数

## 📊 性能对比

| 指标 | Legacy | V2 |
|------|--------|----|
| 首次响应时间 | ~相同 | ~相同 |
| 工具执行延迟 | 低 | 略高（多一层抽象） |
| 多步任务效率 | 中 | 高（ReAct 闭环） |
| 内存占用 | 低 | 略高 |
| 可维护性 | 低 | 高 |

---

**更新日期**: 2026-04-24  
**适用版本**: ThinkingCoding 1.0.0+
