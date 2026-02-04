# Changelog

All notable changes to Assistant Agent will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Initial open source release preparation
- Comprehensive documentation

---

## [0.1.3] - 2026-02-04

### Added

#### Observation & Traceability
- **OpenTelemetry Observability Support**: Full OpenTelemetry native API integration
  - `BaseAgentObservationLifecycleListener`: Base lifecycle listener for Agent observability
  - `CodeactObservationDocumentation`: Standardized observation metrics definition (Hook, Interceptor, React, Execution, CodeGen, ToolCall)
  - `EvaluationObservationLifecycleListener`: Observability listener for Evaluation Graph
- **Tool Call Tracing**: New tool call recording and tracing capabilities
  - `ToolCallRecord`: Tool call record model with call order and tool name
  - `ExecutionRecord.callTrace`: Tool call trace list during code execution
  - `ToolRegistryBridge`: Tool registry bridge
- **Observation Helper Classes**:
  - `HookObservationHelper`: Hook execution observation helper
  - `InterceptorObservationHelper`: Interceptor execution observation helper
  - `OpenTelemetryObservationHelper`: General OpenTelemetry observation helper
- **Observation Contexts**:
  - `HookObservationContext`: Hook observation context
  - `InterceptorObservationContext`: Interceptor observation context
  - `ReactPhaseObservationContext`: React phase observation context
  - `CodeGenerationObservationContext`: Code generation observation context
  - `CodeactExecutionObservationContext`: Code execution observation context
  - `CodeactToolCallObservationContext`: Tool call observation context

#### Prompt Contributor Module
- **PromptContributor Mechanism Refactoring**: Replaced PromptBuilder/PromptManager with a more flexible Prompt contribution system
  - `PromptContributor`: Prompt contributor interface
  - `PromptContributorManager`: Prompt contributor manager interface
  - `DefaultPromptContributorManager`: Default implementation with priority sorting and dynamic registration
  - `PromptContributorContext`: Context interface
  - `OverAllStatePromptContributorContext`: OverAllState-based context implementation
- **Evaluation-based Prompt Contribution**:
  - `EvaluationBasedPromptContributor`: Abstract base class for generating Prompts based on evaluation results
  - `PromptContributorModelHook`: Abstract base class for integrating PromptContributor into ModelHook
  - `ReactPromptContributorModelHook`: Prompt contribution Hook for React phase
  - `CodeactPromptContributorModelHook`: Prompt contribution Hook for Codeact phase
- **Auto Configuration**: `PromptContributorAutoConfiguration` provides out-of-the-box configuration

#### Other Enhancements
- `ParameterTree`: Enhanced parameter tree definition capabilities
- `CommonSenseInjectionTool`: Common sense injection tool
- `ToolContextHelper`: Tool context helper class
- `CodeactStateKeys`: Codeact state key constants

### Changed
- **GraalCodeExecutor**: Enhanced code executor with tool call tracing support
- **PythonToolViewRenderer**: Enhanced Python tool view rendering capabilities
- **EvaluationService**: Support for parent Span for distributed tracing
- **EvaluationSuiteBuilder**: Enhanced evaluation suite building capabilities
- **ReplyCodeactToolFactory**: Optimized reply tool factory implementation
- **AfterAgentLearningHook**: Enhanced learning Hook implementation
- **AsyncLearningHandler**: Optimized async learning handler
- **CodeactAgent**: Refactored to support new observation and Prompt contribution mechanism

### Removed
- `PromptBuilder`: Replaced by PromptContributor
- `PromptManager`: Replaced by PromptContributorManager
- `PromptInjectionInterceptor`: Replaced by PromptContributorModelHook
- `CodeactToolFilter`: Tool filter
- `WhitelistMode`: Whitelist mode enum

---

## [0.1.2] - 2026-01-XX

### Added
- Baidu Qianfan intelligent search API integration
- HookPhases annotation support
- McpServerAwareToolCallback interface for enhanced MCP dynamic tool creation

### Fixed
- Fixed context binding path in evaluation criteria
- Fixed null pointer exception in UnifiedSearchCodeactTool

---

## [0.1.0] - Initial Release

### Added

#### Core Features
- **Code-as-Action Execution Engine**: Agent generates and executes Python code via GraalVM sandbox
- **Secure Sandbox**: AI-generated code runs safely with resource isolation in GraalVM polyglot environment
- **CodeactTool System**: Unified tool interface supporting multiple tool types

#### Evaluation Module
- Multi-dimensional intent recognition through Evaluation Graph
- LLM-based and Rule-based evaluation engines
- Customizable evaluation criteria with dependency management
- Multiple result types: BOOLEAN, ENUM, SCORE, JSON, TEXT

#### Prompt Builder Module
- Dynamic prompt assembly based on evaluation results
- Priority-based PromptBuilder orchestration
- Support for system text prepend/append
- Non-invasive model interceptor integration

#### Experience Module
- Multi-type experience management (Code, ReAct, Common)
- In-memory experience storage with configurable TTL
- Experience retrieval and injection into prompts
- FastIntent quick response mechanism for familiar scenarios

#### Learning Module
- After-Agent learning: Extract experiences after agent execution
- After-Model learning: Extract experiences after model calls
- Tool Interceptor learning: Learn from tool executions
- Async learning execution support

#### Search Module
- Unified SearchProvider SPI for multiple data sources
- Support for Knowledge, Project, and Web search types
- Configurable result merging strategies
- Mock implementations for demonstration

#### Reply Module
- Multi-channel reply routing
- Configuration-driven reply tool generation
- ReplyChannelDefinition SPI for custom channels
- Built-in IDE text channel for demonstration

#### Trigger Module
- Cron-based scheduled triggers (TIME_CRON)
- One-time delayed triggers (TIME_ONCE)
- Callback event triggers (CALLBACK)
- Persistent trigger repository

#### Dynamic Tools Module
- MCP (Model Context Protocol) tool integration
- HTTP API tool integration via OpenAPI spec
- Dynamic tool registration and discovery

#### Auto Configuration
- Spring Boot auto-configuration support
- Sensible defaults for all modules
- Comprehensive configuration properties

### Configuration
- Full YAML configuration support
- Environment variable support for sensitive values
- Module-level enable/disable switches

### Documentation
- Chinese and English README
- Contributing guidelines
- Roadmap documentation
- Configuration reference

---

## Version History Summary

| Version | Date       | Description                                                                 |
|---------|------------|-----------------------------------------------------------------------------|
| 0.1.3   | 2026-02-04 | Enhanced tool call tracing and observability, refactored Prompt Contributor module, bug fixes |
| 0.1.2   | 2026-01-XX | Baidu Qianfan search integration, HookPhases annotation, bug fixes          |
| 0.1.0   | TBD        | Initial open source release                                                 |

---

## Migration Notes

### From Internal Versions

If you're migrating from internal versions of this project:

1. Review configuration property names - some may have changed
2. Implement required SPI interfaces (SearchProvider, etc.) for production use

---

## Deprecation Notices

None at this time.

