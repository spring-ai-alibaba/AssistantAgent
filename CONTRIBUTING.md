# Contributing to Assistant Agent

Thank you for your interest in contributing to Assistant Agent! We welcome contributions from the community.

## Table of Contents

- [Code of Conduct](#code-of-conduct)
- [Getting Started](#getting-started)
- [Development Environment](#development-environment)
- [How to Contribute](#how-to-contribute)
- [Code Style](#code-style)
- [Testing](#testing)
- [Pull Request Process](#pull-request-process)
- [Issue Guidelines](#issue-guidelines)

---

## Code of Conduct

Please be respectful and constructive in all interactions. We are committed to providing a welcoming and inclusive environment for everyone.

---

## Getting Started

### Prerequisites

- **Java 17+** (OpenJDK or GraalVM)
- **Maven 3.8+**
- **Git**
- **IDE**: IntelliJ IDEA (recommended) or Eclipse

### Quick Setup

```bash
# Clone the repository
git clone https://github.com/spring-ai-alibaba/AssistantAgent.git
cd AssistantAgent

# Build the project
mvn clean install -DskipTests

# Run tests
mvn test
```

---

## Development Environment

### IDE Setup (IntelliJ IDEA)

1. **Import Project**: File → Open → Select `pom.xml`
2. **Configure JDK**: File → Project Structure → SDK → Java 17+
3. **Enable Annotation Processing**: Settings → Build → Compiler → Annotation Processors → Enable

### Environment Variables

Create a `.env` file (not committed to Git) or set environment variables:

```bash
export DASHSCOPE_API_KEY=your-api-key-here
```

### Running the Application

```bash
cd assistant-agent-start
mvn spring-boot:run
```

---

## How to Contribute

### Types of Contributions

| Type | Description |
|------|-------------|
| 🐛 **Bug Fix** | Fix issues in existing code |
| ✨ **Feature** | Add new functionality |
| 📝 **Documentation** | Improve docs, README, comments |
| 🧪 **Tests** | Add or improve tests |
| 🔧 **Refactor** | Improve code quality without changing behavior |
| 🌐 **Translation** | Help with i18n |

### Contribution Workflow

1. **Check existing issues** - Look for similar issues or discussions
2. **Open an issue** - For significant changes, discuss first
3. **Fork & Clone** - Fork the repo and clone locally
4. **Create a branch** - Branch from `main`
5. **Make changes** - Follow code style guidelines
6. **Test** - Ensure all tests pass
7. **Commit** - Write clear commit messages
8. **Push & PR** - Open a Pull Request

```bash
# Fork on GitHub, then:
git clone https://github.com/YOUR_USERNAME/AssistantAgent.git
cd AssistantAgent
git checkout -b feature/your-feature-name

# Make changes...
mvn test
git add .
git commit -m "feat: add your feature"
git push origin feature/your-feature-name

# Open PR on GitHub
```

---

## Code Style

### Java Conventions

- Follow [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- Use 4 spaces for indentation (not tabs)
- Maximum line length: 120 characters
- Use meaningful variable and method names

### Javadoc Requirements

**All public APIs must have Javadoc:**

```java
/**
 * Brief description of the method.
 *
 * <p>Additional details if needed.
 *
 * @param paramName Description of the parameter
 * @return Description of return value
 * @throws ExceptionType When this exception is thrown
 * @since 1.0.0
 */
public ReturnType methodName(ParamType paramName) {
    // implementation
}
```

### Logging Format

Follow the project logging convention:

```java
// Format: ClassName#methodName - reason=description
logger.info("GraalCodeExecutor#execute - 执行函数: functionName={}, args={}", functionName, args);
logger.error("SearchProvider#search - 搜索失败: query={}, error={}", query, e.getMessage());
```

### Package Structure

```
com.alibaba.assistant.agent
├── common          # Shared utilities, constants, interfaces
├── core            # Core execution engine
├── evaluation      # Evaluation framework
├── prompt          # Prompt building
├── extension       # Extension modules
│   ├── experience  # Experience management
│   ├── learning    # Learning extraction
│   ├── search      # Search providers
│   ├── reply       # Reply channels
│   ├── trigger     # Trigger mechanism
│   └── dynamic     # Dynamic tools (MCP, HTTP)
└── autoconfigure   # Spring Boot auto-configuration
```

### License Header

All source files must include the Apache 2.0 license header:

```java
/*
 * Copyright 2024-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
```

---

## Testing

### Test Requirements

- **Unit tests required** for all new features
- **Maintain existing tests** - Don't break existing functionality
- **Target coverage**: Aim for > 60% coverage on new code

### Running Tests

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ClassName

# Run with coverage report
mvn test jacoco:report
```

### Test Naming Convention

```java
@Test
void shouldReturnSuccessWhenValidInput() { ... }

@Test
void shouldThrowExceptionWhenInputIsNull() { ... }
```

---

## Pull Request Process

### Before Submitting

- [ ] Code compiles without errors
- [ ] All tests pass
- [ ] New code has appropriate tests
- [ ] Javadoc added for public APIs
- [ ] No sensitive information (API keys, etc.)
- [ ] Commit messages are clear

### PR Title Format

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat: add new search provider for Elasticsearch
fix: resolve null pointer in experience retrieval
docs: update README with configuration examples
refactor: simplify prompt builder logic
test: add unit tests for trigger module
```

### PR Description Template

```markdown
## Description
Brief description of changes.

## Related Issue
Fixes #123

## Type of Change
- [ ] Bug fix
- [ ] New feature
- [ ] Breaking change
- [ ] Documentation update

## Checklist
- [ ] Tests added/updated
- [ ] Documentation updated
- [ ] Code follows style guidelines
```

### Review Process

1. Maintainers will review your PR
2. Address any requested changes
3. Once approved, maintainers will merge
4. Your contribution will be included in the next release!

---

## Issue Guidelines

### Bug Reports

Include:
- Clear description of the bug
- Steps to reproduce
- Expected vs actual behavior
- Environment details (OS, Java version)
- Relevant logs/screenshots

### Feature Requests

Include:
- Clear description of the feature
- Use case / motivation
- Proposed solution (optional)
- Alternatives considered (optional)

---

## Getting Help

- **Questions**: Open a Discussion on GitHub
- **Bugs**: Open an Issue with the bug template
- **Features**: Open an Issue with the feature template

---

## Recognition

Contributors will be recognized in:
- Release notes
- Contributors list

---

## License

By contributing, you agree that your contributions will be licensed under the [Apache License 2.0](LICENSE).
