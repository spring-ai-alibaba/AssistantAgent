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
package com.alibaba.assistant.agent.core.tool.definition;

import com.alibaba.assistant.agent.common.tools.definition.ArrayShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.MapShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.PrimitiveShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.ReturnSchema;
import com.alibaba.assistant.agent.common.tools.definition.SchemaSource;
import com.alibaba.assistant.agent.common.tools.definition.ShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.UnionShapeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 返回值 Schema 合并器 - 实现 schema 的合并算法。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ReturnSchemaMerger {

	private static final Logger logger = LoggerFactory.getLogger(ReturnSchemaMerger.class);

	private ReturnSchemaMerger() {
		// 工具类，禁止实例化
	}

	/**
	 * 合并两个 ReturnSchema。
	 * @param existing 现有的 schema
	 * @param observed 新观测到的 shape
	 * @param success 是否成功返回
	 * @return 合并后的 ReturnSchema
	 */
	public static ReturnSchema merge(ReturnSchema existing, ShapeNode observed, boolean success) {
		if (existing == null) {
			// 创建新的 schema
			ReturnSchema.Builder builder = ReturnSchema.builder()
				.toolName("")
				.sampleCount(1)
				.lastUpdatedAt(Instant.now())
				.sources(EnumSet.of(SchemaSource.OBSERVED));

			if (success) {
				builder.successShape(observed);
			}
			else {
				builder.errorShape(observed);
			}

			return builder.build();
		}

		// 合并现有 schema
		ReturnSchema.Builder builder = ReturnSchema.builder()
			.toolName(existing.getToolName())
			.description(existing.getDescription())
			.typeHint(existing.getTypeHint())
			.sampleCount(existing.getSampleCount() + 1)
			.lastUpdatedAt(Instant.now());

		// 合并来源
		Set<SchemaSource> sources = EnumSet.copyOf(existing.getSources());
		sources.add(SchemaSource.OBSERVED);
		builder.sources(sources);

		// 合并 shape
		if (success) {
			ShapeNode mergedSuccessShape = mergeShapes(existing.getSuccessShape(), observed);
			builder.successShape(mergedSuccessShape);
			builder.errorShape(existing.getErrorShape());
		}
		else {
			ShapeNode mergedErrorShape = mergeShapes(existing.getErrorShape(), observed);
			builder.successShape(existing.getSuccessShape());
			builder.errorShape(mergedErrorShape);
		}

		logger.debug("ReturnSchemaMerger#merge - reason=合并ReturnSchema成功, toolName={}, sampleCount={}",
				existing.getToolName(), existing.getSampleCount() + 1);

		return builder.build();
	}

	/**
	 * 合并两个 ShapeNode。
	 * @param existing 现有的 shape
	 * @param observed 新观测到的 shape
	 * @return 合并后的 shape
	 */
	public static ShapeNode mergeShapes(ShapeNode existing, ShapeNode observed) {
		if (existing == null) {
			return observed;
		}
		if (observed == null) {
			return existing;
		}

		// 如果类型相同，进行合并
		if (existing.getClass().equals(observed.getClass())) {
			return mergeSameTypeShapes(existing, observed);
		}

		// 如果其中一个是未知类型，返回另一个
		if (existing.isUnknown()) {
			return observed;
		}
		if (observed.isUnknown()) {
			return existing;
		}

		// 类型不同，创建联合类型
		return createUnionShape(existing, observed);
	}

	/**
	 * 合并相同类型的 ShapeNode。
	 */
	private static ShapeNode mergeSameTypeShapes(ShapeNode existing, ShapeNode observed) {
		if (existing instanceof PrimitiveShapeNode) {
			// 原始类型直接返回
			return existing;
		}

		if (existing instanceof MapShapeNode existingMap && observed instanceof MapShapeNode observedMap) {
			return mergeMapShapes(existingMap, observedMap);
		}

		if (existing instanceof ObjectShapeNode existingObj && observed instanceof ObjectShapeNode observedObj) {
			return mergeObjectShapes(existingObj, observedObj);
		}

		if (existing instanceof ArrayShapeNode existingArr && observed instanceof ArrayShapeNode observedArr) {
			return mergeArrayShapes(existingArr, observedArr);
		}

		if (existing instanceof UnionShapeNode existingUnion && observed instanceof UnionShapeNode observedUnion) {
			return mergeUnionShapes(existingUnion, observedUnion);
		}

		return existing;
	}

	/**
	 * 合并两个 MapShapeNode：value shape 合并一次，observed keys 做并集。
	 */
	private static MapShapeNode mergeMapShapes(MapShapeNode existing, MapShapeNode observed) {
		ShapeNode mergedValue = mergeShapes(existing.getValueShape(), observed.getValueShape());
		MapShapeNode result = new MapShapeNode(mergedValue);
		result.addObservedKeys(existing.getObservedKeys());
		result.addObservedKeys(observed.getObservedKeys());
		return result;
	}

	/**
	 * 合并两个 ObjectShapeNode。
	 */
	private static ObjectShapeNode mergeObjectShapes(ObjectShapeNode existing, ObjectShapeNode observed) {
		ObjectShapeNode result = new ObjectShapeNode();

		// 复制现有字段
		for (Map.Entry<String, ShapeNode> entry : existing.getFields().entrySet()) {
			String fieldName = entry.getKey();
			ShapeNode existingField = entry.getValue();

			if (observed.hasField(fieldName)) {
				// 字段存在于两者，合并
				ShapeNode mergedField = mergeShapes(existingField, observed.getField(fieldName));
				result.putField(fieldName, mergedField);
			}
			else {
				// 字段只在现有中存在，标记为可选
				existingField.setOptional(true);
				result.putField(fieldName, existingField);
			}
		}

		// 添加新观测到的字段
		for (Map.Entry<String, ShapeNode> entry : observed.getFields().entrySet()) {
			String fieldName = entry.getKey();
			if (!existing.hasField(fieldName)) {
				ShapeNode newField = entry.getValue();
				newField.setOptional(true); // 新字段标记为可选
				result.putField(fieldName, newField);
			}
		}

		return result;
	}

	/**
	 * 合并两个 ArrayShapeNode。
	 */
	private static ArrayShapeNode mergeArrayShapes(ArrayShapeNode existing, ArrayShapeNode observed) {
		ShapeNode mergedItemShape = mergeShapes(existing.getItemShape(), observed.getItemShape());
		return new ArrayShapeNode(mergedItemShape);
	}

	/**
	 * 合并两个 UnionShapeNode。
	 */
	private static UnionShapeNode mergeUnionShapes(UnionShapeNode existing, UnionShapeNode observed) {
		UnionShapeNode result = new UnionShapeNode();

		// 添加现有变体
		for (ShapeNode variant : existing.getVariants()) {
			result.addVariant(variant);
		}

		// 添加新观测的变体（去重）
		for (ShapeNode variant : observed.getVariants()) {
			if (!containsEquivalent(result.getVariants(), variant)) {
				result.addVariant(variant);
			}
		}

		return result;
	}

	/**
	 * 创建联合类型。
	 */
	private static UnionShapeNode createUnionShape(ShapeNode first, ShapeNode second) {
		UnionShapeNode unionShape = new UnionShapeNode();

		// 如果第一个是联合类型，展开
		if (first instanceof UnionShapeNode firstUnion) {
			for (ShapeNode variant : firstUnion.getVariants()) {
				unionShape.addVariant(variant);
			}
		}
		else {
			unionShape.addVariant(first);
		}

		// 如果第二个是联合类型，展开
		if (second instanceof UnionShapeNode secondUnion) {
			for (ShapeNode variant : secondUnion.getVariants()) {
				if (!containsEquivalent(unionShape.getVariants(), variant)) {
					unionShape.addVariant(variant);
				}
			}
		}
		else {
			if (!containsEquivalent(unionShape.getVariants(), second)) {
				unionShape.addVariant(second);
			}
		}

		return unionShape;
	}

	/**
	 * 检查列表中是否包含等价的 shape。
	 */
	private static boolean containsEquivalent(java.util.List<ShapeNode> list, ShapeNode target) {
		for (ShapeNode node : list) {
			if (areEquivalent(node, target)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * 检查两个 shape 是否等价。
	 */
	private static boolean areEquivalent(ShapeNode a, ShapeNode b) {
		if (a == null && b == null) {
			return true;
		}
		if (a == null || b == null) {
			return false;
		}
		if (!a.getClass().equals(b.getClass())) {
			return false;
		}
		if (a instanceof PrimitiveShapeNode pa && b instanceof PrimitiveShapeNode pb) {
			return pa.getType() == pb.getType();
		}
		// 对于复杂类型，简化判断
		return a.getTypeName().equals(b.getTypeName());
	}

}
