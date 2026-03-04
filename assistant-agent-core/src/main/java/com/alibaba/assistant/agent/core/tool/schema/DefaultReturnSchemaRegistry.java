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
package com.alibaba.assistant.agent.core.tool.schema;

import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import com.alibaba.assistant.agent.common.tools.definition.SchemaSource;
import com.alibaba.assistant.agent.common.tools.definition.ShapeNode;
import com.alibaba.assistant.agent.core.tool.definition.ReturnSchemaMerger;
import com.alibaba.assistant.agent.core.tool.definition.ShapeExtractor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 默认的返回值 schema 注册表实现（进程内）。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class DefaultReturnSchemaRegistry implements ReturnSchemaRegistry {

	private static final Logger logger = LoggerFactory.getLogger(DefaultReturnSchemaRegistry.class);

	/**
	 * 存储声明的 schema。
	 */
	private final Map<String, ReturnSchema> declaredSchemas = new ConcurrentHashMap<>();

	/**
	 * 存储合并后的 schema（声明 + 观测）。
	 */
	private final Map<String, ReturnSchema> mergedSchemas = new ConcurrentHashMap<>();

	public DefaultReturnSchemaRegistry() {
		logger.info("DefaultReturnSchemaRegistry#<init> - reason=创建新实例, hashCode={}", System.identityHashCode(this));
	}

	@Override
	public void registerDeclared(String toolName, ReturnSchema declaredSchema) {
		if (toolName == null || toolName.isBlank()) {
			logger.warn("DefaultReturnSchemaRegistry#registerDeclared - reason=工具名为空，跳过注册");
			return;
		}
		if (declaredSchema == null) {
			logger.debug("DefaultReturnSchemaRegistry#registerDeclared - reason=声明schema为空，跳过注册, toolName={}",
					toolName);
			return;
		}

		// 存储声明的 schema
		ReturnSchema schemaWithToolName = ReturnSchema.builder()
			.toolName(toolName)
			.successShape(declaredSchema.getSuccessShape())
			.errorShape(declaredSchema.getErrorShape())
			.description(declaredSchema.getDescription())
			.typeHint(declaredSchema.getTypeHint())
			.sampleCount(0)
			.lastUpdatedAt(Instant.now())
			.sources(EnumSet.of(SchemaSource.DECLARED))
			.build();

		declaredSchemas.put(toolName, schemaWithToolName);
		mergedSchemas.put(toolName, schemaWithToolName);

		logger.debug("DefaultReturnSchemaRegistry#registerDeclared - reason=注册声明schema成功, toolName={}", toolName);
	}

	@Override
	public void observe(String toolName, String resultJson, boolean success) {
		logger.info("DefaultReturnSchemaRegistry#observe - reason=开始观测, hashCode={}, toolName={}, success={}",
				System.identityHashCode(this), toolName, success);

		if (toolName == null || toolName.isBlank()) {
			logger.warn("DefaultReturnSchemaRegistry#observe - reason=工具名为空，跳过观测");
			return;
		}
		if (resultJson == null || resultJson.isBlank()) {
			logger.debug("DefaultReturnSchemaRegistry#observe - reason=返回值为空，跳过观测, toolName={}", toolName);
			return;
		}

		// 提取 shape
		ShapeNode observedShape = ShapeExtractor.extract(resultJson);

		logger.info("DefaultReturnSchemaRegistry#observe - reason=提取shape成功, toolName={}, shapeType={}, resultJsonLength={}",
				toolName, observedShape != null ? observedShape.getClass().getSimpleName() : "null", resultJson.length());

		// 获取现有 schema
		ReturnSchema existingSchema = mergedSchemas.get(toolName);

		// 合并
		ReturnSchema mergedSchema = ReturnSchemaMerger.merge(existingSchema, observedShape, success);

		// 更新工具名
		mergedSchema = ReturnSchema.builder()
			.toolName(toolName)
			.successShape(mergedSchema.getSuccessShape())
			.errorShape(mergedSchema.getErrorShape())
			.description(mergedSchema.getDescription())
			.typeHint(mergedSchema.getTypeHint())
			.sampleCount(mergedSchema.getSampleCount())
			.lastUpdatedAt(mergedSchema.getLastUpdatedAt())
			.sources(mergedSchema.getSources())
			.build();

		mergedSchemas.put(toolName, mergedSchema);

		logger.info(
				"DefaultReturnSchemaRegistry#observe - reason=观测工具返回值结构成功, hashCode={}, toolName={}, success={}, sampleCount={}, hasSuccessShape={}, totalTools={}",
				System.identityHashCode(this), toolName, success, mergedSchema.getSampleCount(),
				mergedSchema.getSuccessShape() != null, mergedSchemas.size());
	}

	@Override
	public Optional<ReturnSchema> getSchema(String toolName) {
		if (toolName == null || toolName.isBlank()) {
			return Optional.empty();
		}
		ReturnSchema schema = mergedSchemas.get(toolName);
		logger.debug("DefaultReturnSchemaRegistry#getSchema - reason=查询schema, hashCode={}, toolName={}, found={}, allTools={}",
				System.identityHashCode(this), toolName, schema != null, mergedSchemas.keySet());
		return Optional.ofNullable(schema);
	}

	@Override
	public List<String> getToolsWithSchema() {
		return new ArrayList<>(mergedSchemas.keySet());
	}

	@Override
	public void clearObserved(String toolName) {
		if (toolName == null || toolName.isBlank()) {
			return;
		}

		// 如果有声明的 schema，恢复到声明状态
		ReturnSchema declaredSchema = declaredSchemas.get(toolName);
		if (declaredSchema != null) {
			mergedSchemas.put(toolName, declaredSchema);
			logger.debug("DefaultReturnSchemaRegistry#clearObserved - reason=清除观测数据并恢复声明schema, toolName={}",
					toolName);
		}
		else {
			mergedSchemas.remove(toolName);
			logger.debug("DefaultReturnSchemaRegistry#clearObserved - reason=清除观测数据, toolName={}", toolName);
		}
	}

	@Override
	public void clearAllObserved() {
		// 恢复所有到声明状态
		mergedSchemas.clear();
		mergedSchemas.putAll(declaredSchemas);
		logger.debug("DefaultReturnSchemaRegistry#clearAllObserved - reason=清除所有观测数据");
	}

}

