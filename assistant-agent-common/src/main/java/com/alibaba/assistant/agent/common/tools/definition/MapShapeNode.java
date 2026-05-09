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
package com.alibaba.assistant.agent.common.tools.definition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Map 类型节点 - 表示动态 key 的字典结构（键为运行时数据，值具有一致的 shape）。
 *
 * <p>区别于 {@link ObjectShapeNode}（固定 schema 字段），{@code MapShapeNode}
 * 用于表示 {@code Map<String, V>} 语义：key 在编译期未知（如应用名、ID 等），
 * 但所有 value 具有相同的类型结构。
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class MapShapeNode extends ShapeNode {

	private ShapeNode valueShape;

	private final Set<String> observedKeys;

	public MapShapeNode() {
		super();
		this.valueShape = new UnknownShapeNode();
		this.observedKeys = new LinkedHashSet<>();
	}

	public MapShapeNode(ShapeNode valueShape) {
		super();
		this.valueShape = valueShape != null ? valueShape : new UnknownShapeNode();
		this.observedKeys = new LinkedHashSet<>();
	}

	public MapShapeNode(ShapeNode valueShape, boolean optional, String description) {
		super(optional, description);
		this.valueShape = valueShape != null ? valueShape : new UnknownShapeNode();
		this.observedKeys = new LinkedHashSet<>();
	}

	public ShapeNode getValueShape() {
		return valueShape;
	}

	public void setValueShape(ShapeNode valueShape) {
		this.valueShape = valueShape != null ? valueShape : new UnknownShapeNode();
	}

	public List<String> getObservedKeys() {
		return Collections.unmodifiableList(new ArrayList<>(observedKeys));
	}

	public void addObservedKey(String key) {
		if (key != null && !key.isBlank()) {
			observedKeys.add(key);
		}
	}

	public void addObservedKeys(Iterable<String> keys) {
		if (keys == null) {
			return;
		}
		for (String key : keys) {
			addObservedKey(key);
		}
	}

	@Override
	public String getPythonTypeHint() {
		String valueHint = valueShape.getPythonTypeHint();
		String hint = "Dict[str, " + valueHint + "]";
		if (optional) {
			return "Optional[" + hint + "]";
		}
		return hint;
	}

	@Override
	public String getTypeName() {
		return "map";
	}

	public boolean isMap() {
		return true;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (o == null || getClass() != o.getClass())
			return false;
		MapShapeNode that = (MapShapeNode) o;
		return Objects.equals(valueShape, that.valueShape);
	}

	@Override
	public int hashCode() {
		return Objects.hash(valueShape);
	}

	@Override
	public String toString() {
		return "MapShapeNode{valueShape=" + valueShape + ", observedKeys=" + observedKeys + ", optional=" + optional
				+ '}';
	}

}
