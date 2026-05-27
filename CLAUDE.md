# Ruflo — Claude Code Configuration
你现在进入【SUPERPOWERS 全自动开发流程模式】，必须严格遵守以下固定流程，每次我给你任何代码需求，你都必须自动按顺序完整执行，不得跳过任何一步：

====================
【SUPERPOWERS 固定执行流程 自动执行】
====================
步骤1：理解需求，生成高质量、可运行、健壮的业务代码。
步骤2：自动为代码生成【强制标准】的单元测试：
   - 正常场景用例 ≥3 个
   - 边界值用例 ≥2 个
   - 异常/错误用例 ≥2 个
   - 总用例数 ≥7 个
   - 必须覆盖所有分支、所有返回值、所有异常
步骤3：自动列出【测试用例清单】，标注：场景、输入、预期结果。
步骤4：自动进行【自检】，检查：
   - 用例数量是否达标
   - 是否覆盖三类场景
   - 是否存在遗漏分支
   - 是否存在测试漏洞
步骤5：如果自检不通过，自动补充用例，直到完全合格。
步骤6：最后输出总结：本次实现功能 + 测试用例统计 + 覆盖范围。

====================
【强制规则 不可违反】
====================
1. 永远不询问我“是否需要生成测试”，必须自动生成。
2. 永远不减少测试用例数量，少一个都不算完成任务。
3. 测试必须可直接运行，不能写伪代码。
4. 每次必须先执行流程，再输出最终结果。
5. 我只需要提供业务需求，你自动完成开发 + 测试 + 自检全流程。

从现在开始，所有响应都遵守这套 SUPERPOWERS 流程。

## Rules

- 使用中文回复
- 所有出现过的问题，都要添加成测试用例，后续重点回归验证
- 所有问题需要先找到根因，然后再开始修改。
- 所有提交到github上的pr，都要经过安全审查，禁止提交密码、密钥等相关的信息
- 除了.github, 其他以.开头的目录尽量不要提交到github上，保持代码仓的整洁。
- 代码使用OOP编程，要遵守软件开发的SOLID原则和KISS原则
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

## 代理通信 (SendMessage优先协调)

命名代理通过`SendMessage`进行协调，而不是轮询或共享状态。

```
领导 (你) ←→ 架构师 ←→ 开发者 ←→ 测试员 ←→ 审查员
              (命名代理直接相互通信)
```

### 启动协调团队

```javascript
// 所有代理在一个消息中启动，每个代理都知道下一个要通知谁
Agent({ prompt: "研究代码库。将发现结果通过SendMessage发送给'architect'。",
  subagent_type: "researcher", name: "researcher", run_in_background: true })
Agent({ prompt: "等待'researcher'。设计解决方案。通过SendMessage发送给'coder'。",
  subagent_type: "system-architect", name: "architect", run_in_background: true })
Agent({ prompt: "等待'architect'。实现它。通过SendMessage发送给'tester'。",
  subagent_type: "coder", name: "coder", run_in_background: true })
Agent({ prompt: "等待'coder'。编写测试。将结果通过SendMessage发送给'reviewer'。",
  subagent_type: "tester", name: "tester", run_in_background: true })
Agent({ prompt: "等待'tester'。审查代码质量和安全性。",
  subagent_type: "reviewer", name: "reviewer", run_in_background: true })

// 启动管道
SendMessage({ to: "researcher", summary: "开始", message: "[任务上下文]" })
```

### 模式

| 模式 | 流程 | 何时使用 |
|------|------|----------|
| **管道模式** | A → B → C → D | 顺序依赖 (功能开发) |
| **扇出模式** | 领导 → A, B, C → 领导 | 独立并行工作 (研究) |
| **监督模式** | 领导 ↔ 工作者 | 持续协调 (复杂重构) |

### 规则

- 始终为代理命名 — `name: "角色"`使其可寻址
- 始终在提示中包含通信指令 — 通知谁，发送什么
- 使用`run_in_background: true`在一个消息中启动所有代理
- 启动后：停止，告诉用户正在运行什么，等待结果
- 永不轮询状态 — 代理会自动返回消息或完成

## 集群与路由

### 配置
- **拓扑结构**: 分层网格 (抗漂移)
- **最大代理数**: 15
- **内存**: 混合
- **HNSW**: 已启用
- **神经网络**: 已启用

```bash
npx @claude-flow/cli@latest swarm init --topology hierarchical --max-agents 8 --strategy specialized
```

### 代理路由

| 任务 | 代理 | 拓扑结构 |
|------|------|----------|
| Bug修复 | researcher, coder, tester | hierarchical |
| 功能开发 | architect, coder, tester, reviewer | hierarchical |
| 重构 | architect, coder, reviewer | hierarchical |
| 性能优化 | perf-engineer, coder | hierarchical |
| 安全检查 | security-architect, auditor | hierarchical |

### 何时使用集群
- **是**: 3+个文件、新功能、跨模块重构、API变更、安全、性能
- **否**: 单文件编辑、1-2行修复、文档更新、配置变更、问题咨询

### 三层模型路由

| 层级 | 处理器 | 使用场景 |
|------|--------|----------|
| 1 | 代理加速器 (WASM) | 简单转换 — 跳过LLM，直接使用Edit |
| 2 | Haiku | 简单任务，低复杂度 |
| 3 | Sonnet/Opus | 架构、安全、复杂推理 |

## 记忆与学习

### 任何任务之前
```bash
npx @claude-flow/cli@latest memory search --query "[任务关键词]" --namespace patterns
npx @claude-flow/cli@latest hooks route --task "[任务描述]"
```

### 成功之后
```bash
npx @claude-flow/cli@latest memory store --namespace patterns --key "[名称]" --value "[有效的方法]"
npx @claude-flow/cli@latest hooks post-task --task-id "[ID]" --success true --store-results true
```

### MCP工具 (使用`ToolSearch("关键词")`来发现)

| 类别 | 关键工具 |
|------|----------|
| **记忆** | `memory_store`, `memory_search`, `memory_search_unified` |
| **桥接** | `memory_import_claude`, `memory_bridge_status` |
| **集群** | `swarm_init`, `swarm_status`, `swarm_health` |
| **代理** | `agent_spawn`, `agent_list`, `agent_status` |
| **钩子** | `hooks_route`, `hooks_post-task`, `hooks_worker-dispatch` |
| **安全** | `aidefence_scan`, `aidefence_is_safe`, `aidefence_has_pii` |
| **群体思维** | `hive-mind_init`, `hive-mind_consensus`, `hive-mind_spawn` |

### 后台工作者

| 工作者 | 何时使用 |
|--------|----------|
| `audit` | 安全变更后 |
| `optimize` | 性能工作后 |
| `testgaps` | 添加功能后 |
| `map` | 每5+文件变更 |
| `document` | API变更后 |

```bash
npx @claude-flow/cli@latest hooks worker dispatch --trigger audit
```

## 代理

**核心**: `coder`, `reviewer`, `tester`, `planner`, `researcher`
**架构**: `system-architect`, `backend-dev`, `mobile-dev`
**安全**: `security-architect`, `security-auditor`
**性能**: `performance-engineer`, `perf-analyzer`
**协调**: `hierarchical-coordinator`, `mesh-coordinator`, `adaptive-coordinator`
**GitHub**: `pr-manager`, `code-review-swarm`, `issue-tracker`, `release-manager`

任何字符串都可以作为自定义代理类型。

## 构建与测试

- 代码变更后始终运行测试
- 提交前始终验证构建成功

```bash
npm run build && npm test
```

## CLI快速参考

```bash
npx @claude-flow/cli@latest init --wizard           # 设置
npx @claude-flow/cli@latest swarm init --v3-mode     # 启动集群
npx @claude-flow/cli@latest memory search --query "" # 向量搜索
npx @claude-flow/cli@latest hooks route --task ""    # 路由到代理
npx @claude-flow/cli@latest doctor --fix             # 诊断
npx @claude-flow/cli@latest security scan            # 安全扫描
npx @claude-flow/cli@latest performance benchmark    # 性能测试
```

26个命令，140+子命令。使用`--help`查看任何命令的详细信息。

## 设置

```bash
claude mcp add claude-flow -- npx -y @claude-flow/cli@latest
npx @claude-flow/cli@latest daemon start
npx @claude-flow/cli@latest doctor --fix
```

**代理工具**处理执行（代理、文件、代码、git）。**MCP工具**处理协调（集群、记忆、钩子）。**CLI**通过Bash相同。

## Karpathy编程原则

> 合并自[multica-ai/andrej-karpathy-skills](https://github.com/multica-ai/andrej-karpathy-skills) — 减少常见LLM编程错误。

**权衡:** 这些指导原则偏向谨慎而非速度。对于简单任务，请运用判断力。

### 1. 编程前思考

**不要假设。不要隐藏困惑。展示权衡。**

- 明确陈述你的假设。如果不确定，就提问。
- 如果存在多种解释，展示它们 — 不要默默选择。
- 如果有更简单的方法，就说出来。在适当时推回。
- 如果有不清楚的地方，就停止。命名令人困惑的地方。提问。

### 2. 简单优先

**解决问题的最少代码。没有投机。**

- 不要添加超出要求的功能。
- 不要为一次性代码做抽象。
- 不要添加未要求的"灵活性"或"可配置性"。
- 不要为不可能的场景添加错误处理。
- 如果你写了200行代码，但它可能只有50行，就重写它。

问自己："高级工程师会说这个过度复杂吗？"如果是，就简化。

### 3. 精准修改

**只触及你必须的。只清理你自己的混乱。**

- 不要"改进"相邻代码、注释或格式。
- 不要重构没有坏的东西。
- 匹配现有风格，即使你会做得不同。
- 如果你注意到无关的死代码，提及它 — 不要删除它。
- 删除你的变更造成的未使用的导入/变量/函数。
- 不要删除预先存在的死代码，除非被要求。

**测试:** 每个变更的行都应该直接追溯到用户的要求。

### 4. 目标驱动执行

**定义成功标准。循环直到验证。**

将任务转换为可验证的目标：
- "添加验证" → "为无效输入编写测试，然后让它们通过"
- "修复bug" → "编写重现它的测试，然后让它通过"
- "重构X" → "确保重构前后测试都通过"

对于多步骤任务，陈述简要计划：
```
1. [步骤] → 验证: [检查]
2. [步骤] → 验证: [检查]
3. [步骤] → 验证: [检查]
```

**这些指导原则有效如果:** 差异中不必要的变更更少，由于过度复杂导致的返工更少，澄清问题在实现前而不是错误后出现。

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
