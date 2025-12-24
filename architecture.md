# Gemini-CLI Agent 架构设计文档

**版本**: v0.1.0  
**作者**: [你的名字]  
**状态**: 孵化中 (Incubating)

---

## 1. 项目愿景 (Vision)
本项目旨在构建一个基于 Java Spring Boot 的命令行智能体 (CLI Agent)。它不仅仅是一个聊天机器人，更是一个能够感知本地环境、操作文件系统、辅助开发者编写代码的“结对编程伙伴” (AI Pair Programmer)。

目标对标产品：`Claude Code`, `Cursor`, `GitHub Copilot CLI`。

---

## 2. 系统架构 (System Architecture)

### 2.1 分层架构 (Layered Architecture)
本项目遵循标准的 Spring Boot 分层架构，严格遵守 **关注点分离 (SoC)** 原则。

```mermaid
graph TD
    User[用户] --> |输入命令| Shell[Spring Shell Layer]
    Shell --> |路由分发| Command[Command Layer (表现层)]
    Command --> |调用业务| Service[Service Layer (业务层)]
    Service --> |读取配置| Config[Configuration]
    Service --> |读写数据| Memory[In-Memory History]
    Service --> |HTTP请求| LLM[Gemini/OpenAI API]
    Service --> |工具调用| Tools[Tool Strategy Layer]
    Tools --> |本地操作| FS[文件系统 / OS]
```

### 2.2 目录结构规范

```text
src/main/java/com/aitool/demo/aitool_cli/
├── config/             # [配置层] 全局配置、RestTemplate配置、API Key管理
├── command/            # [表现层] 负责解析 Shell指令，不含复杂逻辑
├── service/            # [业务层] 核心大脑，负责与 AI 交互、维护上下文
│   ├── tools/          # [策略层] Agent 工具箱 (策略模式实现)
│   └── impl/           # [实现层] 接口的具体实现 (可选，复杂后启用)
├── dto/                # [数据层] 数据传输对象 (Request/Response/POJO)
├── util/               # [工具层] 静态工具类 (如 JSON 处理，文件读写封装)
└── exception/          # [异常层] 全局异常处理定义
```

---

## 3. 核心设计模式与最佳实践
为了保证系统的可扩展性，我们在核心模块强制实施以下模式：

### 3.1 策略模式 (Strategy Pattern) —— 工具箱设计
*   **场景**: AI 需要调用不同的能力（读文件、写文件、运行命令）。
*   **规范**:
    *   所有工具必须实现 `AgentTool` 接口。
    *   利用 Spring 的 `List<AgentTool>` 自动注入机制构建 `ToolRegistry`。
    *   严禁在 Service 层使用 `if (tool == "read")` 的硬编码判断。

### 3.2 单例模式 (Singleton) —— 上下文管理
*   **场景**: AI 的对话历史 (history) 和工具注册表。
*   **规范**: 利用 Spring Bean 的默认单例特性。确保 `AiService` 全局唯一，从而保证对话上下文的连续性。

### 3.3 建造者模式 (Builder Pattern) —— DTO 构建
*   **场景**: 构建复杂的 OpenAI 格式 JSON 请求。
*   **规范**: 强制使用 Lombok 的 `@Builder`。
*   **反例**: 禁止使用含有 5 个以上参数的构造函数 `new ChatRequest(a, b, c, d, e...)`。

---

## 4. 关键编码规范 (Coding Standards)

### 4.1 异常处理 (Exception Handling)
*   **原则**: 不要吞掉异常，也不要裸奔。
*   **规范**:
    *   ❌ **错误**: `try { ... } catch (Exception e) { e.printStackTrace(); }` (控制台刷屏，无法追踪)
    *   ✅ **正确**: 使用 Slf4j 记录日志，并返回友好的错误提示给用户。

```java
// 推荐写法
log.error("调用 Gemini API 失败, 原因: {}", e.getMessage(), e);
return "系统繁忙，请稍后再试。";
```

### 4.2 日志管理 (Logging)
*   **工具**: 使用 Lombok 的 `@Slf4j` 注解。
*   **规范**:
    *   `log.info`: 记录关键操作（如：用户执行了什么命令）。
    *   `log.debug`: 记录调试信息（如：API 返回的原始 JSON，仅在 debug 模式开启）。
    *   `log.error`: 记录异常堆栈。
    *   严禁在业务代码中使用 `System.out.println` (Shell 交互层除外)。

### 4.3 安全性规约 (Security Guidelines) 🛡️
鉴于 Agent 具有执行本地命令的权限，必须实施严格的安全限制：

1.  **人机回环 (Human-in-the-loop)**:
    *   涉及 **写入文件 (write)**、**删除文件 (rm)**、**执行脚本 (exec)** 的操作，必须先在 Shell 中输出提示，要求用户输入 `y` 确认后方可执行。
2.  **路径沙箱 (Path Sandboxing)**:
    *   限制文件读写只能在当前项目目录下进行。
    *   严禁访问 `/etc`, `C:\Windows` 等敏感目录。
3.  **敏感信息脱敏**:
    *   日志中禁止打印完整的 API Key。

---

## 5. Agent 交互协议 (Protocol)

### 5.1 System Prompt (人设注入)
AI 的行为准则定义在 `service/PromptConstants.java` 中。核心指令需包含：
*   **角色定义**: "你是一个 Java 专家助手..."
*   **工具使用规则**: "当你需要读取文件时，请回复 Function: read_file [path]..."
*   **输出格式**: 强制 AI 返回 Markdown 格式的代码块。

### 5.2 记忆管理 (Memory Management)
*   **滑动窗口**: 为了防止 Token 超限（费用爆炸），当 history 列表超过 20 条时，自动移除最早的记录（保留 System Prompt）。

---

## 6. 扩展指南 (How to Contribute)
### 添加一个新工具 (例如: 获取当前时间)
1.  在 `service/tools` 下新建 `TimeTool.java`。
2.  实现 `AgentTool` 接口。
3.  加上 `@Component` 注解。
4.  **完成！** 无需修改任何其他代码，输入 `/help` 即可看到新能力。

---

## 7. 路线图 (Roadmap)
- [x] 基础对话与记忆
- [ ] **P0**: 实现 `FileReadTool` (让 AI 能够读取项目代码)
- [ ] **P1**: 实现工具调用的正则解析 (从 AI 回复中提取指令)
- [ ] **P1**: 接入 `JLine` 美化控制台 (语法高亮、Loading 动画)
- [ ] **P2**: 实现 `FileWriteTool` (让 AI 自动写代码)
