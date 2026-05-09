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
import com.alibaba.assistant.agent.common.tools.definition.PrimitiveType;
import com.alibaba.assistant.agent.common.tools.definition.ShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.UnknownShapeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Shape 提取器 - 从 JSON 字符串提取 ShapeNode。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class ShapeExtractor {

	private static final Logger logger = LoggerFactory.getLogger(ShapeExtractor.class);

	private static final ObjectMapper objectMapper = new ObjectMapper();

	private ShapeExtractor() {
		// 工具类，禁止实例化
	}

	/**
	 * 从 JSON 字符串提取 ShapeNode。
	 * @param json JSON 字符串
	 * @return 提取的 ShapeNode
	 */
	public static ShapeNode extract(String json) {
		if (json == null || json.isBlank()) {
			logger.debug("ShapeExtractor#extract - reason=JSON为空，返回UnknownShapeNode");
			return new UnknownShapeNode();
		}

		try {
			JsonNode rootNode = objectMapper.readTree(json);
			return extractFromJsonNode(rootNode);
		}
		catch (Exception e) {
			logger.warn("ShapeExtractor#extract - reason=解析JSON失败，返回UnknownShapeNode, error={}", e.getMessage());
			return new UnknownShapeNode();
		}
	}

	/**
	 * 从 JsonNode 提取 ShapeNode。
	 * @param node JSON 节点
	 * @return 提取的 ShapeNode
	 */
	public static ShapeNode extractFromJsonNode(JsonNode node) {
		if (node == null || node.isNull()) {
			return new PrimitiveShapeNode(PrimitiveType.NULL);
		}

		if (node.isTextual()) {
			return new PrimitiveShapeNode(PrimitiveType.STRING);
		}

		if (node.isInt() || node.isLong()) {
			return new PrimitiveShapeNode(PrimitiveType.INTEGER);
		}

		if (node.isDouble() || node.isFloat() || node.isNumber()) {
			return new PrimitiveShapeNode(PrimitiveType.NUMBER);
		}

		if (node.isBoolean()) {
			return new PrimitiveShapeNode(PrimitiveType.BOOLEAN);
		}

		if (node.isArray()) {
			return extractArrayShape(node);
		}

		if (node.isObject()) {
			return extractObjectShape(node);
		}

		return new UnknownShapeNode();
	}

	/**
	 * 从数组节点提取 ArrayShapeNode。
	 * @param arrayNode 数组节点
	 * @return ArrayShapeNode
	 */
	private static ArrayShapeNode extractArrayShape(JsonNode arrayNode) {
		if (arrayNode.isEmpty()) {
			// 空数组，元素类型未知
			return new ArrayShapeNode(new UnknownShapeNode());
		}

		// 从第一个元素推断元素类型
		JsonNode firstElement = arrayNode.get(0);
		ShapeNode itemShape = extractFromJsonNode(firstElement);

		// 如果有多个元素，尝试合并它们的 shape
		if (arrayNode.size() > 1) {
			for (int i = 1; i < arrayNode.size(); i++) {
				ShapeNode otherShape = extractFromJsonNode(arrayNode.get(i));
				itemShape = ReturnSchemaMerger.mergeShapes(itemShape, otherShape);
			}
		}

		return new ArrayShapeNode(itemShape);
	}

	/**
	 * 从对象节点提取 ShapeNode。
	 * <p>
	 * 若 JSON object 的 key 包含非标识符字符（如连字符 {@code -}、点 {@code .}、空格等），
	 * 说明 key 是运行时动态数据（应用名、ID 等），整体应视为 {@link MapShapeNode}；
	 * 否则视为固定 schema 字段，返回 {@link ObjectShapeNode}。
	 *
	 * @param objectNode 对象节点
	 * @return ObjectShapeNode 或 MapShapeNode
	 */
	private static ShapeNode extractObjectShape(JsonNode objectNode) {
		if (objectNode.isEmpty()) {
			return new ObjectShapeNode();
		}

		if (isMapLikeObject(objectNode)) {
			return extractMapShape(objectNode);
		}

		ObjectShapeNode shapeNode = new ObjectShapeNode();
		Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			ShapeNode fieldShape = extractFromJsonNode(field.getValue());
			shapeNode.putField(field.getKey(), fieldShape);
		}
		return shapeNode;
	}

	/**
	 * 从 map-like 对象节点提取 MapShapeNode，合并所有 value 的 shape。
	 */
	private static MapShapeNode extractMapShape(JsonNode objectNode) {
		MapShapeNode mapShape = new MapShapeNode();
		ShapeNode valueShape = null;
		Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> field = fields.next();
			mapShape.addObservedKey(field.getKey());
			ShapeNode current = extractFromJsonNode(field.getValue());
			valueShape = (valueShape == null) ? current : ReturnSchemaMerger.mergeShapes(valueShape, current);
		}
		mapShape.setValueShape(valueShape != null ? valueShape : new UnknownShapeNode());
		return mapShape;
	}

	/**
	 * 判断 JSON object 是否为 map-like（key 为动态数据而非固定 schema 字段）。
	 * <p>
	 * 判断依据：
	 * <ul>
	 *     <li>若 key 含有非标识符字符，则认为 key 本身就是动态数据</li>
	 *     <li>否则若多个字段的 value shape 一致/可合并，则也视为 Map；
	 *     此时 key 只是数据实例，schema 只需保留一份 value 结构</li>
	 * </ul>
	 */
	private static boolean isMapLikeObject(JsonNode objectNode) {
		List<Map.Entry<String, JsonNode>> entries = new ArrayList<>();
		Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
		while (fields.hasNext()) {
			Map.Entry<String, JsonNode> entry = fields.next();
			entries.add(entry);
			if (!isIdentifierKey(entry.getKey())) {
				return true;
			}
		}
		return hasHomogeneousValueShapes(entries);
	}

	private static boolean hasHomogeneousValueShapes(List<Map.Entry<String, JsonNode>> entries) {
		if (entries.size() < 2) {
			return false;
		}
		ShapeNode merged = null;
		for (Map.Entry<String, JsonNode> entry : entries) {
			ShapeNode current = extractFromJsonNode(entry.getValue());
			if (merged == null) {
				merged = current;
				continue;
			}
			if (!isCompatibleMapValueShape(merged, current)) {
				return false;
			}
			merged = ReturnSchemaMerger.mergeShapes(merged, current);
		}
		return true;
	}

	private static boolean isCompatibleMapValueShape(ShapeNode left, ShapeNode right) {
		if (left == null || right == null) {
			return false;
		}
		if (left.isUnknown() || right.isUnknown()) {
			return true;
		}
		if (left instanceof PrimitiveShapeNode leftPrimitive && right instanceof PrimitiveShapeNode rightPrimitive) {
			return leftPrimitive.getType() == rightPrimitive.getType();
		}
		return left.getClass().equals(right.getClass());
	}

	/**
	 * 判断字符串是否是合法的标识符格式（仅含字母、数字、下划线、$，且不以数字开头）。
	 */
	private static boolean isIdentifierKey(String key) {
		if (key == null || key.isEmpty()) {
			return false;
		}
		char first = key.charAt(0);
		if (!Character.isLetter(first) && first != '_' && first != '$') {
			return false;
		}
		for (int i = 1; i < key.length(); i++) {
			char c = key.charAt(i);
			if (!Character.isLetterOrDigit(c) && c != '_' && c != '$') {
				return false;
			}
		}
		return true;
	}

}
