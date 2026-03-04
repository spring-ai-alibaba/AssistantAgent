package com.alibaba.assistant.agent.autoconfigure.subagent.node;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CodeGeneratorNode#extractCodeFromContent.
 *
 * <p>Covers various LLM output formats to verify robustness of code extraction.
 */
class CodeGeneratorNodeExtractCodeTest {

    // ==================== 1. Standard markdown code blocks ====================

    @Nested
    @DisplayName("Standard markdown code blocks")
    class MarkdownCodeBlock {

        @Test
        @DisplayName("Pure ```python code block")
        void pureCodeBlock() {
            String input = "```python\ndef foo():\n    return 42\n```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def foo():"));
            assertFalse(result.contains("```"));
        }

        @Test
        @DisplayName("Pure ``` code block (no language tag)")
        void pureCodeBlockNoLang() {
            String input = "```\ndef bar():\n    pass\n```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def bar():"));
        }

        @Test
        @DisplayName("```py shorthand tag")
        void pyShorthand() {
            String input = "```py\ndef baz():\n    return 1\n```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def baz():"));
        }
    }

    // ==================== 2. Natural language prefix + code block (core scenario) ====================

    @Nested
    @DisplayName("Natural language prefix + code block")
    class NaturalLanguagePrefixWithCodeBlock {

        @Test
        @DisplayName("Analysis text before ```python block (reproduces the real production crash)")
        void chineseAnalysisBeforeCodeBlock() {
            String input = "Based on the requirements, I need to create an iteration for the project.\n"
                    + "Looking at available tools, I need to query the project list first.\n\n"
                    + "```python\n"
                    + "def create_iteration():\n"
                    + "    project_name = \"test-project\"\n"
                    + "    return {\"success\": False, \"message\": \"missing project ID, cannot create iteration\"}\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def create_iteration():"),
                    "should start with function definition, actual: " + result.substring(0, Math.min(50, result.length())));
            assertFalse(result.contains("Based on the requirements"), "should not contain analysis text");
            assertFalse(result.contains("```"), "should not contain markdown markers");
        }

        @Test
        @DisplayName("English analysis text before code block")
        void englishAnalysisBeforeCodeBlock() {
            String input = "I'll create a function to search for the project.\n\n"
                    + "```python\n"
                    + "def search_project():\n"
                    + "    return []\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def search_project():"));
            assertFalse(result.contains("I'll create"));
        }

        @Test
        @DisplayName("Multi-paragraph analysis before code block")
        void multiParagraphAnalysis() {
            String input = "First, analyze the requirements.\n\n"
                    + "Then, determine which tools to use.\n\n"
                    + "Finally, the generated code is as follows:\n\n"
                    + "```python\n"
                    + "def my_func():\n"
                    + "    return True\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def my_func():"));
        }
    }

    // ==================== 3. Multiple code blocks ====================

    @Nested
    @DisplayName("Multiple code blocks (last one wins)")
    class MultipleCodeBlocks {

        @Test
        @DisplayName("Two code blocks - should take the last one")
        void twoCodeBlocks() {
            String input = "Here is the wrong version:\n\n"
                    + "```python\n"
                    + "def wrong():\n"
                    + "    pass\n"
                    + "```\n\n"
                    + "Here is the corrected version:\n\n"
                    + "```python\n"
                    + "def correct():\n"
                    + "    return 42\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.contains("def correct():"), "should extract the last code block");
            assertFalse(result.contains("def wrong():"), "should not contain the first code block");
        }

        @Test
        @DisplayName("Three code blocks - should take the last one")
        void threeCodeBlocks() {
            String input = "v1:\n```python\ndef v1():\n    pass\n```\n"
                    + "v2:\n```python\ndef v2():\n    pass\n```\n"
                    + "final:\n```python\ndef v3():\n    return 'final'\n```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.contains("def v3():"));
        }
    }

    // ==================== 4. No markdown markers ====================

    @Nested
    @DisplayName("No markdown markers")
    class NoMarkdown {

        @Test
        @DisplayName("Pure code starting with def")
        void pureCode() {
            String input = "def hello():\n    return 'hello'";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertEquals("def hello():\n    return 'hello'", result);
        }

        @Test
        @DisplayName("Natural language followed by bare def (no code block wrapper)")
        void naturalLanguageThenBareDef() {
            String input = "Sure, here is the generated code.\n"
                    + "def do_something():\n"
                    + "    return True";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def do_something():"), "should start from def");
            assertFalse(result.contains("Sure, here"), "should not contain natural language prefix");
        }

        @Test
        @DisplayName("Pure natural language (no code, fallback returns content as-is)")
        void pureNaturalLanguage() {
            String input = "Sorry, I cannot generate code for this request.";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertEquals("Sorry, I cannot generate code for this request.", result);
        }
    }

    // ==================== 5. Edge cases ====================

    @Nested
    @DisplayName("Edge cases")
    class EdgeCases {

        @Test
        @DisplayName("null input")
        void nullInput() {
            assertNull(CodeGeneratorNode.extractCodeFromContent(null));
        }

        @Test
        @DisplayName("empty string")
        void emptyString() {
            assertEquals("", CodeGeneratorNode.extractCodeFromContent(""));
        }

        @Test
        @DisplayName("whitespace-only input")
        void onlyWhitespace() {
            String result = CodeGeneratorNode.extractCodeFromContent("   \n\n  ");
            // whitespace-only: guard returns the original value
            assertNotNull(result);
        }

        @Test
        @DisplayName("full-width characters inside a string literal should be preserved")
        void fullWidthCharsInString() {
            String input = "```python\n"
                    + "def greet():\n"
                    + "    return 'hello, world!'\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.contains("'hello, world!'"), "should preserve characters inside string literals");
        }

        @Test
        @DisplayName("full-width characters inside a comment should be preserved")
        void fullWidthCharsInComment() {
            String input = "```python\n"
                    + "def foo():\n"
                    + "    # query project, get result\n"
                    + "    return None\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.contains("# query project, get result"), "should preserve characters inside comments");
        }

        @Test
        @DisplayName("lots of surrounding blank lines")
        void lotsOfWhitespace() {
            String input = "\n\n\n```python\n\ndef foo():\n    pass\n\n```\n\n\n";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def foo():"));
        }

        @Test
        @DisplayName("empty code block followed by a real code block - should take the real one")
        void emptyCodeBlockFollowedByReal() {
            String input = "```python\n\n```\n\n```python\ndef fallback():\n    pass\n```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def fallback():"));
        }
    }

    // ==================== 6. Real-world LLM output simulation ====================

    @Nested
    @DisplayName("Real-world LLM output simulation")
    class RealWorldLLMOutput {

        @Test
        @DisplayName("Full LLM output that reproduced the real production SyntaxError crash")
        void realWorldCrashCase() {
            String input = "Based on the requirements, I need to create an iteration for the project. "
                    + "Looking at the available tools, `coop_tools.create_sprint` requires a `projectId` parameter, "
                    + "but no project ID was provided, so I need to query the project list first.\n"
                    + "\n"
                    + "However, the current tool list only contains `coop_tools.create_sprint` and `o2_tools.add_iteration`, "
                    + "with no tool to query the project list. I need to inform the user of the missing information.\n"
                    + "\n"
                    + "```python\n"
                    + "def create_iteration():\n"
                    + "    project_name = \"test-project\"\n"
                    + "    iteration_name = \"test-iteration-2\"\n"
                    + "    owner_info = \"not-found(99999999)\"\n"
                    + "    \n"
                    + "    owner_id_result = llm_tools.call_llm(\n"
                    + "        source_data=owner_info,\n"
                    + "        target_format=\"99999999\",\n"
                    + "        extract_requirement=\"extract owner ID from parentheses as a numeric string\"\n"
                    + "    )\n"
                    + "    owner_id = owner_id_result if owner_id_result else \"99999999\"\n"
                    + "    \n"
                    + "    reply_tools.send_message(\n"
                    + "        message=f\"Cannot create iteration: missing project ID.\"\n"
                    + "    )\n"
                    + "    \n"
                    + "    return {\n"
                    + "        \"success\": False,\n"
                    + "        \"message\": \"missing project ID, cannot create iteration\",\n"
                    + "        \"projectName\": project_name,\n"
                    + "        \"iterationName\": iteration_name,\n"
                    + "        \"ownerId\": owner_id\n"
                    + "    }\n"
                    + "```";

            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def create_iteration():"),
                    "should start with function definition, actual: " + result.substring(0, Math.min(60, result.length())));
            assertFalse(result.contains("Based on the requirements"), "should not contain natural language");
            assertTrue(result.contains("reply_tools.send_message"), "should preserve function body");
            assertTrue(result.contains("\"success\": False"), "should preserve return value");
        }

        @Test
        @DisplayName("LLM returns only code with no explanation")
        void llmReturnsOnlyCode() {
            String input = "def simple():\n    return 1 + 1";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertEquals("def simple():\n    return 1 + 1", result);
        }

        @Test
        @DisplayName("LLM returns a code block followed by explanation text")
        void codeBlockFollowedByExplanation() {
            String input = "```python\n"
                    + "def calculate():\n"
                    + "    return 42\n"
                    + "```\n\n"
                    + "This function returns 42, the answer to life, the universe, and everything.";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def calculate():"));
            assertFalse(result.contains("answer to life"), "should not contain explanation after code block");
        }

        @Test
        @DisplayName("LLM returns a complex indented function")
        void complexIndentedFunction() {
            String input = "Here is the implementation:\n\n```python\n"
                    + "def process_data(**kwargs):\n"
                    + "    results = []\n"
                    + "    for key, value in kwargs.items():\n"
                    + "        if isinstance(value, str):\n"
                    + "            results.append(f\"{key}: {value}\")\n"
                    + "        else:\n"
                    + "            results.append(f\"{key}: {str(value)}\")\n"
                    + "    return {\"processed\": results, \"count\": len(results)}\n"
                    + "```";
            String result = CodeGeneratorNode.extractCodeFromContent(input);
            assertTrue(result.startsWith("def process_data(**kwargs):"));
            assertTrue(result.contains("for key, value in kwargs.items():"));
            assertTrue(result.contains("return {\"processed\": results"));
        }
    }
}

