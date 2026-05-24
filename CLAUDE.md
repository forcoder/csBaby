# Ruflo — Claude Code Configuration

## Rules

- 请使用中文回复
- 使用中文回复我
- Do what has been asked; nothing more, nothing less
- NEVER create files unless absolutely necessary — prefer editing existing files
- NEVER create documentation files unless explicitly requested
- NEVER save working files or tests to root — use `/src`, `/tests`, `/docs`, `/config`, `/scripts`
- ALWAYS read a file before editing it
- NEVER commit secrets, credentials, or .env files
- Keep files under 500 lines
- Validate input at system boundaries

## 测试要求

- 单元测试覆盖率必须达到 85% 以上
- 所有新功能必须有对应的测试用例
- 测试驱动开发 (TDD)：先写测试，再实现功能

## Agent Comms (SendMessage-First Coordination)

Named agents coordinate via `SendMessage`, not polling or shared state.

```
Lead (you) ←→ architect ←→ developer ←→ tester ←→ reviewer
              (named agents message each other directly)
```

### Spawning a Coordinated Team

```javascript
// ALL agents in ONE message, each knows WHO to message next
Agent({ prompt: "Research the codebase. SendMessage findings to 'architect'.",
  subagent_type: "researcher", name: "researcher", run_in_background: true })
Agent({ prompt: "Wait for 'researcher'. Design solution. SendMessage to 'coder'.",
  subagent_type: "system-architect", name: "architect", run_in_background: true })
Agent({ prompt: "Wait for 'architect'. Implement it. SendMessage to 'tester'.",
  subagent_type: "coder", name: "coder", run_in_background: true })
Agent({ prompt: "Wait for 'coder'. Write tests. SendMessage results to 'reviewer'.",
  subagent_type: "tester", name: "tester", run_in_background: true })
Agent({ prompt: "Wait for 'tester'. Review code quality and security.",
  subagent_type: "reviewer", name: "reviewer", run_in_background: true })

// Kick off the pipeline
SendMessage({ to: "researcher", summary: "Start", message: "[task context]" })
```

### Patterns

| Pattern | Flow | Use When |
|---------|------|----------|
| **Pipeline** | A → B → C → D | Sequential dependencies (feature dev) |
| **Fan-out** | Lead → A, B, C → Lead | Independent parallel work (research) |
| **Supervisor** | Lead ↔ workers | Ongoing coordination (complex refactor) |

### Rules

- ALWAYS name agents — `name: "role"` makes them addressable
- ALWAYS include comms instructions in prompts — who to message, what to send
- Spawn ALL agents in ONE message with `run_in_background: true`
- After spawning: STOP, tell user what's running, wait for results
- NEVER poll status — agents message back or complete automatically

## Swarm & Routing

### Config
- **Topology**: hierarchical-mesh (anti-drift)
- **Max Agents**: 15
- **Memory**: hybrid
- **HNSW**: Enabled
- **Neural**: Enabled

```bash
npx @claude-flow/cli@latest swarm init --topology hierarchical --max-agents 8 --strategy specialized
```

### Agent Routing

| Task | Agents | Topology |
|------|--------|----------|
| Bug Fix | researcher, coder, tester | hierarchical |
| Feature | architect, coder, tester, reviewer | hierarchical |
| Refactor | architect, coder, reviewer | hierarchical |
| Performance | perf-engineer, coder | hierarchical |
| Security | security-architect, auditor | hierarchical |

### When to Swarm
- **YES**: 3+ files, new features, cross-module refactoring, API changes, security, performance
- **NO**: single file edits, 1-2 line fixes, docs updates, config changes, questions

### 3-Tier Model Routing

| Tier | Handler | Use Cases |
|------|---------|-----------|
| 1 | Agent Booster (WASM) | Simple transforms — skip LLM, use Edit directly |
| 2 | Haiku | Simple tasks, low complexity |
| 3 | Sonnet/Opus | Architecture, security, complex reasoning |

## Memory & Learning

### Before Any Task
```bash
npx @claude-flow/cli@latest memory search --query "[task keywords]" --namespace patterns
npx @claude-flow/cli@latest hooks route --task "[task description]"
```

### After Success
```bash
npx @claude-flow/cli@latest memory store --namespace patterns --key "[name]" --value "[what worked]"
npx @claude-flow/cli@latest hooks post-task --task-id "[id]" --success true --store-results true
```

### MCP Tools (use `ToolSearch("keyword")` to discover)

| Category | Key Tools |
|----------|-----------|
| **Memory** | `memory_store`, `memory_search`, `memory_search_unified` |
| **Bridge** | `memory_import_claude`, `memory_bridge_status` |
| **Swarm** | `swarm_init`, `swarm_status`, `swarm_health` |
| **Agents** | `agent_spawn`, `agent_list`, `agent_status` |
| **Hooks** | `hooks_route`, `hooks_post-task`, `hooks_worker-dispatch` |
| **Security** | `aidefence_scan`, `aidefence_is_safe`, `aidefence_has_pii` |
| **Hive-Mind** | `hive-mind_init`, `hive-mind_consensus`, `hive-mind_spawn` |

### Background Workers

| Worker | When |
|--------|------|
| `audit` | After security changes |
| `optimize` | After performance work |
| `testgaps` | After adding features |
| `map` | Every 5+ file changes |
| `document` | After API changes |

```bash
npx @claude-flow/cli@latest hooks worker dispatch --trigger audit
```

## Agents

**Core**: `coder`, `reviewer`, `tester`, `planner`, `researcher`
**Architecture**: `system-architect`, `backend-dev`, `mobile-dev`
**Security**: `security-architect`, `security-auditor`
**Performance**: `performance-engineer`, `perf-analyzer`
**Coordination**: `hierarchical-coordinator`, `mesh-coordinator`, `adaptive-coordinator`
**GitHub**: `pr-manager`, `code-review-swarm`, `issue-tracker`, `release-manager`

Any string works as a custom agent type.

## Build & Test

- ALWAYS run tests after code changes
- ALWAYS verify build succeeds before committing

```bash
npm run build && npm test
```

## CLI Quick Reference

```bash
npx @claude-flow/cli@latest init --wizard           # Setup
npx @claude-flow/cli@latest swarm init --v3-mode     # Start swarm
npx @claude-flow/cli@latest memory search --query "" # Vector search
npx @claude-flow/cli@latest hooks route --task ""    # Route to agent
npx @claude-flow/cli@latest doctor --fix             # Diagnostics
npx @claude-flow/cli@latest security scan            # Security scan
npx @claude-flow/cli@latest performance benchmark    # Benchmarks
```

26 commands, 140+ subcommands. Use `--help` on any command for details.

## Setup

```bash
claude mcp add claude-flow -- npx -y @claude-flow/cli@latest
npx @claude-flow/cli@latest daemon start
npx @claude-flow/cli@latest doctor --fix
```

**Agent tool** handles execution (agents, files, code, git). **MCP tools** handle coordination (swarm, memory, hooks). **CLI** is the same via Bash.

## Karpathy Coding Principles

> Merged from [multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) — reduce common LLM coding mistakes.

**Tradeoff:** These guidelines bias toward caution over speed. For trivial tasks, use judgment.

### 1. Think Before Coding

**Don't assume. Don't hide confusion. Surface tradeoffs.**

- State your assumptions explicitly. If uncertain, ask.
- If multiple interpretations exist, present them — don't pick silently.
- If a simpler approach exists, say so. Push back when warranted.
- If something is unclear, stop. Name what's confusing. Ask.

### 2. Simplicity First

**Minimum code that solves the problem. Nothing speculative.**

- No features beyond what was asked.
- No abstractions for single-use code.
- No "flexibility" or "configurability" that wasn't requested.
- No error handling for impossible scenarios.
- If you write 200 lines and it could be 50, rewrite it.

Ask yourself: "Would a senior engineer say this is overcomplicated?" If yes, simplify.

### 3. Surgical Changes

**Touch only what you must. Clean up only your own mess.**

- Don't "improve" adjacent code, comments, or formatting.
- Don't refactor things that aren't broken.
- Match existing style, even if you'd do it differently.
- If you notice unrelated dead code, mention it — don't delete it.
- Remove imports/variables/functions that YOUR changes made unused.
- Don't remove pre-existing dead code unless asked.

**The test:** Every changed line should trace directly to the user's request.

### 4. Goal-Driven Execution

**Define success criteria. Loop until verified.**

Transform tasks into verifiable goals:
- "Add validation" → "Write tests for invalid inputs, then make them pass"
- "Fix the bug" → "Write a test that reproduces it, then make it pass"
- "Refactor X" → "Ensure tests pass before and after"

For multi-step tasks, state a brief plan:
```
1. [Step] → verify: [check]
2. [Step] → verify: [check]
3. [Step] → verify: [check]
```

**These guidelines are working if:** fewer unnecessary changes in diffs, fewer rewrites due to overcomplication, and clarifying questions come before implementation rather than after mistakes.

---

## Android Kotlin 强制编码规范（AI 专属生效版）

### 核心总则（AI 最高优先级）

本文件为当前项目 Claude Code 强制生效编码规则，优先级高于所有默认行为、默认代码风格、AI 自主生成逻辑。所有代码新增、修改、修复、重构、优化动作，必须 100% 严格遵守，不得省略、简化、自定义。

**铁律流程**：先设计文档 → 人工 Review 确认 → 合规编码 → 自测验证 → 验收交付，禁止直接编码、禁止边写边改、禁止幻觉式开发。

**核心目标**：统一业界标准 Kotlin 安卓编码风格、杜绝不规范代码、空指针隐患、内存泄漏、架构混乱、逻辑漏洞，实现工程级标准化交付。

---

### 一、基础命名规范（零容忍强制）

所有命名严格遵循统一规则，无特殊例外、无自定义风格：

- **包名**：全小写、无下划线、无大小写混写、多词连续拼接，示例：`com.app.userprofile`
- **KT 文件名**：单类文件与类名完全 PascalCase 同名；扩展文件以功能命名；禁止使用 Util/Helper/Tools/Common 模糊命名
- **类/接口/密封类/枚举**：统一 PascalCase。ViewModel、数据类、状态类、枚举严格区分语义命名
- **函数/变量**：小驼峰 camelCase，函数动词开头；常量大写下划线 `MAX_RETRY_COUNT`；私有属性无下划线前缀
- **资源 ID**：全小写+下划线，示例：`btn_login`、`iv_avatar`

---

### 二、代码格式强制标准

- **缩进**：固定 4 个空格，绝对禁止 Tab 制表符
- **大括号**：紧跟代码语句不换行，`if (flag) {}`，禁止大括号单独换行
- **单行长度**：最大 120 字符，超长必须规范换行
- **格式细节**：运算符、逗号后必须加空格；括号内侧无空格；逻辑块、方法间空一行分隔
- **链式调用**：点号置于行首，统一对齐换行
- **禁止**：冗余空行、多余空格、无效注释、废弃代码

**类内固定代码顺序（强制统一）**：所有类文件必须严格按以下顺序编排，禁止乱序：
1. Companion Object 常量、静态配置
2. 成员属性（val 优先于 var，公开优先于私有）
3. 构造函数、初始化代码块
4. 公开业务方法
5. 私有工具方法
6. 内部类、接口、枚举定义

---

### 三、Kotlin 语言特性强制规范

#### 3.1 空安全（最高优先级红线）

- 所有变量、参数、返回值默认非空，空类型必须显式声明 `?`
- 空处理优先级：`?.` 安全调用 > `?:` Elvis 兜底 > `let` 作用域
- **严格禁止滥用 `!!` 非空断言**
- 不确定非空场景，禁止强制断言，优先使用 `Result<T>` 或可空兜底
- 网络回调、本地缓存、页面传参、异步结果，必须完整做空安全防护

#### 3.2 函数编写规范

- **单一职责原则**：单个函数代码行数 ≤ 20 行，只实现单一业务逻辑
- 单行纯返回函数，必须使用表达式简写格式
- 优先使用默认参数，减少无用函数重载
- 复杂方法调用必须使用命名参数，提升可读性
- **强制早期返回**：杜绝深层嵌套 if-else，条件不满足直接 return
- 所有公开方法必须编写标准 KDoc 注释，标注用途、参数、返回值、异常场景

#### 3.3 作用域函数使用规范（固定场景，禁止滥用）

| 作用域函数 | 允许场景 | 禁止场景 |
|-----------|---------|---------|
| `apply` | 对象初始化、属性配置 | 其他所有场景 |
| `let` | 空安全判空、对象转换处理 | 非空处理 |
| `run` | 代码块计算、返回结果 | 对象配置 |
| `with` | 重复调用同一对象成员简化 | 嵌套使用 |
| `also` | 附加日志、埋点、次要辅助操作 | 主要业务逻辑 |

- **禁止作用域函数嵌套、禁止跨场景滥用**

#### 3.4 扩展函数 & 集合规范

- 通用扩展统一归类至 `XxxExtensions.kt`，禁止零散定义、禁止替代业务方法
- 所有扩展函数必须做空安全、边界异常判断
- 优先使用 `filter`/`map`/`forEach` 高阶函数替代原生 for 循环
- 复杂、大批量数据遍历转换，必须使用 Sequence 延迟计算，减少内存开销
- 禁止生成冗余临时集合、临时对象

---

### 四、项目架构强制规范（MVVM + Clean 业界标准）

#### 4.1 固定分包结构（禁止私自改动）

```
com.xxx.app
├── ui/                # 界面层
│   ├── 功能模块/      # login/home/mine 按业务拆分
│   │   ├── Activity/Fragment
│   │   ├── ViewModel
│   │   ├── UiState/UiEvent
│   └── common/        # 全局公共UI组件
├── domain/            # 纯领域层（无Android依赖）
│   ├── model/         # 纯数据模型
│   ├── repository/    # 仓库抽象接口
│   └── usecase/       # 业务用例
├── data/              # 数据层
│   ├── remote/        # 网络请求、API定义
│   ├── local/         # 本地缓存、数据库、SP
│   └── repository/    # 仓库接口实现
└── utils/             # 极简工具类，严控新增
```

#### 4.2 MVVM 单向数据流铁律

- **View 层**（Activity/Fragment）：仅负责 UI 渲染、事件分发，**禁止任何业务逻辑、数据解析、状态处理**
- **ViewModel 层**：仅管理 UI 状态、处理业务事件；禁止持有 View、Context 引用；通过 StateFlow/Flow 暴露不可变状态
- **状态管理**：UiState、UiEvent 使用密封类/数据类定义，状态不可变
- **严格单向流**：View 事件 → ViewModel 处理 → 更新 State → View 刷新，**禁止反向数据流**

#### 4.3 依赖注入规范

- 全局统一使用 Google Hilt 实现依赖注入
- 所有依赖优先构造函数注入
- 禁止静态依赖、全局单例滥用、硬编码依赖

---

### 五、异常处理 & 资源规范

- **可恢复业务异常**：统一使用 `Result<T>` 封装处理
- **不可恢复异常**：主动抛出带明确描述的异常信息
- **禁止空 catch 捕获**，所有异常必须打印日志、容错处理、用户友好提示
- 文件流、网络流、数据库连接等资源，必须使用 `use{}` 自动关闭释放

---

### 六、性能 & 内存优化强制规则

- ViewModel、全局单例**禁止持有 Context、View 引用**，杜绝内存泄漏
- 字符串拼接优先使用 StringBuilder，禁止频繁使用 `+` 拼接
- 非即时使用的属性，统一使用 `lazy` 懒加载
- 对象序列化统一使用 `@Parcelize`，废弃 Serializable
- 异步任务统一使用 Kotlin Coroutine，**禁止原生 Thread、Handler 滥用**
- 避免频繁创建临时对象、临时集合，减少 GC 开销

---

### 七、AI 开发强制流程（必须 100% 执行）

所有功能开发、BUG 修复、代码修改、重构优化，必须严格执行以下七步流程，**未完成前置步骤禁止编码**：

1. **需求/问题澄清**：明确需求边界、BUG 根因、影响范围、异常场景
2. **输出设计文档**：生成技术方案、架构设计、风险评估、适配方案
3. **输出标准化任务清单**：拆分原子任务、明确修改文件、定义验收标准
4. **等待人工 Review 确认**：用户未批准，**禁止任何代码改动**
5. **合规编码实现**：严格遵循本规范所有规则开发
6. **全维度自测**：功能测试、边界测试、异常测试、回归测试
7. **输出验收报告**：对照测试用例逐条核验，确认全部通过后交付

---

### 八、红线禁止行为（绝对不允许）

- **禁止**滥用非空断言 `!!`
- **禁止**深层嵌套代码、混乱逻辑、无注释复杂逻辑
- **禁止**批量新增 Util/Helper 工具类
- **禁止** View 层承载业务逻辑、数据解析、状态处理
- **禁止**所有硬编码（字符串、地址、状态值、常量数值）
- **禁止**遗漏空判断、异常捕获、边界防护
- **禁止**私自修改项目架构、分包结构、编码风格
- **禁止**带警告、带隐患、不规范代码交付

---

### 九、最终交付标准（全部满足才算完成）

- 代码 100% 匹配本规范所有条款，无违规写法
- 无 IDE 语法警告、无 Lint 检测报错
- 空安全、异常、边界场景全部处理完毕
- 公共方法、复杂逻辑、核心业务完整注释
- 功能正常、无闪退、无逻辑 BUG、无内存泄漏
- 附带完整修改说明、设计文档、验收测试结果
