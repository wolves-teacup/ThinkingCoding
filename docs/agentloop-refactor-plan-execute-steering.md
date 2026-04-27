# AgentLoop 三阶段重构方案（Plan → Execute+ReAct → Mid‑Turn Steering）

> 适用项目：ThinkingCoding（Java 17 / Picocli / JLine / LangChain4j / MCP）  
> 文档目的：将当前 `core.AgentLoop` 的单体式回合逻辑，重构为“先规划、再执行与反思、最后可中途干预”的可扩展架构，并给出**模块划分、接口草案、迁移路径与测试策略**。

---

## 0. 背景与现状

当前主链路：

- CLI 入口：`com.thinkingcoding.cli.ThinkingCodingCommand`
- 回合循环：`com.thinkingcoding.core.AgentLoop`
- 模型调用：`com.thinkingcoding.service.LangChainService`（流式输出 + LangChain4j 原生 tool calling）
- 工具执行确认：`com.thinkingcoding.core.ToolExecutionConfirmation`
- 工具与执行：`com.thinkingcoding.tools.*` + `ToolRegistry`

现状问题（与目标三阶段的差距）：

1. **Plan 缺失**：用户输入后并不会先得到“全局任务规划”，而是直接对话/直接触发工具调用。
2. **Execute+ReAct 不完整**：工具执行结果虽然会写入 `history`，但缺少“Observation → 再规划 → 再执行”的闭环驱动器。
3. **Mid‑Turn Steering 过弱**：目前主要是“执行前 1/2/3 确认”，缺少更通用的中途干预（暂停/跳过/改参数/重规划/终止回合）。

---

## 1. 目标：三阶段回合流水线

对每个用户请求（turn）执行如下流水线：

1) **Plan（全局任务规划）**：
- 输出：计划（可展示给用户）+ 初步工具意图（toolCalls 或 step→tool hints）

2) **Execute + ReAct（细粒度工具执行与反思）**：
- 按步骤执行工具
- 每次工具执行后把结果作为 Observation 写回上下文
- 必要时再调用模型进行下一步规划/修正

> ✅ **强约束（默认安全策略）**：模型只能“提出 ToolCall（工具名+参数）”，**不允许直接执行**。
> 所有工具执行必须经过 **Mid‑Turn Steering** 的用户许可（确认/拒绝/跳过/编辑参数等）后才能落地。

3) **Mid‑Turn Steering（人机协同干预）**：
- 在关键点把控制权交给用户（执行前/失败后/计划偏离时）
- 支持 stop、跳过、编辑参数、重规划、取消本轮等

> ✅ **工具执行门禁**：Steering 阶段是工具执行的唯一入口（Policy Gate）。
> 默认任何 ToolCall 都需要用户确认后才会进入执行引擎。

---

## 2. 总体模块划分（建议新增 V2 包）

为了**可回滚**与**渐进迁移**，建议新增一套 V2 实现，保持 legacy `core.AgentLoop` 不被一次性推翻。

### 2.1 包结构建议（新增）

建议新增：`com.thinkingcoding.agentloop.v2`，并按职责拆分子包：

```
com.thinkingcoding.agentloop.v2
 ├─ orchestrator   # 回合编排（Plan→Execute→Steer）
 ├─ plan           # 规划阶段（Planner / PlanResult）
 ├─ execute        # 执行 + ReAct（工具执行引擎、ReAct驱动）
 ├─ steer          # Mid‑Turn Steering（确认/跳过/重规划/停止）
 ├─ gateway        # 对现有 SessionService、UI、AIService 的适配层
 └─ model          # DTO：TurnContext/Plan/Trace/Decision 等
```

> 说明：将 UI / JLine / Picocli 相关依赖尽可能“外置到 gateway/steer 实现层”，核心编排与执行保持可测试。

---

## 3. 模块清单（类级别）与职责说明

下面给出一套“可落地”的模块清单（建议最小实现先覆盖核心链路，后续再扩展）。

### 3.1 Orchestrator（总编排）

**包：** `com.thinkingcoding.agentloop.v2.orchestrator`

- `AgentOrchestrator`（V2 的新 AgentLoop）
  - 职责：串联三阶段；维护 turn 状态；决定保存会话时机；提供对外统一入口 `onUserInput()`。
  - 依赖：`Planner`、`ReActDriver`、`ToolExecutionEngine`、`SteeringHandle`、`SessionGateway`、`AgentEventSink`。

- `AgentLoopFactory`（可选，但强烈建议）
  - 职责：根据开关创建 legacy `core.AgentLoop` 或 V2 `AgentOrchestrator`。
  - 价值：随时回滚，不影响 CLI 入口。

- `AgentConfig`
  - 职责：集中管理 V2 参数，例如：
    - `enabled`（是否启用 V2）
    - `maxReActStepsPerTurn`
    - `maxToolCallsPerPlan`
    - `autoApproveDefault`
    - `steeringMode`（严格/宽松）

- `TurnContext`
  - 职责：单回合上下文，包含：sessionId、modelName、history（引用）、turnIndex、runId 等。

### 3.2 Plan Stage（规划阶段）

**包：** `com.thinkingcoding.agentloop.v2.plan`

- `Planner`（接口）
  - 输入：`PlanRequest`
  - 输出：`PlanResult`

- `LangChainPlanner`（实现，适配当前 `LangChainService`）
  - 职责：
    - 调用 LLM（流式），聚合 `assistantText`
    - 收集模型产出的 `ToolCall`（来自 LangChain4j tool calling）
    - 可选：抽取 `options`（如果保留 `OptionManager` 交互）

- `PlanRequest`
  - 字段建议：
    - `String sessionId`
    - `String modelName`
    - `String userInput`
    - `List<ChatMessage> historyView`
    - `boolean isContinuation`（是否 ReAct 续跑）

- `PlanResult`
  - 字段建议：
    - `String assistantText`（本次模型输出的完整文本）
    - `List<ToolCall> toolCalls`（模型请求的工具调用列表）
    - `List<OptionItem> options`（可选）
    - `boolean stopped`（是否被 stop 中断）

> 关键点：规划阶段可以选择两种策略：
> - **策略 A（推荐用于短期落地）**：仍允许模型 tool calling，但 Orchestrator 将其视为“计划中的工具调用序列”。
> - **策略 B（中长期优化）**：Plan 阶段禁用 tools，让模型输出结构化 plan(JSON)；Execute 阶段再让模型逐步决定 tool call（更纯粹）。

### 3.3 Execute + ReAct Stage（执行与反思闭环）

**包：** `com.thinkingcoding.agentloop.v2.execute`

- `ToolExecutionEngine`（接口）
  - 输入：`ToolCall` + `TurnContext`
  - 输出：`ToolExecutionOutcome`

> ⚠️ 注意：`ToolExecutionEngine` **不负责决定“要不要执行”**，只负责“在允许执行的前提下如何执行”。
> 是否执行由 `ToolConfirmationPolicy`（Steering）做决策，确保“用户允许的情况下才调用工具”。

- `DefaultToolExecutionEngine`（实现）
  - 职责：
    1) 工具解析与别名映射（复用/迁移 legacy `AgentLoop.resolveToolForExecution` 逻辑）
    2) 参数 Map → JSON（复用 `convertParametersToJson`）
    3) 执行 `BaseTool.execute()`
    4) 结果写回 `history`（system message），用于下一轮 ReAct 规划

- `ToolResolver`
  - 职责：将语义别名（write_file/read_file/list_directory/bash）映射到真实可执行工具（file_manager/command_executor）并重写参数。
  - 注意：当前 `LangChainService.normalizeToolCall()` 已做过一层 normalize，V2 仍要保证**执行侧**最终可落地。

- `ToolResultFormatter`
  - 职责：把 `ToolResult` 变成“对模型可读”的 observation 文本。
  - 兼容性：建议保持与 legacy `AgentLoop.formatToolResultForHistory()` 同等语义，避免影响 `ContextManager` 的压缩/识别策略。

- `ReActDriver`
  - 职责：真正实现 ReAct 回路：
    - while(还有 toolCalls 或需要 follow-up) {
      - steering 决策
      - 执行工具 → 写回 observation → 触发 Planner 续跑（continue prompt）
      - 达到上限/超时/取消则退出
    }
  - 防护：必须引入 `maxReActStepsPerTurn` 防止无限循环。

- `ExecuteReactResult`
  - 字段建议：`steps`、`trace(outcomes)`、`cancelled/stopped`、`finalAssistantText`（可选）。

### 3.4 Mid‑Turn Steering（中途干预）

**包：** `com.thinkingcoding.agentloop.v2.steer`

- `SteeringController`（接口）
  - 职责：接收来自 CLI/UI 的 steering 指令（stop、取消回合、自动批准开关、跳过下一个工具、选项选择等）。

- `SteeringHandle`（接口）
  - 职责：提供给 Planner/Executor 查询 steering 状态（shouldStop / shouldCancel / autoApprove 等）。

- `SteeringCommand`（枚举）
  - 建议最小集合：
    - `STOP_GENERATION`：停止当前流式生成
    - `CANCEL_TURN`：取消当前回合（停止后续执行）
    - `SET_AUTO_APPROVE_ON / OFF`
    - `SKIP_NEXT_TOOL`

- `ToolConfirmationPolicy`（接口）
  - 输出 `ToolDecision`（EXECUTE / EXECUTE_AND_FOLLOWUP / DISCARD）

> ✅ **默认策略要求**：
> - `ToolDecision` 的默认返回值应是“需要用户确认后才可 EXECUTE”。
> - 除非用户显式开启 `autoApprove`，否则不允许绕过确认。

- `InteractiveToolConfirmationPolicy`（实现）
  - 职责：包装当前 `core.ToolExecutionConfirmation`，把“1/2/3确认”转换为 `ToolDecision`。

- `OptionSelectionPolicy`（可选）
  - 职责：包装 legacy `core.OptionManager`；将用户数字输入转换为“新的用户指令”，让 Orchestrator 递归进入 Plan。

> Mid‑Turn Steering 的边界建议：
> - MVP 先覆盖：stop、工具确认、选项选择
> - 迭代加入：编辑参数、重规划 replan、暂停 resume、批量工具跳过

### 3.5 Gateways / Adapters（适配层）

**包：** `com.thinkingcoding.agentloop.v2.gateway`

- `SessionGateway`
  - 职责：适配 `service.SessionService` 的 load/save，便于测试替身。

- `AgentEventSink`
  - 职责：统一把事件输出到 UI：token、assistant 段落、工具计划、工具执行结果摘要。
  - MVP：可以先直接调用 `ThinkingCodingUI`（但建议通过接口隔离，方便测试）。

- `AIServiceAdapter`（可选）
  - 职责：把 `AIService.streamingChat()` 的 callback 机制封装为更易用的 `Planner.plan()`。

---

## 4. 依赖关系与调用链（简化图）

```
ThinkingCodingCommand
   └─ AgentLoopFactory
        ├─ legacy: core.AgentLoop
        └─ v2: AgentOrchestrator
              ├─ Plan: Planner (LangChainPlanner)
              ├─ Execute: ReActDriver
              │     └─ ToolExecutionEngine (DefaultToolExecutionEngine)
              │            ├─ ToolResolver
              │            └─ ToolResultFormatter
              ├─ Steer: SteeringHandle + ToolConfirmationPolicy  # ✅ 执行门禁：用户允许后才执行
              └─ Gateway: SessionGateway + AgentEventSink
```

---

## 5. 迁移路线（分阶段、可回滚）

### 阶段 0：引入 V2 骨架 + 开关
- 新增 `AgentLoopFactory`，默认仍创建 legacy `core.AgentLoop`
- 在 `ThinkingCodingCommand` 仅修改“创建 agent loop”的一处调用
- 回滚：关闭开关即可

### 阶段 1：低风险抽离工具执行引擎（不改行为）
- 从 legacy `core.AgentLoop` 迁移/下沉：
  - `resolveToolForExecution` → `ToolResolver`
  - `executeToolCall` → `DefaultToolExecutionEngine`
  - `formatToolResultForHistory` → `ToolResultFormatter`
- legacy 先改为“委托执行”，行为保持

### 阶段 2：抽离 Steering（确认/选项）
- 用 `ToolConfirmationPolicy` 包装 `ToolExecutionConfirmation`
- 用 `OptionSelectionPolicy` 包装 `OptionManager`
- legacy 仍可用这些策略对象

> ✅ 阶段 2 的验收点：任何工具执行都必须经由 `ToolConfirmationPolicy.decide(...)`。
> 即使 Planner 已产出 toolCalls，未获用户许可则不会进入 `ToolExecutionEngine.execute(...)`。

### 阶段 3：Plan Stage 落地（先不启用 ReAct）
- `Planner.plan()` 输出 `PlanResult(text + toolCalls)`
- Orchestrator：展示 plan（或摘要）后，执行一次工具并结束

### 阶段 4：引入 ReActDriver（多步闭环）
- 执行 toolCalls → observation 写回 history
- Planner 续跑（continuation prompt）→ 直到无工具或达上限

### 阶段 5：默认切换到 V2
- Factory 默认创建 V2，legacy 仍保留作为 fallback

---

## 6. 关键接口草案（Java 签名级）

> 以下仅给出签名与数据模型建议，用于指导实现与分工。

### 6.1 对外统一入口
- `interface Agent { void onUserInput(String input); void onSteeringCommand(SteeringCommand cmd); List<ChatMessage> snapshotHistory(); }`

### 6.2 Plan
- `interface Planner { PlanResult plan(PlanRequest request, AgentEventSink events, SteeringHandle steering); }`
- `final class PlanRequest { String sessionId; String modelName; String userInput; List<ChatMessage> historyView; boolean isContinuation; }`
- `final class PlanResult { String assistantText; List<ToolCall> toolCalls; List<OptionItem> options; boolean stopped; }`

### 6.3 Execute + ReAct
- `interface ToolExecutionEngine { ToolExecutionOutcome execute(ToolCall call, TurnContext turn, ToolConfirmationPolicy confirm, AgentEventSink events); }`
- `final class ToolExecutionOutcome { ToolCall call; ToolResult result; ChatMessage historyMessageToAppend; boolean executed; }`
- `final class ExecuteReactResult { int steps; List<ToolExecutionOutcome> trace; boolean cancelled; }`
- `final class ReActDriver { ExecuteReactResult run(TurnContext turn, PlanResult initialPlan, Planner planner, ToolExecutionEngine engine, SteeringHandle steering); }`

### 6.4 Steering
- `enum SteeringCommand { STOP_GENERATION, CANCEL_TURN, SET_AUTO_APPROVE_ON, SET_AUTO_APPROVE_OFF, SKIP_NEXT_TOOL }`
- `interface SteeringController { void submit(SteeringCommand cmd); void submitOptionSelection(int optionIndex); }`
- `interface SteeringHandle { boolean shouldStopGeneration(); boolean isAutoApprove(); boolean shouldCancelTurn(); boolean shouldSkipNextTool(); }`
- `enum ToolDecision { EXECUTE, EXECUTE_AND_FOLLOWUP, DISCARD }`
- `interface ToolConfirmationPolicy { ToolDecision decide(ToolExecution execution, SteeringHandle steering); }`

> ✅ **默认行为约定（写入实现规范）**：
> - `decide(...)` 在交互模式下必须向用户发起确认；用户拒绝则返回 `DISCARD`。
> - 只有用户显式开启自动批准（autoApprove）时，才允许默认返回 `EXECUTE`。
> - 对高风险工具（例如 `bash/command_executor` 且包含危险命令）建议仍强制确认（即使 autoApprove=true）。

### 6.5 Gateways
- `interface SessionGateway { List<ChatMessage> load(String sessionId); void save(String sessionId, List<ChatMessage> history); }`
- `interface AgentEventSink { void onToken(String token); void onAssistantMessage(String text); void onToolPlanned(ToolCall call); void onToolExecuted(ToolExecutionOutcome outcome); }`

---

## 7. 与现有代码的映射表（摘录）

| 现有模块 | 建议迁移/归属 | 说明 |
|---|---|---|
| `core.AgentLoop.executeToolCall()` | `v2.execute.DefaultToolExecutionEngine` | 工具执行、输出、写回历史 |
| `core.AgentLoop.resolveToolForExecution()` | `v2.execute.ToolResolver` | 语义别名 → 真实工具 |
| `core.AgentLoop.formatToolResultForHistory()` | `v2.execute.ToolResultFormatter` | 统一 observation 格式 |
| `core.ToolExecutionConfirmation` | `v2.steer.InteractiveToolConfirmationPolicy` | 统一决策输出 |
| `core.OptionManager` | `v2.steer.OptionSelectionPolicy`（可选） | 数字选项 → 指令 |
| `service.LangChainService.streamingChat()` | `v2.plan.LangChainPlanner` | 包装为 plan() 返回结果 |

---

## 8. 风险点与工程化防护

### 8.1 风险
1. **stop 竞态**：流式 token 回调与 stop 信号时序不可控。
2. **ReAct 无限循环**：模型持续产出工具调用。
3. **历史膨胀**：多步 ReAct 会加速 history 增长，需依赖 `ContextManager` 裁剪。
4. **工具别名映射重复**：`LangChainService.normalizeToolCall()` 与执行侧 resolver 需一致。

### 8.2 防护建议
- `maxReActStepsPerTurn` + `maxToolCallsPerPlan` + `turnTimeout` 三重限流
- 在 tool 失败时：
  - 写入结构化 observation（包含 error）
  - 触发 steering point（用户选择重试/跳过/取消/重规划）

---

## 9. 测试策略（建议新增/改造）

### 9.1 单元测试（不依赖真实模型）
- `ToolResolverTest`：验证 write_file/read_file/list_directory/bash 等别名映射正确
- `ToolResultFormatterTest`：success/failure 输出是否可被模型理解、并与 legacy 行为兼容
- `ToolConfirmationPolicyTest`：autoApprove 与交互选择分支

### 9.2 规划与 ReAct 的替身测试
- 提供 `FakePlanner`：脚本化返回 `PlanResult`（带 toolCalls）
- 提供 `FakeToolRegistry/FakeTool`：返回固定输出
- 验证 `ReActDriver`：多步执行 → observation 写回 → 达到终止条件

### 9.3 集成测试（最小闭环）
- `AgentOrchestrator + FakePlanner + FakeToolEngine + InMemorySessionGateway`
- 覆盖：Plan→Execute→SaveSession；Steering 的取消/跳过路径

---

## 10. 配置与开关建议（便于灰度与回滚）

建议在 `config.yaml`（或 AppConfig）新增（示例）：

```yaml
agentLoop:
  version: legacy|v2
  maxReActStepsPerTurn: 6
  maxToolCallsPerPlan: 3
  steering:
    enabled: true
    # ✅ 默认必须用户确认后才执行工具
    requireUserApprovalForTools: true
    # ✅ 允许 auto-approve 但仅用户显式开启时生效（默认关闭）
    defaultAutoApprove: false
    allowAutoApprove: true
    # ✅ 高风险命令建议始终强制确认（即使 autoApprove=true）
    forceConfirmDangerousCommands: true
```

并在 CLI 支持临时覆盖：
- `--agent-loop legacy|v2`
- `--max-react-steps 6`

---

## 11. MVP 落地建议（两周内可完成）

1. **第一周**：抽离 ToolExecutionEngine / ToolResolver / ToolResultFormatter（不改行为）
2. **第二周**：完成 V2 Orchestrator + Planner 包装 + ReActDriver（带上限）
3. 在任何时刻保留 Factory 开关回滚到 legacy

> ✅ MVP 额外验收点：
> - Planner 产生的每一个 ToolCall，在执行前都能看到明确的“是否允许执行？”交互。
> - 用户选择拒绝/取消后，该 ToolCall 不会被执行，且会写入可追踪的系统消息（用于后续 ReAct）。

---

## 12. 备注（安全与配置）

当前仓库中 `config.yaml` 存在明文 API Key / Token（例如模型 key、GitHub token）。建议在重构同时：
- 将敏感信息迁移到环境变量或本地不入库配置
- 增加启动时的安全提示/校验

---

**文档版本**：v1.1  
**更新时间**：2026-04-24  

