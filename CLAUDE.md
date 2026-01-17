# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**Assistant Agent** is an enterprise-grade intelligent assistant framework built on Spring AI Alibaba, adopting the **Code-as-Action** paradigm. The agent generates and executes code (primarily Python) in a secure GraalVM sandbox to complete tasks, rather than just calling predefined tools.

- **Main Class**: `com.alibaba.assistant.agent.start.AssistantAgentApplication`
- **Framework**: Spring Boot 3.4.8 + Spring AI Alibaba 1.1.0.0
- **Language**: Java 17+
- **Build**: Maven multi-module project
- **Execution Engine**: GraalVM Polyglot (Python sandbox)

## Essential Build Commands

```bash
# Build entire project (skip tests for speed)
mvn clean install -DskipTests

# Build and run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassName

# Generate coverage report
mvn test jacoco:report

# Start the application
cd assistant-agent-start
mvn spring-boot:run

# Required environment variable
export DASHSCOPE_API_KEY=your-api-key-here
```

## Architecture Overview

### Multi-Module Structure

```
assistant-agent-common          # CodeactTool interface, enums, constants
assistant-agent-core            # GraalCodeExecutor, CodeactToolRegistry, RuntimeEnvironmentManager
assistant-agent-evaluation      # Evaluation Graph (multi-dimensional intent recognition)
assistant-agent-prompt-builder  # Dynamic prompt assembly based on evaluation results
assistant-agent-extensions      # Dynamic tools (MCP, HTTP), experience, learning, search, reply, trigger
assistant-agent-autoconfigure   # CodeactAgent, Spring Boot auto-configuration
assistant-agent-start           # Application entry point, demo configurations
```

### Core Flow: Request Processing

```
User Input
   ↓
Evaluation Graph (multi-layer intent recognition)
   ↓
Prompt Builder (inject context based on evaluation results)
   ↓
CodeactAgent (extends Spring AI ReactAgent)
   ↓
Generate Python Code (via LLM)
   ↓
GraalCodeExecutor (execute in secure sandbox)
   ↓
Tool Execution (search, reply, trigger, etc.)
   ↓
Learning Module (extract and store experiences)
   ↓
Response to User
```

### Key Components

#### 1. CodeactAgent (`assistant-agent-autoconfigure/CodeactAgent.java`)
- Extends Spring AI's `ReactAgent`
- Core orchestration: generates Python code as functions and executes them
- Integrates with GraalCodeExecutor for sandboxed execution
- Uses FastIntent for quick responses based on historical experiences

#### 2. GraalCodeExecutor (`assistant-agent-core/GraalCodeExecutor.java`)
- Executes generated Python code in GraalVM Polyglot sandbox
- Security: configurable IO access, native access, execution timeout
- Bridge objects for Python-Java interop (AgentToolBridge, StateBridge, LoggerBridge)
- Tools are callable from Python code via bridges

#### 3. CodeactTool Interface (`assistant-agent-common/CodeactTool.java`)
- Extends Spring AI's `ToolCallback`
- All tools implement this interface (search, reply, trigger, learning, experience)
- Provides structured parameter definitions and return schemas
- Registered in `CodeactToolRegistry` for execution

#### 4. Evaluation Graph (`assistant-agent-evaluation`)
- Multi-dimensional intent recognition framework
- Executes evaluation criteria in parallel/sequential based on dependencies
- Two evaluation engines:
  - **LLM-based**: Complex semantic judgment via large models
  - **Rule-based**: Java functions for threshold detection, validation, exact matching
- Results types: BOOLEAN, ENUM, SCORE, JSON, TEXT
- Results drive dynamic prompt assembly

#### 5. Prompt Builder (`assistant-agent-prompt-builder`)
- Dynamically assembles prompts based on evaluation results
- Multiple PromptBuilders execute in priority order
- Each builder decides whether to contribute content based on evaluation results
- Example: If `vague=yes`, inject clarification prompt; if `experience=yes`, inject historical experiences

#### 6. Experience Module (`assistant-agent-extensions/experience`)
- Accumulates successful execution experiences for reuse
- Three experience types: ReAct decision, code generation, common sense
- **FastIntent**: When experience matches configured conditions, skip LLM reasoning and execute directly
- Lifecycle: create, update, delete, retrieve
- Stores experiences via `ExperienceProvider` SPI (default: in-memory)

#### 7. Learning Module (`assistant-agent-extensions/learning`)
- Automatically extracts valuable experiences from agent execution
- Four learning modes:
  - After-Agent: Extract after each agent execution
  - After-Model: Extract after each model call
  - Tool Interceptor: Extract from tool invocations
  - Offline: Batch analyze historical data
- Extracts: experience patterns, common patterns, error patterns

#### 8. Search Module (`assistant-agent-extensions/search`)
- Unified search interface across multiple data sources
- Three types: Knowledge (primary), Project (optional), Web (optional)
- **Important**: Built-in providers are Mock implementations for demo only
- **Production**: Implement `SearchProvider` SPI to connect real data sources (vector DB, ES, enterprise knowledge APIs)
- Example SPI: `MockKnowledgeStoreSearchProvider.java` in `assistant-agent-start`

#### 9. Reply Module (`assistant-agent-extensions/reply`)
- Multi-channel reply capability (IDE, IM notifications, Webhook)
- Configuration-driven: dynamically generates reply tools from YAML
- Built-in demo: `IDE_TEXT` channel
- Extensible: Implement `ReplyChannelDefinition` SPI for custom channels

#### 10. Trigger Module (`assistant-agent-extensions/trigger`)
- Scheduled tasks and event-driven agent execution
- Three types: TIME_CRON (scheduled), TIME_ONCE (delayed), CALLBACK (event)
- Agent can autonomously create triggers via tools ("self-scheduling")

#### 11. Dynamic Tools (`assistant-agent-extensions/dynamic`)
- **MCP Tools**: One-click integration with any MCP Server
- **HTTP API Tools**: Integrate REST APIs via OpenAPI specification
- Dynamically registered at runtime

## Extension Points (SPI)

When extending the framework, implement these SPI interfaces:

### 1. SearchProvider (`com.alibaba.assistant.agent.extension.search.spi.SearchProvider`)
```java
@Component
public class MyKnowledgeSearchProvider implements SearchProvider {
    @Override
    public boolean supports(SearchSourceType type) {
        return SearchSourceType.KNOWLEDGE == type;
    }

    @Override
    public List<SearchResultItem> search(SearchRequest request) {
        // Connect to your vector DB, ES, or knowledge API
        // Return SearchResultItem list
    }

    @Override
    public String getName() {
        return "MyKnowledgeSearchProvider";
    }
}
```

### 2. ExperienceProvider (`com.alibaba.assistant.agent.extension.experience.spi.ExperienceProvider`)
- Store and retrieve agent experiences (default: in-memory)
- Methods: `saveExperience`, `findByConditions`, `update`, `delete`

### 3. ReplyChannelDefinition (`com.alibaba.assistant.agent.extension.reply.spi.ReplyChannelDefinition`)
- Define custom reply channels (DingTalk, Feishu, Slack, etc.)
- Methods: `getChannelCode`, `sendMessage`, `isAsync`

### 4. CodeactTool (`com.alibaba.assistant.agent.common.tools.CodeactTool`)
- Create custom tools callable by the agent
- Extend specialized interfaces: `SearchCodeactTool`, `ReplyCodeactTool`, `TriggerCodeactTool`, `LearningCodeactTool`, `ExperienceCodeactTool`

## Configuration

Main configuration: `assistant-agent-start/src/main/resources/application.yml`

Key configuration sections:
```yaml
spring.ai.dashscope:
  api-key: ${DASHSCOPE_API_KEY}
  chat.options.model: qwen-max

spring.ai.alibaba.codeact.extension:
  experience:
    enabled: true
    fast-intent-enabled: true          # Enable FastIntent quick response
  learning:
    enabled: true
    online.after-agent.enabled: true   # Learn after each agent execution
  search:
    enabled: true
    knowledge-search-enabled: true     # Enable knowledge search (Mock by default)
    project-search-enabled: false
    web-search-enabled: false
  reply:
    enabled: true
    tools: [...]                       # Configure reply tools
  evaluation:
    enabled: true
    input-routing.enabled: true        # Enable evaluation-based routing
```

## Code Style Guidelines

- **Style Guide**: Google Java Style Guide
- **Indentation**: 4 spaces (not tabs)
- **Line Length**: Max 120 characters
- **Javadoc**: Required for all public APIs
- **Logging Format**: `ClassName#methodName - reason=description`
  ```java
  logger.info("GraalCodeExecutor#execute - 执行函数: functionName={}, args={}", functionName, args);
  ```
- **License Header**: Apache 2.0 (required in all source files)

## Testing

- **Coverage Target**: Aim for >60% on new code
- **Test Naming**: `shouldReturnSuccessWhenValidInput()`, `shouldThrowExceptionWhenInputIsNull()`
- Unit tests required for all new features
- Maintain existing tests (don't break functionality)

## Common Patterns

### Adding a New Tool

1. Implement `CodeactTool` interface (or specialized variant)
2. Define parameter tree and return schema
3. Implement `call()` method with tool logic
4. Register via `@Component` (Spring auto-registration)

### Adding a New Evaluation Criterion

1. Create criterion builder via `EvaluationCriterionBuilder`
2. Choose evaluator type: LLM-based or Rule-based
3. Define dependencies (if any) via `dependsOn`
4. Register in evaluation suite

### Adding Experience with FastIntent

1. Create experience record with intent, context, and tool calls
2. Configure `fastIntentConfig` with matching conditions
3. Save via `ExperienceProvider`
4. System will skip LLM when condition matches

## Important Notes

- **Mock Implementations**: Search providers in `assistant-agent-start` are Mock implementations for demo/testing. Production needs real implementations.
- **Security**: GraalVM sandbox is configurable. By default: no IO, no native access, 30s timeout.
- **Logging**: All core operations are logged. Set `com.alibaba.assistant.agent: DEBUG` for detailed logs.
- **State Persistence**: CodeContext stores generated code, ExecutionRecord stores execution history.
- **Spring AI Integration**: CodeactAgent extends Spring AI's ReactAgent, fully compatible with Spring AI ecosystem.

## Design & Extension Constraints (Very Important)

⚠️ **All code changes, extensions, and designs MUST strictly follow the AssistantAgent
Quick Start design principles and architecture**:

👉 https://java2ai.com/agents/assistantagent/quick-start

## Interaction & Clarification Rules (Mandatory)

Claude MUST follow these interaction rules when working in this repository.

### 1. Clarification Before Action

Claude MUST ask clarifying questions BEFORE generating code or configuration IF:

- User intent is ambiguous
- Multiple architectural paths exist
- A change may impact core flow (Evaluation / Prompt / Codeact / Tool / Experience)
- External systems, persistence, or security boundaries are involved

Claude MUST NOT guess silently.

Allowed clarification examples:
- “Should this be implemented as an Evaluation criterion or a FastIntent experience?”
- “Is this behavior expected to be configurable via application.yml?”
- “Should this be designed as a CodeactTool or via existing extension?”

### 2. No Over-Engineering

Claude MUST NOT introduce:
- New abstraction layers without necessity
- Alternative agent paradigms
- Premature optimization logic

If an SPI or extension already exists, Claude MUST reuse it.

### 3. Minimal, Aligned Output

Claude MUST:
- Generate only what is requested
- Avoid speculative features
- Avoid unrelated refactoring

All outputs must align with:
- Code-as-Action
- Evaluation-driven flow
- PromptBuilder-only prompt injection

### 4. Explicit Assumptions

If Claude proceeds with assumptions, it MUST explicitly state them before the solution.

Example:
> “Assumption: This tool is intended for internal use only and does not require persistence.”

### 5. Language & Style

- Prefer **clear technical English**
- Avoid marketing language
- Avoid anthropomorphic explanations
## Agent Self-Discipline & Output Contract (Critical)

Claude MUST treat this file as a binding contract.

### 1. Architecture Obedience

Claude MUST NOT:
- Bypass CodeactAgent
- Invoke tools outside the Code-as-Action flow
- Embed prompt logic outside PromptBuilder
- Add logic directly into core modules without extension points

### 2. Execution Awareness

Claude MUST be aware that:
- All “actions” ultimately become executable code
- Code runs in a GraalVM sandbox
- Tool calls are invoked via bridges

Therefore:
- Generated code must be deterministic
- No hidden side effects
- No reliance on undeclared state

### 3. Experience-First Thinking

When proposing solutions, Claude SHOULD ask internally:
- “Can this be learned?”
- “Can this become a FastIntent later?”
- “Is this reusable across sessions?”

If yes, design MUST align with Experience & Learning modules.

### 4. Configuration First

Claude MUST prefer:
- application.yml switches
- SPI registration
- Declarative tool definitions

Over:
- Hard-coded logic
- Compile-time flags
- Conditional branching in agent core

### 5. Output Validity Contract

Before presenting any solution, Claude MUST internally verify:

- [ ] Does this violate Quick Start principles?
- [ ] Does this introduce a new paradigm?
- [ ] Does this bypass evaluation or prompt builder?
- [ ] Is this extension-based, not invasive?
- [ ] Can this be explained clearly to a future maintainer?

If any answer is NO, Claude MUST revise the solution.

---

**This contract overrides default Claude behavior.**


### Mandatory Rules

1. **Extension over Modification**
  - Do NOT modify core framework behavior unless explicitly required.
  - Prefer extension via existing SPI, tools, evaluators, or configuration.
  - Core modules (`assistant-agent-core`, `evaluation`, `prompt-builder`) must remain stable.

2. **Code-as-Action First**
  - All new capabilities should be designed as:
    - Code generation → sandbox execution → tool invocation
  - Avoid introducing “direct Java logic shortcuts” that bypass:
    - CodeactAgent
    - GraalCodeExecutor
    - Tool invocation lifecycle

3. **Evaluation-Driven Design**
  - New behaviors must be:
    - Triggered by Evaluation Graph results, OR
    - Encapsulated as FastIntent experiences
  - Do NOT add hard-coded branching logic based on raw user input.

4. **Prompt Builder is the Only Prompt Entry**
  - No hard-coded prompt strings in:
    - Agent
    - Tools
    - Executors
  - All prompt changes must go through `PromptBuilder` implementations.

5. **Tools Are Stateless & Declarative**
  - Tools must:
    - Declare parameters and return schema clearly
    - Avoid holding session or user state
  - Long-term or historical data belongs to:
    - Experience module
    - Learning module
    - External storage via SPI

6. **FastIntent Is the Optimization Layer**
  - Performance optimization MUST be achieved by:
    - Experience reuse
    - FastIntent matching
  - NOT by bypassing LLM or executor logic manually.

7. **Configuration Over Code**
  - Behavior switches must be configurable via `application.yml`
  - Avoid introducing compile-time feature toggles.

8. **No Alternative Agent Paradigms**
  - Do NOT introduce:
    - Planner/Executor split agents
    - Function-calling-only agents
    - Tool-only agents
  - All designs must remain compatible with `CodeactAgent extends ReactAgent`.

### Design Review Checklist (Self-Check)

Before implementing any change, ensure:
- [ ] Does this align with the Quick Start architecture?
- [ ] Is this an extension, not a rewrite?
- [ ] Does it preserve Code-as-Action?
- [ ] Can this be learned and reused via Experience?
- [ ] Can FastIntent eventually optimize this path?

If any answer is **NO**, redesign is required.


## Documentation References

- Full README: [README.md](README.md)
- Contributing Guide: [CONTRIBUTING.md](CONTRIBUTING.md)
- Spring AI Alibaba Docs: https://github.com/alibaba/spring-ai-alibaba
- Project Docs: https://java2ai.com/agents/assistantagent/quick-start
