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
package com.alibaba.assistant.agent.core.tool.view;

import com.alibaba.assistant.agent.common.tools.CodeactTool;
import com.alibaba.assistant.agent.common.tools.CodeExample;
import com.alibaba.assistant.agent.common.tools.definition.ArrayShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterNode;
import com.alibaba.assistant.agent.common.tools.definition.ParameterTree;
import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import com.alibaba.assistant.agent.common.tools.definition.ShapeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * Python 工具视图渲染器 - 生成 Python 风格的代码 stub。
 *
 * <p>采用 Google style docstring 格式。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class PythonToolViewRenderer implements ToolViewRenderer {

	private static final Logger logger = LoggerFactory.getLogger(PythonToolViewRenderer.class);

	private static final String INDENT = "    ";

	private static final int MAX_FEWSHOTS = 3;

	@Override
	public String renderToolStub(CodeactTool tool, ReturnSchema returnSchema) {
		StringBuilder sb = new StringBuilder();

		// 获取工具名 - 从 ToolDefinition 获取原始名称
		String toolName = null;
		if (tool.getToolDefinition() != null) {
			toolName = tool.getToolDefinition().name();
		}

		// 如果还是空，尝试其他方式
		if (toolName == null || toolName.isEmpty()) {
			toolName = tool.getName();
		}

		// 最后的保底
		if (toolName == null || toolName.isEmpty()) {
			toolName = "unknown_function";
			logger.warn("PythonToolViewRenderer#renderToolStub - reason=工具名为空，使用默认名称, toolClass={}",
					tool.getClass().getSimpleName());
		}

		String description = tool.getDescription();
		ParameterTree parameterTree = tool.getParameterTree();

		// 生成函数签名
		String signature = generateSignature(toolName, parameterTree, returnSchema);
		sb.append("def ").append(signature).append(":\n");

		// 生成 docstring
		String docstring = generateDocstring(description, parameterTree, returnSchema, tool.getCodeactMetadata().fewShots());
		sb.append(docstring);

		// 函数体
		sb.append(INDENT).append("...\n");

		logger.debug("PythonToolViewRenderer#renderToolStub - reason=渲染工具stub成功, toolName={}", toolName);
		return sb.toString();
	}

	@Override
	public String renderClassStub(String className, String classDescription, List<CodeactTool> tools,
			ReturnSchemaProvider schemaProvider) {
		StringBuilder sb = new StringBuilder();

		// 生成类定义
		String pascalClassName = toPascalCase(className);
		sb.append("class ").append(pascalClassName).append(":\n");

		// 类 docstring
		if (classDescription != null && !classDescription.isBlank()) {
			sb.append(INDENT).append("\"\"\"").append(classDescription).append("\"\"\"\n\n");
		}

		// 生成每个方法
		for (CodeactTool tool : tools) {
			String toolName = tool.getName();
			ReturnSchema schema = schemaProvider != null ? schemaProvider.getSchema(toolName) : null;

			logger.debug("PythonToolViewRenderer#renderClassStub - reason=渲染方法, toolName={}, hasSchema={}, toolClass={}",
					toolName, schema != null, tool.getClass().getSimpleName());

			String methodStub = renderMethodStub(tool, schema);
			// 缩进方法
			String indentedMethod = indentLines(methodStub, INDENT);
			sb.append(indentedMethod).append("\n");
		}

		logger.debug("PythonToolViewRenderer#renderClassStub - reason=渲染类stub成功, className={}, methodCount={}",
				className, tools.size());
		return sb.toString();
	}

	/**
	 * 渲染方法 stub（不含类定义，用于嵌入类中）。
	 */
	private String renderMethodStub(CodeactTool tool, ReturnSchema returnSchema) {
		StringBuilder sb = new StringBuilder();

		// 获取方法名 - 优先从 codeInvocationTemplate 提取，与 GraalCodeExecutor 保持一致
		String methodName = extractMethodName(tool);

		String description = tool.getDescription();
		ParameterTree parameterTree = tool.getParameterTree();

		logger.debug("PythonToolViewRenderer#renderMethodStub - reason=渲染方法stub, methodName={}, hasParams={}",
				methodName, parameterTree != null && parameterTree.hasParameters());

		// 生成方法签名（带 self 参数）
		String signature = generateMethodSignature(methodName, parameterTree, returnSchema);
		sb.append("def ").append(signature).append(":\n");

		// 生成 docstring
		String docstring = generateDocstring(description, parameterTree, returnSchema, tool.getCodeactMetadata().fewShots());
		sb.append(docstring);

		// 方法体
		sb.append(INDENT).append("...\n");

		return sb.toString();
	}

	/**
	 * 生成函数签名。
	 */
	private String generateSignature(String funcName, ParameterTree parameterTree, ReturnSchema returnSchema) {
		StringBuilder sb = new StringBuilder();
		sb.append(funcName).append("(");

		if (parameterTree != null && parameterTree.hasParameters()) {
			sb.append(parameterTree.toPythonSignature());
		}

		sb.append(")");

		// 返回类型提示
		String returnType = getReturnTypeHint(returnSchema);
		sb.append(" -> ").append(returnType);

		return sb.toString();
	}

	/**
	 * 生成方法签名（带 self）。
	 */
	private String generateMethodSignature(String methodName, ParameterTree parameterTree, ReturnSchema returnSchema) {
		StringBuilder sb = new StringBuilder();
		sb.append(methodName).append("(self");

		if (parameterTree != null && parameterTree.hasParameters()) {
			sb.append(", ").append(parameterTree.toPythonSignature());
		}

		sb.append(")");

		// 返回类型提示
		String returnType = getReturnTypeHint(returnSchema);
		sb.append(" -> ").append(returnType);

		return sb.toString();
	}

	/**
	 * 生成 docstring。
	 */
	private String generateDocstring(String description, ParameterTree parameterTree, ReturnSchema returnSchema,
			List<CodeExample> fewShots) {
		StringBuilder sb = new StringBuilder();
		sb.append(INDENT).append("\"\"\"");

		// 描述
		if (description != null && !description.isBlank()) {
			sb.append(description);
		}
		sb.append("\n");

		// Args 部分
		if (parameterTree != null && parameterTree.hasParameters()) {
			sb.append(INDENT).append("\n");
			sb.append(INDENT).append("Args:\n");
			for (ParameterNode param : parameterTree.getParameters()) {
				sb.append(generateArgDoc(param));
			}
		}

		// Returns 部分
		sb.append(INDENT).append("\n");
		sb.append(INDENT).append("Returns:\n");
		sb.append(generateReturnsDoc(returnSchema));

		// Examples 部分
		if (fewShots != null && !fewShots.isEmpty()) {
			sb.append(INDENT).append("\n");
			sb.append(INDENT).append("Examples:\n");
			int count = 0;
			for (CodeExample example : fewShots) {
				if (count >= MAX_FEWSHOTS) {
					break;
				}
				sb.append(generateExampleDoc(example));
				count++;
			}
		}

		sb.append(INDENT).append("\"\"\"\n");
		return sb.toString();
	}

	/**
	 * 生成参数文档。
	 */
	private String generateArgDoc(ParameterNode param) {
		StringBuilder sb = new StringBuilder();
		sb.append(INDENT).append(INDENT);
		sb.append(param.getName());
		sb.append(" (").append(param.getPythonTypeHint());
		if (!param.isRequired()) {
			sb.append(", optional");
		}
		sb.append(")");
		if (param.getDescription() != null && !param.getDescription().isBlank()) {
			sb.append(": ").append(param.getDescription());
		}
		if (param.hasDefaultValue()) {
			sb.append(" 默认 ").append(param.getDefaultValue()).append("。");
		}
		sb.append("\n");

		// 如果是对象类型，展开属性
		if (param.getProperties() != null && !param.getProperties().isEmpty()) {
			for (ParameterNode prop : param.getProperties()) {
				sb.append(INDENT).append(INDENT).append(INDENT);
				sb.append("- ").append(prop.getName());
				sb.append(" (").append(prop.getPythonTypeHint()).append(")");
				if (prop.getDescription() != null) {
					sb.append(": ").append(prop.getDescription());
				}
				sb.append("\n");
			}
		}

		return sb.toString();
	}

	/**
	 * 生成返回值文档。
	 */
	private String generateReturnsDoc(ReturnSchema returnSchema) {
		StringBuilder sb = new StringBuilder();

		if (returnSchema == null) {
			logger.debug("PythonToolViewRenderer#generateReturnsDoc - reason=ReturnSchema为null，使用默认类型");
			sb.append(INDENT).append(INDENT).append("Dict[str, Any]: 操作结果\n");
			return sb.toString();
		}

		String typeHint = returnSchema.getPythonTypeHint();
		String description = returnSchema.getDescription();

		logger.debug("PythonToolViewRenderer#generateReturnsDoc - reason=生成返回值文档, typeHint={}, hasSuccessShape={}, sampleCount={}",
				typeHint, returnSchema.getSuccessShape() != null, returnSchema.getSampleCount());

		sb.append(INDENT).append(INDENT).append(typeHint).append(": ");
		if (description != null && !description.isBlank()) {
			sb.append(description);
		}
		else {
			sb.append("操作结果");
		}
		sb.append("\n");

		// 展开 successShape 的字段
		ShapeNode successShape = returnSchema.getSuccessShape();
		if (successShape != null) {
			logger.debug("PythonToolViewRenderer#generateReturnsDoc - reason=展开successShape, shapeType={}",
					successShape.getClass().getSimpleName());
			sb.append(generateShapeDoc(successShape, 3));
		}

		// 如果有 errorShape
		ShapeNode errorShape = returnSchema.getErrorShape();
		if (errorShape != null && errorShape instanceof ObjectShapeNode) {
			sb.append(INDENT).append(INDENT).append("失败时返回：\n");
			sb.append(generateShapeDoc(errorShape, 3));
		}

		return sb.toString();
	}

	/**
	 * 生成 shape 文档。
	 */
	private String generateShapeDoc(ShapeNode shape, int indentLevel) {
		StringBuilder sb = new StringBuilder();
		String baseIndent = INDENT.repeat(indentLevel);

		if (shape instanceof ObjectShapeNode objShape) {
			for (Map.Entry<String, ShapeNode> entry : objShape.getFields().entrySet()) {
				String fieldName = entry.getKey();
				ShapeNode fieldShape = entry.getValue();
				sb.append(baseIndent).append("- ").append(fieldName);
				sb.append(" (").append(fieldShape.getPythonTypeHint());
				if (fieldShape.isOptional()) {
					sb.append(", optional");
				}
				sb.append(")");
				if (fieldShape.getDescription() != null) {
					sb.append(": ").append(fieldShape.getDescription());
				}
				sb.append("\n");

				// 递归展开嵌套对象（最多 2 层）
				if (fieldShape instanceof ObjectShapeNode && indentLevel < 5) {
					sb.append(generateShapeDoc(fieldShape, indentLevel + 1));
				}
				else if (fieldShape instanceof ArrayShapeNode arrShape && arrShape.getItemShape() instanceof ObjectShapeNode
						&& indentLevel < 5) {
					sb.append(baseIndent).append(INDENT).append("每项包含：\n");
					sb.append(generateShapeDoc(arrShape.getItemShape(), indentLevel + 2));
				}
			}
		}
		else if (shape instanceof ArrayShapeNode arrShape) {
			sb.append(baseIndent).append("列表，每项为 ").append(arrShape.getItemShape().getPythonTypeHint()).append("\n");
			if (arrShape.getItemShape() instanceof ObjectShapeNode) {
				sb.append(generateShapeDoc(arrShape.getItemShape(), indentLevel + 1));
			}
		}

		return sb.toString();
	}

	/**
	 * 生成示例文档。
	 */
	private String generateExampleDoc(CodeExample example) {
		StringBuilder sb = new StringBuilder();
		sb.append(INDENT).append(INDENT);
		if (example.description() != null && !example.description().isBlank()) {
			sb.append("# ").append(example.description()).append("\n");
			sb.append(INDENT).append(INDENT);
		}
		sb.append(">>> ").append(example.codeSnippet()).append("\n");
		if (example.expectedBehavior() != null && !example.expectedBehavior().isBlank()) {
			sb.append(INDENT).append(INDENT).append("# ").append(example.expectedBehavior()).append("\n");
		}
		return sb.toString();
	}

	/**
	 * 获取返回类型提示。
	 */
	private String getReturnTypeHint(ReturnSchema returnSchema) {
		if (returnSchema != null) {
			return returnSchema.getPythonTypeHint();
		}
		return "Dict[str, Any]";
	}


	/**
	 * 转换为 PascalCase。
	 */
	private String toPascalCase(String input) {
		if (input == null || input.isEmpty()) {
			return input;
		}
		StringBuilder result = new StringBuilder();
		boolean capitalizeNext = true;
		for (char c : input.toCharArray()) {
			if (c == '_' || c == '-' || c == ' ') {
				capitalizeNext = true;
			}
			else if (capitalizeNext) {
				result.append(Character.toUpperCase(c));
				capitalizeNext = false;
			}
			else {
				result.append(c);
			}
		}
		return result.toString();
	}

	/**
	 * 为每行添加缩进。
	 */
	private String indentLines(String text, String indent) {
		if (text == null || text.isEmpty()) {
			return text;
		}
		StringBuilder sb = new StringBuilder();
		String[] lines = text.split("\n");
		for (int i = 0; i < lines.length; i++) {
			sb.append(indent).append(lines[i]);
			if (i < lines.length - 1) {
				sb.append("\n");
			}
		}
		return sb.toString();
	}

	/**
	 * 提取工具的方法名。
	 *
	 * <p>优先从 codeInvocationTemplate 中提取，与 GraalCodeExecutor.generatePythonMethod 保持一致。
	 * 这确保了 LLM 生成的代码中的方法名与实际注入到 Python 环境中的方法名一致。
	 *
	 * @param tool CodeactTool 实例
	 * @return 方法名
	 */
	private String extractMethodName(CodeactTool tool) {
		// 获取 ParameterTree 判断是否有结构化参数
		ParameterTree parameterTree = tool.getParameterTree();

		// 如果没有结构化参数，尝试从 codeInvocationTemplate 提取方法名
		// 这与 GraalCodeExecutor.generatePythonMethod 的逻辑一致
		if (parameterTree == null || !parameterTree.hasParameters()) {
			String invocationTemplate = tool.getCodeactMetadata() != null
					? tool.getCodeactMetadata().codeInvocationTemplate()
					: null;

			if (invocationTemplate != null && invocationTemplate.contains("(")) {
				int parenIndex = invocationTemplate.indexOf('(');
				String extractedName = invocationTemplate.substring(0, parenIndex).trim();
				if (!extractedName.isEmpty()) {
					logger.debug("PythonToolViewRenderer#extractMethodName - reason=从codeInvocationTemplate提取方法名, " +
							"methodName={}, toolClass={}", extractedName, tool.getClass().getSimpleName());
					return extractedName;
				}
			}
		}

		// 回退到 ToolDefinition.name()
		String methodName = null;
		if (tool.getToolDefinition() != null) {
			methodName = tool.getToolDefinition().name();
			logger.debug("PythonToolViewRenderer#extractMethodName - reason=从ToolDefinition获取名称, name={}, toolClass={}",
					methodName, tool.getClass().getSimpleName());
		}

		// 如果还是空，尝试 getName()
		if (methodName == null || methodName.isEmpty()) {
			methodName = tool.getName();
			logger.debug("PythonToolViewRenderer#extractMethodName - reason=尝试getName, name={}", methodName);
		}

		// 最后的保底
		if (methodName == null || methodName.isEmpty()) {
			methodName = "unknown_method";
			logger.warn("PythonToolViewRenderer#extractMethodName - reason=工具名为空使用默认名称, toolClass={}",
					tool.getClass().getSimpleName());
		}

		return methodName;
	}

}

