package com.alibaba.assistant.agent.autoconfigure.tools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class WriteCodeToolRequestDeserializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldDeserializeEmptyParametersArray() throws Exception {
		WriteCodeTool.Request request = objectMapper.readValue("""
				{
				  "functionName": "get_current_time",
				  "description": "获取当前时间",
				  "parameters": [],
				  "code": "def get_current_time():\\n    return {\\"success\\": True}"
				}
				""", WriteCodeTool.Request.class);

		assertNotNull(request.parameters);
		assertEquals(List.of(), request.parameters);
	}

	@Test
	void shouldTolerateNestedEmptyParametersArray() throws Exception {
		WriteCodeTool.Request request = objectMapper.readValue("""
				{
				  "functionName": "get_broadcast_subscription_apply_link",
				  "description": "获取申请链接",
				  "parameters": [[]],
				  "code": "def get_broadcast_subscription_apply_link():\\n    return {\\"success\\": True}"
				}
				""", WriteCodeTool.Request.class);

		assertNotNull(request.parameters);
		assertEquals(List.of(), request.parameters);
	}

	@Test
	void shouldFlattenNestedStringArrays() throws Exception {
		WriteCodeTool.Request request = objectMapper.readValue("""
				{
				  "functionName": "search_info",
				  "description": "搜索信息",
				  "parameters": [["query"], "limit"],
				  "code": "def search_info(query, limit):\\n    return {\\"success\\": True}"
				}
				""", WriteCodeTool.Request.class);

		assertEquals(List.of("query", "limit"), request.parameters);
	}
}
