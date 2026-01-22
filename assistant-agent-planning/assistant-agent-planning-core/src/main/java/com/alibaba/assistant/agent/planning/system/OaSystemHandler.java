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
package com.alibaba.assistant.agent.planning.system;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.persistence.entity.ExternalSystemConfigEntity;
import com.alibaba.assistant.agent.planning.persistence.mapper.ExternalSystemConfigMapper;
import com.alibaba.assistant.agent.planning.spi.ActionProvider;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;

import java.util.*;

/**
 * OA系统通用处理器
 *
 * <p>职责：
 * <ul>
 * <li>从external_system_config读取OA系统配置</li>
 * <li>管理PHPSESSID session（获取、缓存）</li>
 * <li>根据action的interface_binding动态调用不同的OA接口</li>
 * <li>统一的错误处理和日志记录</li>
 * </ul>
 *
 * <h3>配置要求</h3>
 * <ul>
 * <li>external_system_config: system_id=oa-system, auth_type=SESSION</li>
 * <li>action_registry: system_id=oa-system, handler=oaSystemHandler</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component("oaSystemHandler")
public class OaSystemHandler implements SystemHandler {

    private static final Logger logger = LoggerFactory.getLogger(OaSystemHandler.class);

    private final ActionProvider actionProvider;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final ExternalSystemConfigMapper externalSystemConfigMapper;

    // Session缓存: key=systemId:assistantUserId, value=SessionInfo
    private final Map<String, SessionInfo> sessionCache = new HashMap<>();

    public OaSystemHandler(ActionProvider actionProvider,
                          RestTemplate restTemplate,
                          ExternalSystemConfigMapper externalSystemConfigMapper) {
        this.actionProvider = actionProvider;
        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.externalSystemConfigMapper = externalSystemConfigMapper;
        logger.info("OaSystemHandler#init - initialized");
    }

    @Override
    public Map<String, Object> execute(
            String actionId,
            Map<String, Object> params,
            Map<String, Object> context) {

        long startTime = System.currentTimeMillis();

        try {
            // 1. 获取Action定义
            ActionDefinition action = actionProvider.getAction(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Action不存在: " + actionId));

            String systemId = action.getSystemId();
            if (systemId == null) {
                throw new IllegalArgumentException("Action未配置system_id: " + actionId);
            }

            logger.info("OaSystemHandler#execute - actionId={}, systemId={}", actionId, systemId);

            // 2. 获取系统配置
            Map<String, Object> systemConfig = getSystemConfig(systemId);
            String baseUrl = (String) systemConfig.get("api_base_url");
            @SuppressWarnings("unchecked")
            Map<String, Object> authConfig = (Map<String, Object>) systemConfig.get("auth_config");

            // 3. 获取接口绑定配置
            StepDefinition.InterfaceBinding binding = action.getInterfaceBinding();
            if (binding == null) {
                throw new IllegalArgumentException("Action未配置interface_binding: " + actionId);
            }

            // 从InterfaceBinding提取配置
            StepDefinition.HttpConfig httpConfig = binding.getHttp();
            if (httpConfig == null) {
                throw new IllegalArgumentException("Action未配置HTTP配置: " + actionId);
            }

            String endpoint = httpConfig.getUrl();
            String method = httpConfig.getMethod();

            logger.debug("OaSystemHandler#execute - endpoint={}, method={}", endpoint, method);

            // 4. 获取用户session
            String assistantUserId = (String) context.get("assistantUserId");
            if (assistantUserId == null) {
                assistantUserId = (String) context.get("userId");
            }
            if (assistantUserId == null) {
                throw new IllegalArgumentException("context中缺少assistantUserId");
            }

            String phpsessid = getPhpSessionId(systemConfig, assistantUserId, baseUrl);

            // 5. 构建请求参数
            // 注意：parameterMapping应该从metadata中获取，或者使用其他方式
            Map<String, Object> parameterMapping = new HashMap<>();
            parameterMapping.put("start_date", "start_date");
            parameterMapping.put("end_date", "end_date");
            parameterMapping.put("types", "types");
            parameterMapping.put("reason", "reason");
            parameterMapping.put("check_uids", "check_uids");
            parameterMapping.put("flow_id", "1");
            parameterMapping.put("check_copy_uids", "0");
            parameterMapping.put("id", "0");

            MultiValueMap<String, String> requestParams = buildRequestParams(params, parameterMapping);

            // 6. 自动计算字段（如duration）
            autoCalculateFields(requestParams, Arrays.asList("duration"));

            // 7. 调用OA接口
            // 确保endpoint以/开头，baseUrl不以/结尾
            String normalizedBaseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
            String normalizedEndpoint = endpoint.startsWith("/") ? endpoint : "/" + endpoint;
            String url = normalizedBaseUrl + normalizedEndpoint;

            logger.info("OaSystemHandler#execute - calling OA API, actionId={}, url={}", actionId, url);

            Map<String, String> extraHeaders = httpConfig.getHeaders();

            ResponseEntity<String> response = callOaApi(
                url,
                method,
                requestParams,
                phpsessid,
                extraHeaders
            );

            long executionTime = System.currentTimeMillis() - startTime;

            logger.info("OaSystemHandler#execute - completed, actionId={}, time={}ms",
                actionId, executionTime);

            // 8. 解析并返回结果
            return parseOaResponse(response.getBody());

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("OaSystemHandler#execute - failed, actionId={}, time={}ms, error={}",
                actionId, executionTime, e.getMessage(), e);

            return Map.of(
                "success", false,
                "error", e.getMessage(),
                "action_id", actionId
            );
        }
    }

    /**
     * 获取系统配置（从数据库查询）
     */
    private Map<String, Object> getSystemConfig(String systemId) {
        logger.debug("OaSystemHandler#getSystemConfig - querying from database, systemId={}", systemId);

        // 从数据库查询系统配置
        ExternalSystemConfigEntity entity = externalSystemConfigMapper.selectBySystemId(systemId);

        if (entity == null) {
            throw new IllegalArgumentException("系统配置不存在: " + systemId);
        }

        if (!Boolean.TRUE.equals(entity.getEnabled())) {
            throw new IllegalArgumentException("系统已禁用: " + systemId);
        }

        // 转换为 Map 格式（保持与原有代码兼容）
        Map<String, Object> config = new HashMap<>();
        config.put("system_id", entity.getSystemId());
        config.put("api_base_url", entity.getApiBaseUrl());
        config.put("auth_config", entity.getAuthConfig());
        config.put("auth_type", entity.getAuthType());

        logger.info("OaSystemHandler#getSystemConfig - config loaded from database, systemId={}, apiBaseUrl={}",
                systemId, entity.getApiBaseUrl());

        return config;
    }

    /**
     * 获取PHPSESSID（带缓存）
     */
    private String getPhpSessionId(
            Map<String, Object> systemConfig,
            String assistantUserId,
            String baseUrl) {

        String systemId = (String) systemConfig.get("system_id");
        String cacheKey = systemId + ":" + assistantUserId;

        // 1. 尝试从缓存获取
        SessionInfo cached = sessionCache.get(cacheKey);
        if (cached != null && !cached.isExpired()) {
            logger.debug("OaSystemHandler#getPhpSessionId - cache hit, userId={}", assistantUserId);
            return cached.getPhpsessid();
        }

        // 2. 调用OA API获取
        @SuppressWarnings("unchecked")
        Map<String, Object> authConfig = (Map<String, Object>) systemConfig.get("auth_config");
        String sessionEndpoint = (String) authConfig.get("sessionEndpoint");
        String phpsessid = fetchPhpSessionId(baseUrl + sessionEndpoint, assistantUserId);

        // 3. 缓存
        long ttl = ((Number) authConfig.getOrDefault("sessionCacheTtl", 7200L)).longValue();
        sessionCache.put(cacheKey, new SessionInfo(phpsessid, ttl, System.currentTimeMillis()));

        logger.debug("OaSystemHandler#getPhpSessionId - fetched and cached, userId={}, ttl={}s",
            assistantUserId, ttl);

        return phpsessid;
    }

    /**
     * 从OA API获取PHPSESSID
     */
    private String fetchPhpSessionId(String sessionEndpoint, String assistantUserId) {
        logger.debug("OaSystemHandler#fetchPhpSessionId - endpoint={}, userId={}",
            sessionEndpoint, assistantUserId);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, String> body = Map.of("assistant_user_id", assistantUserId);
        HttpEntity<Map<String, String>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(
                sessionEndpoint,
                request,
                Map.class
            );

            @SuppressWarnings("unchecked")
            Map<String, Object> responseBody = response.getBody();
            if (responseBody == null || !Integer.valueOf(0).equals(responseBody.get("code"))) {
                throw new RuntimeException("获取session失败: " + responseBody);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) responseBody.get("data");
            String phpsessid = (String) data.get("phpsessid");

            logger.debug("OaSystemHandler#fetchPhpSessionId - success, phpsessid={}", phpsessid);
            return phpsessid;

        } catch (Exception e) {
            logger.error("OaSystemHandler#fetchPhpSessionId - failed, error={}", e.getMessage(), e);
            throw new RuntimeException("获取OA session失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建请求参数
     */
    private MultiValueMap<String, String> buildRequestParams(
            Map<String, Object> userParams,
            Map<String, Object> parameterMapping) {

        MultiValueMap<String, String> params = new LinkedMultiValueMap<>();

        if (parameterMapping == null || parameterMapping.isEmpty()) {
            return params;
        }

        parameterMapping.forEach((key, value) -> {
            if (value instanceof String strValue) {
                // 检查是否引用用户参数
                if (userParams.containsKey(strValue)) {
                    Object userValue = userParams.get(strValue);
                    // 处理Long等类型转换为String
                    params.add(key, String.valueOf(userValue));
                } else {
                    // 静态值
                    params.add(key, strValue);
                }
            } else {
                // 其他类型，直接转换
                params.add(key, String.valueOf(value));
            }
        });

        return params;
    }

    /**
     * 自动计算字段
     */
    private void autoCalculateFields(
            MultiValueMap<String, String> params,
            List<String> autoCalculateFields) {

        if (autoCalculateFields == null || autoCalculateFields.isEmpty()) {
            return;
        }

        for (String field : autoCalculateFields) {
            if ("duration".equals(field)) {
                String startDate = params.getFirst("start_date");
                String endDate = params.getFirst("end_date");
                long duration = calculateDuration(startDate, endDate);
                params.add("duration", String.valueOf(duration));
                logger.debug("OaSystemHandler#autoCalculateFields - duration={}", duration);
            }
        }
    }

    /**
     * 调用OA API
     */
    private ResponseEntity<String> callOaApi(
            String url,
            String method,
            MultiValueMap<String, String> params,
            String phpsessid,
            Map<String, String> extraHeaders) {

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

        // 设置Cookie（PHPSESSID）
        headers.set("Cookie", "PHPSESSID=" + phpsessid);

        // 额外的headers
        if (extraHeaders != null) {
            extraHeaders.forEach(headers::set);
        }

        HttpEntity<MultiValueMap<String, String>> request =
            new HttpEntity<>(params, headers);

        logger.debug("OaSystemHandler#callOaApi - url={}, method={}", url, method);
        logger.trace("OaSystemHandler#callOaApi - params={}", params);

        return restTemplate.postForEntity(url, request, String.class);
    }

    /**
     * 解析OA响应
     */
    private Map<String, Object> parseOaResponse(String responseBody) {
        try {
            // 尝试解析JSON
            Map<String, Object> result = objectMapper.readValue(
                responseBody,
                new TypeReference<Map<String, Object>>() {}
            );

            // OA接口格式：{code: 0, msg: "success", data: {...}}
            if (result.containsKey("code")) {
                Integer code = (Integer) result.get("code");
                if (code == 0) {
                    return Map.of(
                        "success", true,
                        "message", result.getOrDefault("msg", "操作成功"),
                        "data", result.getOrDefault("data", Map.of())
                    );
                } else {
                    return Map.of(
                        "success", false,
                        "error", result.getOrDefault("msg", "操作失败"),
                        "code", code
                    );
                }
            }

            // 其他JSON格式
            return Map.of("success", true, "data", result);

        } catch (Exception e) {
            // HTML响应或其他格式
            logger.debug("OaSystemHandler#parseOaResponse - not JSON response, length={}",
                responseBody != null ? responseBody.length() : 0);
            return Map.of(
                "success", true,
                "message", "操作已提交",
                "response", responseBody != null ? responseBody.substring(0, Math.min(200, responseBody.length())) : ""
            );
        }
    }

    /**
     * 计算请假天数（简化版）
     */
    private long calculateDuration(String startDate, String endDate) {
        // 简化处理：假设都是1天
        // TODO: 实际应该解析日期，计算工作日
        return 1L;
    }

    /**
     * Session信息（内部类）
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    private static class SessionInfo {
        private String phpsessid;
        private long ttl; // 秒
        private long createdAt; // 创建时间戳（毫秒）

        public boolean isExpired() {
            return System.currentTimeMillis() - createdAt > ttl * 1000;
        }
    }
}
