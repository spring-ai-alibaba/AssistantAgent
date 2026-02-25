# Repository Guidelines

## Project Structure & Module Organization
This is a multi-module Maven project rooted at `pom.xml`. Core modules live under:
- `assistant-agent-common` (shared constants/utilities)
- `assistant-agent-core` (runtime execution engine)
- `assistant-agent-extensions` (search/reply/trigger/learning integrations)
- `assistant-agent-evaluation` and `assistant-agent-prompt-builder`
- `assistant-agent-autoconfigure` (Spring Boot wiring)
- `assistant-agent-start` (runnable demo app)

Primary source code is in each module's `src/main/java`. Add tests under matching `src/test/java` package paths. Documentation assets are in `images/`, with contribution/process docs in `README.md` and `CONTRIBUTING.md`.

## Build, Test, and Development Commands
- `mvn clean install -DskipTests`: build all modules quickly.
- `mvn test`: run all unit/integration tests across modules.
- `cd assistant-agent-start && mvn spring-boot:run`: start the demo application locally.
- `mvn -pl assistant-agent-start test -Dtest=ClassName`: run a focused test class.

Use Java 17+ and Maven 3.8+.

## Coding Style & Naming Conventions
Follow Google Java Style and existing module/package layout (`com.alibaba.assistant.agent...`).
- Indentation: 4 spaces, no tabs.
- Max line length: 120.
- Naming: `PascalCase` for classes, `camelCase` for methods/fields, `UPPER_SNAKE_CASE` for constants.
- Add Javadoc for public APIs and keep Apache 2.0 license headers on source files.
- Prefer clear, structured logging (for example, `ClassName#method - reason=...`).

## Testing Guidelines
Testing is based on JUnit Jupiter and Spring Boot test support (module-dependent). 
- Name test classes `*Test`.
- Prefer behavior-oriented method names such as `shouldReturnXWhenY`.
- Keep tests close to module ownership (`assistant-agent-core` changes should include core tests).
- Target meaningful coverage on new code (team guidance: >60% for new logic).

## Commit & Pull Request Guidelines
Recent history follows Conventional Commit-style messages, often with optional scope:
- `feat(search): ...`
- `fix(config): ...`
- `hotfix(Component): ...`
- `docs: ...`, `test: ...`, `chore: ...`

For PRs, include: concise description, linked issue (if any), test evidence (`mvn test` output summary), and doc/config updates when behavior changes. Keep PRs focused and small enough for review.

## Security & Configuration Tips
Do not commit secrets. Configure `DASHSCOPE_API_KEY` via environment variables and keep local overrides out of version control.

## Capability Registration Baseline (2026-02-11)

Current implementation uses startup-time YAML registration for business capability tools.

- Config entry:
  - `assistant.agent.capability.registrations` in `assistant-agent-start/src/main/resources/application.yml`
  - full reference in `assistant-agent-start/src/main/resources/application-reference.yml`
- Registration chain:
  - `CapabilityRegistrationProperties` -> `CapabilityToolRegistrar` -> `RegisteredHttpFormCodeactTool`
  - final wiring in `CodeactAgentConfig#grayscaleCodeactAgent`
- Runtime behavior contract (capability tool response):
  - `SLOT_MISSING`
  - `WAIT_CONFIRM`
  - `SUBMITTED`
  - `SUBMIT_FAILED`
- React orchestration:
  - `WorkReportDraftResumeReactHook` now handles generic capability drafts via `capability_draft_{toolName}_status`
  - slot extraction in resume flow is LLM-based (no regex hardcoding for business fields)
  - `FastIntentJumpCleanupModelHook` performs generic capability direct reply and fast-intent jump cleanup

### Current Constraints

- YAML is loaded at startup; changing YAML requires application restart.
- Dynamic hot-add via API + DB persistence is not implemented yet.
- Scope validated for single-instance flow only.

### Local Verification Commands

- Targeted extension tests:
  - `mvn --% -pl assistant-agent-extensions -am test -Dtest=WorkReportDraftResumeReactHookTest,FastIntentJumpCleanupModelHookTest -Dsurefire.failIfNoSpecifiedTests=false`
- Start-module tests:
  - `mvn --% -pl assistant-agent-start -am test -Dtest=DemoExperienceConfigTest -Dsurefire.failIfNoSpecifiedTests=false`
- Local startup (if 8081 is occupied):
  - `cd assistant-agent-start && mvn spring-boot:run -Dspring-boot.run.arguments=--server.port=18081`
