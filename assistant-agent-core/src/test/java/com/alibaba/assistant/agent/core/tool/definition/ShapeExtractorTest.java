package com.alibaba.assistant.agent.core.tool.definition;

import com.alibaba.assistant.agent.common.tools.definition.MapShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.ObjectShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.PrimitiveShapeNode;
import com.alibaba.assistant.agent.common.tools.definition.PrimitiveType;
import com.alibaba.assistant.agent.common.tools.definition.ShapeNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShapeExtractorTest {

    @Test
    void shouldExtractMapShapeNodeForDynamicKeys() {
        ShapeNode shape = ShapeExtractor.extract("""
                {
                  "app-center": {
                    "owner": "alice",
                    "memberCount": 3
                  },
                  "meow-agent": {
                    "owner": "bob"
                  }
                }
                """);

        MapShapeNode mapShape = assertInstanceOf(MapShapeNode.class, shape);
        ObjectShapeNode valueShape = assertInstanceOf(ObjectShapeNode.class, mapShape.getValueShape());
        assertIterableEquals(java.util.List.of("app-center", "meow-agent"), mapShape.getObservedKeys());

        PrimitiveShapeNode ownerShape = assertInstanceOf(PrimitiveShapeNode.class, valueShape.getField("owner"));
        assertEquals(PrimitiveType.STRING, ownerShape.getType());

        PrimitiveShapeNode memberCountShape =
                assertInstanceOf(PrimitiveShapeNode.class, valueShape.getField("memberCount"));
        assertEquals(PrimitiveType.INTEGER, memberCountShape.getType());
        assertTrue(memberCountShape.isOptional());
    }

    @Test
    void shouldExtractMapShapeNodeForIndexedSiblingKeys() {
        ShapeNode shape = ShapeExtractor.extract("""
                {
                  "tag1": {
                    "id": 1,
                    "name": "tag1",
                    "value": "value1"
                  },
                  "tag2": {
                    "id": 2,
                    "name": "tag2",
                    "value": "value2"
                  }
                }
                """);

        MapShapeNode mapShape = assertInstanceOf(MapShapeNode.class, shape);
        ObjectShapeNode valueShape = assertInstanceOf(ObjectShapeNode.class, mapShape.getValueShape());
        assertIterableEquals(java.util.List.of("tag1", "tag2"), mapShape.getObservedKeys());
        assertTrue(valueShape.hasField("id"));
        assertTrue(valueShape.hasField("name"));
        assertTrue(valueShape.hasField("value"));
    }

    @Test
    void shouldMergeMapValueShapesWithoutExpandingDynamicKeys() {
        MapShapeNode first = new MapShapeNode(new ObjectShapeNode());
        first.addObservedKey("tag1");
        ((ObjectShapeNode) first.getValueShape()).putField("owner", new PrimitiveShapeNode(PrimitiveType.STRING));

        MapShapeNode second = new MapShapeNode(new ObjectShapeNode());
        second.addObservedKey("tag2");
        ((ObjectShapeNode) second.getValueShape()).putField("repoCount", new PrimitiveShapeNode(PrimitiveType.INTEGER));

        ShapeNode merged = ReturnSchemaMerger.mergeShapes(first, second);

        MapShapeNode mergedMap = assertInstanceOf(MapShapeNode.class, merged);
        ObjectShapeNode mergedValue = assertInstanceOf(ObjectShapeNode.class, mergedMap.getValueShape());
        assertIterableEquals(java.util.List.of("tag1", "tag2"), mergedMap.getObservedKeys());
        assertEquals(2, mergedValue.getFieldCount());
        assertTrue(mergedValue.hasField("owner"));
        assertTrue(mergedValue.hasField("repoCount"));
        assertTrue(mergedValue.getField("owner").isOptional());
        assertTrue(mergedValue.getField("repoCount").isOptional());
    }

    @Test
    void shouldKeepRegularObjectWhenValueShapesAreDifferent() {
        ShapeNode shape = ShapeExtractor.extract("""
                {
                  "owner": "alice",
                  "repoCount": 3,
                  "enabled": true
                }
                """);

        ObjectShapeNode objectShape = assertInstanceOf(ObjectShapeNode.class, shape);
        assertTrue(objectShape.hasField("owner"));
        assertTrue(objectShape.hasField("repoCount"));
        assertTrue(objectShape.hasField("enabled"));
    }
}
