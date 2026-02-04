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
package com.alibaba.assistant.agent.core.executor;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.enums.Language;
import com.alibaba.assistant.agent.core.context.CodeContext;
import com.alibaba.assistant.agent.core.executor.bridge.AgentToolBridge;
import com.alibaba.assistant.agent.core.executor.bridge.LoggerBridge;
import com.alibaba.assistant.agent.core.executor.bridge.StateBridge;
import com.alibaba.assistant.agent.core.model.ExecutionRecord;
import com.alibaba.assistant.agent.core.model.GeneratedCode;
import com.alibaba.assistant.agent.core.tool.CodeactToolRegistry;
import com.alibaba.assistant.agent.core.tool.DefaultToolRegistryBridgeFactory;
import com.alibaba.assistant.agent.core.tool.ToolRegistryBridge;
import com.alibaba.assistant.agent.core.tool.ToolRegistryBridgeFactory;
import com.alibaba.cloud.ai.graph.OverAllState;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.HostAccess;
import org.graalvm.polyglot.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * GraalVM-based code executor.
 * Executes Python code using GraalVM's Polyglot API.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class GraalCodeExecutor {

	private static final Logger logger = LoggerFactory.getLogger(GraalCodeExecutor.class);

	private final RuntimeEnvironmentManager environmentManager;
	private final CodeContext codeContext;

	// Bridge objects for Python-Java interop
	private final AgentToolBridge toolBridge;
	private final StateBridge stateBridge;
	private final LoggerBridge loggerBridge;

	// Executable tool registry
	private final CodeactToolRegistry codeactToolRegistry;

	// ToolRegistryBridge factory for customization
	private final ToolRegistryBridgeFactory toolRegistryBridgeFactory;

	// Security settings
	private final boolean allowIO;
	private final boolean allowNativeAccess;
	private final long executionTimeoutMs;

	public GraalCodeExecutor(
			RuntimeEnvironmentManager environmentManager,
			CodeContext codeContext,
			List<ToolCallback> tools,
			OverAllState state) {
		this(environmentManager, codeContext, tools, state, null, null, false, false, 30000);
	}

	public GraalCodeExecutor(
			RuntimeEnvironmentManager environmentManager,
			CodeContext codeContext,
			List<ToolCallback> tools,
			OverAllState state,
			boolean allowIO,
			boolean allowNativeAccess,
			long executionTimeoutMs) {
		this(environmentManager, codeContext, tools, state, null, null, allowIO, allowNativeAccess, executionTimeoutMs);
	}

	public GraalCodeExecutor(
			RuntimeEnvironmentManager environmentManager,
			CodeContext codeContext,
			List<ToolCallback> tools,
			OverAllState state,
			CodeactToolRegistry codeactToolRegistry,
			boolean allowIO,
			boolean allowNativeAccess,
			long executionTimeoutMs) {
		this(environmentManager, codeContext, tools, state, codeactToolRegistry, null, allowIO, allowNativeAccess, executionTimeoutMs);
	}

	/**
	 * Full constructor that supports a custom ToolRegistryBridgeFactory.
	 *
	 * @param environmentManager runtime environment manager
	 * @param codeContext code context
	 * @param tools tool callbacks
	 * @param state overall state
	 * @param codeactToolRegistry Codeact tool registry
	 * @param toolRegistryBridgeFactory custom ToolRegistryBridge factory; if null, the default factory is used
	 * @param allowIO whether to allow IO
	 * @param allowNativeAccess whether to allow native access
	 * @param executionTimeoutMs execution timeout in milliseconds
	 */
	public GraalCodeExecutor(
			RuntimeEnvironmentManager environmentManager,
			CodeContext codeContext,
			List<ToolCallback> tools,
			OverAllState state,
			CodeactToolRegistry codeactToolRegistry,
			ToolRegistryBridgeFactory toolRegistryBridgeFactory,
			boolean allowIO,
			boolean allowNativeAccess,
			long executionTimeoutMs) {
		this.environmentManager = environmentManager;
		this.codeContext = codeContext;
		this.codeactToolRegistry = codeactToolRegistry;
		this.toolRegistryBridgeFactory = toolRegistryBridgeFactory != null
				? toolRegistryBridgeFactory
				: DefaultToolRegistryBridgeFactory.INSTANCE;
		this.allowIO = allowIO;
		this.allowNativeAccess = allowNativeAccess;
		this.executionTimeoutMs = executionTimeoutMs;

		// Create bridge objects
		this.toolBridge = new AgentToolBridge(tools);
		this.stateBridge = new StateBridge(state);
		this.loggerBridge = new LoggerBridge();

		logger.info("GraalCodeExecutor#<init> 初始化完成: language={}, allowIO={}, timeout={}ms, executableTools={}, customBridgeFactory={}",
				codeContext.getLanguage(), allowIO, executionTimeoutMs, codeactToolRegistry != null, toolRegistryBridgeFactory != null);
	}

	/**
	 * Execute a function by name (uses empty ToolContext)
	 */
	public ExecutionRecord execute(String functionName, Map<String, Object> args) {
		return execute(functionName, args, new ToolContext(Map.of()));
	}

	/**
	 * Execute a function by name with ToolContext.
	 *
	 * <p>The ToolContext is passed to ToolRegistryBridgeFactory when creating
	 * ToolRegistryBridge for CodeactTools.
	 *
	 * @param functionName the function name to execute
	 * @param args the function arguments
	 * @param toolContext the tool context
	 * @return execution record
	 */
	public ExecutionRecord execute(String functionName, Map<String, Object> args, ToolContext toolContext) {
		logger.info("GraalCodeExecutor#execute 执行函数: functionName={}, args={}", functionName, args);

		ExecutionRecord record = new ExecutionRecord(functionName, codeContext.getLanguage());
		long startTime = System.currentTimeMillis();

		try {
			// Check if function exists
			GeneratedCode code = codeContext.getFunction(functionName)
				.orElseThrow(() -> new IllegalArgumentException("Function not found: " + functionName));

			// IMPORTANT: Re-extract the actual function name from the generated code
			// Because LLM might generate different function names than what we registered
			String actualFunctionName = environmentManager.extractFunctionName(code.getCode());
			if (actualFunctionName == null) {
				throw new IllegalStateException("Cannot extract function name from code: " + functionName);
			}

			logger.info("GraalCodeExecutor#execute 注册的函数名={}, 实际函数名={}", functionName, actualFunctionName);

			// Generate complete code with all functions
			StringBuilder codeBuilder = new StringBuilder();

			// Add imports
			codeBuilder.append(environmentManager.generateImports(codeContext));
			codeBuilder.append("\n\n");

			// Add all registered functions
			for (GeneratedCode func : codeContext.getAllFunctions()) {
				codeBuilder.append(func.getCode());
				codeBuilder.append("\n\n");
			}

			// Check if the function accepts parameters by inspecting the code
			// Need to handle:
			// - def foo(): or def foo() ->
			// - def foo(**kwargs): or def foo(param1, param2):
			// Use DOTALL flag to match across newlines
			Pattern noParamsPattern = Pattern.compile(
				"def\\s+" + Pattern.quote(actualFunctionName) + "\\s*\\(\\s*\\)",
				Pattern.DOTALL
			);
			boolean functionHasNoParams = noParamsPattern.matcher(code.getCode()).find();

			// Generate function call
			String functionCall;
			if (!functionHasNoParams && args != null && !args.isEmpty()) {
				// Function accepts parameters and we have args to pass
				functionCall = environmentManager.generateFunctionCall(actualFunctionName, args);
				logger.debug("GraalCodeExecutor#execute 函数接受参数，生成带参数的调用: {}", functionCall);
			} else {
				// Function doesn't accept parameters or no args provided
				functionCall = actualFunctionName + "()";
				if (args != null && !args.isEmpty()) {
					logger.warn("GraalCodeExecutor#execute 函数不接受参数，但提供了args={}，将忽略参数", args);
				}
			}

			codeBuilder.append("# Execute function\n");
			codeBuilder.append("_result = ").append(functionCall).append("\n");
			codeBuilder.append("_result  # Return the result\n");

			String finalCode = codeBuilder.toString();

			logger.info("GraalCodeExecutor#execute 准备执行的完整代码:\n{}", finalCode);
			logger.debug("GraalCodeExecutor#execute 代码长度: {} 字符", finalCode.length());

			// Execute with GraalVM
			Object result = executeWithGraal(finalCode, toolContext);

			record.setSuccess(true);
			record.setResult(result != null ? String.valueOf(result) : "null");

			logger.info("GraalCodeExecutor#execute 执行成功: result={}", result);

		} catch (Exception e) {
			record.setSuccess(false);
			record.setErrorMessage(e.getMessage());
			record.setStackTrace(getStackTrace(e));

			logger.error("GraalCodeExecutor#execute 执行失败: functionName=" + functionName, e);
		} finally {
			long duration = System.currentTimeMillis() - startTime;
			record.setDurationMs(duration);
		}

		return record;
	}

	/**
	 * Execute code directly (for testing or one-off execution, uses empty ToolContext)
	 */
	public ExecutionRecord executeDirect(String code) {
		return executeDirect(code, new ToolContext(Map.of()));
	}

	/**
	 * Execute code directly with ToolContext (for testing or one-off execution)
	 *
	 * @param code the code to execute
	 * @param toolContext the tool context
	 * @return execution record
	 */
	public ExecutionRecord executeDirect(String code, ToolContext toolContext) {
		logger.info("GraalCodeExecutor#executeDirect 直接执行代码");

		ExecutionRecord record = new ExecutionRecord("__direct__", codeContext.getLanguage());
		long startTime = System.currentTimeMillis();

		try {
			// Wrap code with imports
			String completeCode = environmentManager.generateImports(codeContext) + "\n" + code;

			// Execute with GraalVM
			Object result = executeWithGraal(completeCode, toolContext);

			record.setSuccess(true);
			record.setResult(String.valueOf(result));

			logger.info("GraalCodeExecutor#executeDirect 执行成功");

		} catch (Exception e) {
			record.setSuccess(false);
			record.setErrorMessage(e.getMessage());
			record.setStackTrace(getStackTrace(e));

			logger.error("GraalCodeExecutor#executeDirect 执行失败", e);
		} finally {
			long duration = System.currentTimeMillis() - startTime;
			record.setDurationMs(duration);
		}

		return record;
	}

	/**
	 * Execute code using GraalVM Polyglot API
	 *
	 * @param code the code to execute
	 * @param toolContext the tool context to pass to ToolRegistryBridgeFactory
	 * @return execution result
	 */
	private Object executeWithGraal(String code, ToolContext toolContext) {
		logger.debug("GraalCodeExecutor#executeWithGraal 创建GraalVM Context, hasToolContext={}", toolContext != null);

		// Capture output
		ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
		ByteArrayOutputStream errorStream = new ByteArrayOutputStream();

		try (Context context = Context.newBuilder("python")
				.allowHostAccess(HostAccess.ALL)
				.allowIO(allowIO)
				.allowNativeAccess(allowNativeAccess)
				.out(new PrintStream(outputStream, true, StandardCharsets.UTF_8))
				.err(new PrintStream(errorStream, true, StandardCharsets.UTF_8))
				.build()) {

			// Inject bridge objects into Python context
			context.getBindings("python").putMember("agent_tools", toolBridge);
			context.getBindings("python").putMember("agent_state", stateBridge);
			context.getBindings("python").putMember("logger", loggerBridge);

			logger.debug("GraalCodeExecutor#executeWithGraal - reason=Bridge对象注入完成");

			// Inject CodeactTools into Python environment with toolContext
			if (codeactToolRegistry != null) {
				injectCodeactTools(context, codeactToolRegistry, codeContext.getLanguage(), toolContext);
			}

			// Execute code
			Value result = context.eval("python", code);

			// Log captured output
			String output = outputStream.toString(StandardCharsets.UTF_8);
			String errors = errorStream.toString(StandardCharsets.UTF_8);

			if (!output.isEmpty()) {
				logger.info("GraalCodeExecutor#executeWithGraal Python输出:\n{}", output);
			}
			if (!errors.isEmpty()) {
				logger.warn("GraalCodeExecutor#executeWithGraal Python错误输出:\n{}", errors);
			}

			// Convert result to Java object BEFORE context closes
			// This is critical: lazy conversion will fail after context closes
			Object javaResult;

			// Check if result is a Python module object (indicates no return value)
			String resultStr = result.toString();
			logger.info("GraalCodeExecutor#executeWithGraal result string: {}", resultStr);
			if (resultStr != null && resultStr.startsWith("<module")) {
				logger.info("GraalCodeExecutor#executeWithGraal 检测到返回Python模块对象，视为None");
				javaResult = null;
			} else if (result.isNull()) {
				javaResult = null;
			} else if (result.isNumber()) {
				javaResult = convertNumberValue(result);
			} else if (result.isBoolean()) {
				javaResult = result.asBoolean();
			} else if (result.isString()) {
				javaResult = result.asString();
			} else if (result.hasArrayElements()) {
				// Convert array to Java List immediately
				long size = result.getArraySize();
				List<Object> list = new ArrayList<>((int) size);
				for (long i = 0; i < size; i++) {
					list.add(convertValueToJava(result.getArrayElement(i)));
				}
				javaResult = list;
			} else if (result.hasMembers()) {
				// Check if this is a Python dict (has keys() method)
				logger.debug("GraalCodeExecutor#executeWithGraal result has members, checking if it's a Python dict");
				logger.debug("GraalCodeExecutor#executeWithGraal result.hasMember('keys')={}", result.hasMember("keys"));

				if (result.hasMember("keys")) {
					Value keysValue = result.getMember("keys");
					logger.debug("GraalCodeExecutor#executeWithGraal keysValue.canExecute()={}", keysValue.canExecute());

					if (keysValue.canExecute()) {
						// This is a Python dict
						logger.info("GraalCodeExecutor#executeWithGraal 识别为Python dict，开始转换");
						Map<String, Object> map = new HashMap<>();
						Value keys = keysValue.execute();

						// Python dict_keys 对象支持迭代，但不支持hasArrayElements
						// 需要将其转换为list才能使用数组接口
						if (keys.hasArrayElements()) {
							// 如果支持数组接口，直接使用
							long size = keys.getArraySize();
							logger.info("GraalCodeExecutor#executeWithGraal Python dict 包含 {} 个键（数组方式）", size);
							for (long i = 0; i < size; i++) {
								Value keyValue = keys.getArrayElement(i);
								String key = keyValue.asString();
								logger.debug("GraalCodeExecutor#executeWithGraal 处理键: {}", key);
								Value value = result.invokeMember("__getitem__", key);
								Object javaValue = convertValueToJava(value);
								map.put(key, javaValue);
								logger.debug("GraalCodeExecutor#executeWithGraal 键 {} 的值类型: {}", key,
									javaValue != null ? javaValue.getClass().getSimpleName() : "null");
							}
						} else if (keys.hasIterator()) {
							// dict_keys 支持迭代器
							logger.debug("GraalCodeExecutor#executeWithGraal 使用迭代器遍历Python dict keys");
							Value iterator = keys.getIterator();
							while (iterator.hasIteratorNextElement()) {
								Value keyValue = iterator.getIteratorNextElement();
								String key = keyValue.asString();
								logger.debug("GraalCodeExecutor#executeWithGraal 处理键: {}", key);
								Value value = result.invokeMember("__getitem__", key);
								Object javaValue = convertValueToJava(value);
								map.put(key, javaValue);
								logger.debug("GraalCodeExecutor#executeWithGraal 键 {} 的值类型: {}", key,
									javaValue != null ? javaValue.getClass().getSimpleName() : "null");
							}
							logger.info("GraalCodeExecutor#executeWithGraal Python dict 包含 {} 个键（迭代器方式）", map.size());
						} else {
							// 最后的fallback：尝试将keys转换为list
							logger.warn("GraalCodeExecutor#executeWithGraal keys既不支持数组也不支持迭代器，尝试转换为list");
							// 调用 Python 的 list() 函数
							Value listFunc = result.getContext().getBindings("python").getMember("list");
							if (listFunc != null && listFunc.canExecute()) {
								Value keysList = listFunc.execute(keys);
								if (keysList.hasArrayElements()) {
									long size = keysList.getArraySize();
									logger.info("GraalCodeExecutor#executeWithGraal 转换为list成功，包含 {} 个键", size);
									for (long i = 0; i < size; i++) {
										Value keyValue = keysList.getArrayElement(i);
										String key = keyValue.asString();
										Value value = result.invokeMember("__getitem__", key);
										map.put(key, convertValueToJava(value));
									}
								}
							}
						}
						javaResult = map;
						logger.info("GraalCodeExecutor#executeWithGraal 转换Python dict完成，包含 {} 个键", map.size());
					} else {
						logger.warn("GraalCodeExecutor#executeWithGraal keys() 不可执行，使用getMemberKeys");
						// Fallback to getMemberKeys
						Map<String, Object> map = new HashMap<>();
						for (String key : result.getMemberKeys()) {
							map.put(key, convertValueToJava(result.getMember(key)));
						}
						javaResult = map;
					}
				} else {
					logger.warn("GraalCodeExecutor#executeWithGraal 没有keys成员，使用getMemberKeys");
					// Regular object with members
					Map<String, Object> map = new HashMap<>();
					for (String key : result.getMemberKeys()) {
						map.put(key, convertValueToJava(result.getMember(key)));
					}
					javaResult = map;
				}
			} else {
				// For other types, convert to string immediately
				javaResult = result.toString();
			}

			return javaResult;

		} catch (Exception e) {
			String errors = errorStream.toString(StandardCharsets.UTF_8);
			if (!errors.isEmpty()) {
				throw new RuntimeException("GraalVM execution error: " + errors, e);
			}
			throw e;
		}
	}

	/**
	 * 无损转换数字类型，优先使用能适配的最小类型
	 * 对于超大整数使用 BigInteger，对于浮点数使用 BigDecimal
	 */
	private Object convertNumberValue(Value value) {
		// 首先尝试适配较小的类型
		if (value.fitsInLong()) {
			long longValue = value.asLong();
			// 如果能放入 int，返回 int
			if (longValue >= Integer.MIN_VALUE && longValue <= Integer.MAX_VALUE) {
				return (int) longValue;
			}
			return longValue;
		} else if (value.fitsInDouble()) {
			return value.asDouble();
		} else if (value.fitsInBigInteger()) {
			return value.asBigInteger();
		} else {
			// 对于其他情况（如非常大的数），使用字符串表示来创建 BigDecimal
			String numStr = value.toString();
			try {
				return new java.math.BigDecimal(numStr);
			} catch (NumberFormatException e) {
				logger.warn("GraalCodeExecutor#convertNumberValue 无法解析数字: {}, 返回字符串形式", numStr);
				return numStr;
			}
		}
	}

	/**
	 * Recursively convert GraalVM Value to Java object
	 */
	private Object convertValueToJava(Value value) {
		return convertValueToJava(value, new HashSet<>());
	}

	/**
	 * Recursively convert GraalVM Value to Java object with cycle detection
	 */
	private Object convertValueToJava(Value value, Set<Integer> visited) {
		// Check if value is a Python module object
		String valueStr = value.toString();
		if (valueStr != null && (valueStr.startsWith("<module") || valueStr.startsWith("<function") || valueStr.startsWith("<class"))) {
			logger.debug("GraalCodeExecutor#convertValueToJava 检测到Python内部对象，跳过转换: {}", valueStr);
			return valueStr;
		}

		if (value.isNull()) {
			return null;
		} else if (value.isNumber()) {
			return convertNumberValue(value);
		} else if (value.isBoolean()) {
			return value.asBoolean();
		} else if (value.isString()) {
			return value.asString();
		} else if (value.hasArrayElements()) {
			// Check for cycles
			int identityHash = System.identityHashCode(value);
			if (visited.contains(identityHash)) {
				logger.warn("GraalCodeExecutor#convertValueToJava 检测到循环引用，跳过处理");
				return "[Circular Reference]";
			}
			visited.add(identityHash);

			long size = value.getArraySize();
			List<Object> list = new ArrayList<>((int) size);
			for (long i = 0; i < size; i++) {
				list.add(convertValueToJava(value.getArrayElement(i), visited));
			}

			visited.remove(identityHash);
			return list;
		} else if (value.hasMembers()) {
			// Check for cycles
			int identityHash = System.identityHashCode(value);
			if (visited.contains(identityHash)) {
				logger.warn("GraalCodeExecutor#convertValueToJava 检测到循环引用，跳过处理");
				return "[Circular Reference]";
			}
			visited.add(identityHash);

			// Check if this is a Python dict (has keys() method)
			if (value.hasMember("keys") && value.getMember("keys").canExecute()) {
				// This is a Python dict
				Map<String, Object> map = new HashMap<>();
				Value keysMethod = value.getMember("keys");
				Value keys = keysMethod.execute();

				// Try different methods to iterate over keys
				if (keys.hasArrayElements()) {
					long size = keys.getArraySize();
					for (long i = 0; i < size; i++) {
						Value keyValue = keys.getArrayElement(i);
						String key = keyValue.asString();
						Value itemValue = value.invokeMember("__getitem__", key);
						map.put(key, convertValueToJava(itemValue, visited));
					}
				} else if (keys.hasIterator()) {
					// Use iterator
					Value iterator = keys.getIterator();
					while (iterator.hasIteratorNextElement()) {
						Value keyValue = iterator.getIteratorNextElement();
						String key = keyValue.asString();
						Value itemValue = value.invokeMember("__getitem__", key);
						map.put(key, convertValueToJava(itemValue, visited));
					}
				}

				visited.remove(identityHash);
				return map;
			} else {
				// Regular object with members - skip special Python members
				Map<String, Object> map = new HashMap<>();
				for (String key : value.getMemberKeys()) {
					// Skip special Python attributes that may cause cycles
					if (key.startsWith("__") && key.endsWith("__")) {
						logger.debug("GraalCodeExecutor#convertValueToJava 跳过特殊属性: {}", key);
						continue;
					}
					map.put(key, convertValueToJava(value.getMember(key), visited));
				}

				visited.remove(identityHash);
				return map;
			}
		} else {
			return value.toString();
		}
	}

	private String getStackTrace(Exception e) {
		ByteArrayOutputStream baos = new ByteArrayOutputStream();
		PrintStream ps = new PrintStream(baos, true, StandardCharsets.UTF_8);
		e.printStackTrace(ps);
		return baos.toString(StandardCharsets.UTF_8);
	}

	/**
	 * 将 CodeactTools 注入到 Python 环境中。
	 *
	 * <p>根据每个 CodeactTool 的元数据生成对应的 Python 函数或类方法，
	 * 当 Python 代码调用这些函数时，会回调到 Java 的 CodeactTool。
	 *
	 * @param context GraalVM Context
	 * @param registry CodeactTool 注册表
	 * @param language 编程语言
	 * @param toolContext 工具上下文
	 */
	private void injectCodeactTools(Context context,
                                    CodeactToolRegistry registry,
                                    Language language,
                                    ToolContext toolContext) {

		logger.info("GraalCodeExecutor#injectCodeactTools - reason=开始注入CodeactTool到Python环境, hasToolContext={}, toolContextKeys={}",
				toolContext != null,
				toolContext != null && toolContext.getContext() != null ? toolContext.getContext().keySet() : "null");

		// Use provided toolContext or create an empty one
		ToolContext effectiveToolContext = toolContext != null ? toolContext : new ToolContext(Map.of());

		logger.info("GraalCodeExecutor#injectCodeactTools - reason=effectiveToolContext构建完成, keys={}",
				effectiveToolContext.getContext() != null ? effectiveToolContext.getContext().keySet() : "null");

		// Create and inject ToolRegistryBridge using factory
		ToolRegistryBridge bridge = toolRegistryBridgeFactory.create(registry, effectiveToolContext);
		context.getBindings("python").putMember("__tool_registry__", bridge);
		logger.debug("GraalCodeExecutor#injectCodeactTools - reason=ToolRegistryBridge注入完成, bridgeClass={}",
				bridge.getClass().getSimpleName());

		// Get all tools for this language
		List<CodeactTool> tools = registry.getToolsForLanguage(language);

		if (tools.isEmpty()) {
			logger.debug("GraalCodeExecutor#injectCodeactTools - reason=没有支持该语言的工具, language={}", language);
			return;
		}

		// Group tools by targetClassName
		Map<String, List<CodeactTool>> toolsByClass = new HashMap<>();
		List<CodeactTool> globalTools = new ArrayList<>();

		for (CodeactTool tool : tools) {
			String className = tool.getCodeactMetadata().targetClassName();
			if (className != null && !className.isEmpty()) {
				toolsByClass.computeIfAbsent(className, k -> new ArrayList<>()).add(tool);
			} else {
				globalTools.add(tool);
			}
		}

		// Generate and execute Python code
		String pythonCode = generatePythonToolCode(toolsByClass, globalTools, effectiveToolContext);
		if (pythonCode != null && !pythonCode.isEmpty()) {
			logger.debug("GraalCodeExecutor#injectCodeactTools - reason=生成Python工具代码, length={}", pythonCode.length());
			context.eval("python", pythonCode);
		}

		logger.info("GraalCodeExecutor#injectCodeactTools - reason=CodeactTool注入完成, classCount={}, globalToolCount={}",
			toolsByClass.size(), globalTools.size());
	}

	/**
	 * 生成 Python 工具代码。
	 *
	 * <p>为每个工具生成 Python 函数，这些函数会调用 Java 的 CodeactTool。
	 *
	 * @param toolsByClass 按类名分组的工具
	 * @param globalTools 全局工具（没有类名）
	 * @param toolContext 工具上下文
	 * @return Python 代码字符串
	 */
	private String generatePythonToolCode(
			Map<String, List<CodeactTool>> toolsByClass,
			List<CodeactTool> globalTools,
			org.springframework.ai.chat.model.ToolContext toolContext) {

		StringBuilder code = new StringBuilder();
		code.append("# Generated CodeactTool bindings\n");
		code.append("import json\n");
		code.append("from typing import Any, Dict, List, Optional, Literal\n\n");

		// Generate tool classes
		for (Map.Entry<String, List<CodeactTool>> entry :
				toolsByClass.entrySet()) {
			String className = entry.getKey();
			List<CodeactTool> tools = entry.getValue();

			code.append(String.format("class %s:\n", className));
			code.append(String.format("    \"\"\"Generated class for %s tools\"\"\"\n", className));

			// Generate methods for this class
			for (CodeactTool tool : tools) {
				generatePythonMethod(code, tool, toolContext, true);
			}
			code.append("\n");
		}

		// Generate global functions
		for (CodeactTool tool : globalTools) {
			generatePythonMethod(code, tool, toolContext, false);
			code.append("\n");
		}

		return code.toString();
	}

	/**
	 * 生成单个工具的 Python 方法或函数。
	 *
	 * @param code StringBuilder to append code
	 * @param tool CodeactTool 实例
	 * @param toolContext 工具上下文
	 * @param isClassMethod 是否是类方法
	 */
	private void generatePythonMethod(StringBuilder code,
			CodeactTool tool,
			org.springframework.ai.chat.model.ToolContext toolContext,
			boolean isClassMethod) {

		String toolName = tool.getToolDefinition().name();
		String description = tool.getToolDefinition().description();

		// 优先使用 ParameterTree 获取参数信息
		ParameterTree parameterTree = tool.getParameterTree();

		String functionName = toolName;
		String parameters;
		List<String> requiredParams = new ArrayList<>();
		List<String> optionalParams = new ArrayList<>();
		List<String> allParamNames = new ArrayList<>();

		if (parameterTree != null && parameterTree.hasParameters()) {
			// 使用 ParameterTree 生成参数签名
			parameters = parameterTree.toPythonSignature();

			// 收集必填和可选参数名
			for (ParameterNode param : parameterTree.getParameters()) {
				String paramName = param.getName();
				allParamNames.add(paramName);
				if (param.isRequired()) {
					requiredParams.add(paramName);
				} else {
					optionalParams.add(paramName);
				}
			}
		} else {
			// 兼容旧方式：从 codeInvocationTemplate 提取参数
			String invocationTemplate = tool.getCodeactMetadata().codeInvocationTemplate();
			parameters = "**kwargs";

			if (invocationTemplate != null && invocationTemplate.contains("(")) {
				int parenIndex = invocationTemplate.indexOf('(');
				functionName = invocationTemplate.substring(0, parenIndex).trim();
				int endParenIndex = invocationTemplate.lastIndexOf(')');
				if (endParenIndex > parenIndex) {
					parameters = invocationTemplate.substring(parenIndex + 1, endParenIndex).trim();
					if (parameters.isEmpty()) {
						parameters = "**kwargs";
					}
				}
			}
		}

		// Generate method/function
		String indent = isClassMethod ? "    " : "";

		// Add @staticmethod for class methods
		if (isClassMethod) {
			code.append(String.format("%s@staticmethod\n", indent));
		}

		// Function definition
		code.append(String.format("%sdef %s(%s):\n", indent, functionName, parameters));
		code.append(String.format("%s    \"\"\"%s\"\"\"\n", indent, description != null ? description : toolName));

		// Function body - call Java tool through proxy
		code.append(String.format("%s    # Call Java CodeactTool\n", indent));
		code.append(String.format("%s    import json\n", indent));
		code.append(String.format("%s    \n", indent));
		code.append(String.format("%s    # Prepare arguments\n", indent));
		code.append(String.format("%s    args = {}\n", indent));

		// Build args dict from parameters
		if (!allParamNames.isEmpty()) {
			// 使用结构化参数信息
			// 必填参数直接添加
			for (String paramName : requiredParams) {
				code.append(String.format("%s    args['%s'] = %s\n", indent, paramName, paramName));
			}
			// 可选参数只在非 None 时添加
			for (String paramName : optionalParams) {
				code.append(String.format("%s    if %s is not None: args['%s'] = %s\n",
						indent, paramName, paramName, paramName));
			}
		} else if (!parameters.equals("**kwargs")) {
			// 兼容旧方式：从参数字符串解析
			String[] params = parameters.split(",");
			for (String param : params) {
				String paramName = param.trim().split(":")[0].split("=")[0].trim();
				if (!paramName.isEmpty() && !paramName.equals("self")) {
					// 检查是否有默认值（可选参数）
					boolean isOptional = param.contains("=");
					if (isOptional) {
						code.append(String.format("%s    if %s is not None: args['%s'] = %s\n",
							indent, paramName, paramName, paramName));
					} else {
						code.append(String.format("%s    args['%s'] = %s\n",
							indent, paramName, paramName));
					}
				}
			}
		} else {
			code.append(String.format("%s    args = kwargs\n", indent));
		}

		code.append(String.format("%s    \n", indent));
		code.append(String.format("%s    # Convert to JSON\n", indent));
		code.append(String.format("%s    args_json = json.dumps(args)\n", indent));
		code.append(String.format("%s    \n", indent));
		code.append(String.format("%s    # Call Java tool through __tool_registry__\n", indent));
		code.append(String.format("%s    result_json = __tool_registry__.callTool('%s', args_json)\n", indent, toolName));
		code.append(String.format("%s    \n", indent));
		code.append(String.format("%s    # Parse result\n", indent));
		code.append(String.format("%s    return json.loads(result_json)\n", indent));
		code.append("\n");
	}

	public CodeContext getCodeContext() {
		return codeContext;
	}

	public RuntimeEnvironmentManager getEnvironmentManager() {
		return environmentManager;
	}

}

