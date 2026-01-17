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
package com.alibaba.assistant.agent.planning.executor;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.spi.ActionExecutor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.lang.reflect.Method;
import java.util.Map;

/**
 * 内部服务执行器
 *
 * <p>执行 INTERNAL 类型的 Action，调用 Spring Bean 方法。
 *
 * <h3>功能特性</h3>
 * <ul>
 * <li>调用 Spring Bean 方法</li>
 * <li>自动参数类型转换</li>
 * <li>支持方法重载</li>
 * <li>处理反射异常</li>
 * </ul>
 *
 * <h3>配置示例</h3>
 * <pre>
 * binding:
 *   type: INTERNAL
 *   beanName: userService
 *   methodName: getUserById
 *   methodParams:
 *     - name: userId
 *       type: java.lang.Long
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Component
public class InternalExecutor implements ActionExecutor {

    private static final Logger logger = LoggerFactory.getLogger(InternalExecutor.class);

    private final ApplicationContext applicationContext;

    public InternalExecutor(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
        logger.info("InternalExecutor#init - initialized");
    }

    @Override
    public String getExecutorType() {
        return "INTERNAL";
    }

    @Override
    public int getPriority() {
        return 80;
    }

    @Override
    public ExecutionResult execute(ActionDefinition action,
                                   Map<String, Object> params,
                                   Integer timeoutSeconds) {
        long startTime = System.currentTimeMillis();

        StepDefinition.InterfaceBinding binding = action.getBinding();
        StepDefinition.InternalConfig internalConfig = binding.getInternal();

        String beanName = internalConfig.getBeanName();
        String methodName = internalConfig.getMethodName();

        logger.info("InternalExecutor#execute - invoking bean method, actionId={}, bean={}, method={}",
                action.getActionId(), beanName, methodName);

        try {
            // 1. 获取 Spring Bean
            Object bean = applicationContext.getBean(beanName);

            // 2. 查找方法
            Method method = findMethod(bean.getClass(), methodName, internalConfig.getMethodParams());

            // 3. 准备参数
            Object[] args = prepareArguments(method, params);

            // 4. 调用方法
            Object result = method.invoke(bean, args);

            long executionTime = System.currentTimeMillis() - startTime;

            logger.info("InternalExecutor#execute - method invoked successfully, actionId={}, time={}ms",
                    action.getActionId(), executionTime);

            return ExecutionResult.builder()
                    .success(true)
                    .responseData(result)
                    .executionTimeMs(executionTime)
                    .build();

        } catch (Exception e) {
            long executionTime = System.currentTimeMillis() - startTime;
            logger.error("InternalExecutor#execute - method invocation failed, actionId={}, time={}ms",
                    action.getActionId(), executionTime, e);
            return ExecutionResult.failure("内部方法调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查找方法
     */
    private Method findMethod(Class<?> beanClass, String methodName,
                             java.util.List<StepDefinition.MethodParam> methodParams)
            throws NoSuchMethodException, ClassNotFoundException {
        Class<?>[] paramTypes = null;

        if (methodParams != null && !methodParams.isEmpty()) {
            paramTypes = new Class<?>[methodParams.size()];
            for (int i = 0; i < methodParams.size(); i++) {
                String typeName = methodParams.get(i).getType();
                paramTypes[i] = resolveClass(typeName);
            }
        }

        if (paramTypes == null) {
            // 查找无参方法
            return beanClass.getMethod(methodName);
        } else {
            // 查找带参数的方法
            return beanClass.getMethod(methodName, paramTypes);
        }
    }

    /**
     * 解析类
     */
    private Class<?> resolveClass(String typeName) throws ClassNotFoundException {
        if (!StringUtils.hasText(typeName)) {
            throw new IllegalArgumentException("Type name cannot be empty");
        }

        // 支持基本类型
        return switch (typeName) {
            case "int" -> int.class;
            case "long" -> long.class;
            case "double" -> double.class;
            case "float" -> float.class;
            case "boolean" -> boolean.class;
            case "short" -> short.class;
            case "byte" -> byte.class;
            case "char" -> char.class;
            default -> Class.forName(typeName);
        };
    }

    /**
     * 准备方法参数
     */
    private Object[] prepareArguments(Method method, Map<String, Object> params) {
        Class<?>[] paramTypes = method.getParameterTypes();
        Object[] args = new Object[paramTypes.length];

        if (params == null || params.isEmpty()) {
            return args;
        }

        // 按参数名顺序赋值
        int index = 0;
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            if (index < args.length) {
                args[index] = convertType(entry.getValue(), paramTypes[index]);
                index++;
            }
        }

        return args;
    }

    /**
     * 类型转换
     */
    private Object convertType(Object value, Class<?> targetType) {
        if (value == null) {
            return null;
        }

        if (targetType.isAssignableFrom(value.getClass())) {
            return value;
        }

        // 简单类型转换
        if (targetType == String.class) {
            return value.toString();
        } else if (targetType == Integer.class || targetType == int.class) {
            if (value instanceof Number) {
                return ((Number) value).intValue();
            }
            return Integer.parseInt(value.toString());
        } else if (targetType == Long.class || targetType == long.class) {
            if (value instanceof Number) {
                return ((Number) value).longValue();
            }
            return Long.parseLong(value.toString());
        } else if (targetType == Double.class || targetType == double.class) {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        } else if (targetType == Boolean.class || targetType == boolean.class) {
            if (value instanceof Boolean) {
                return value;
            }
            return Boolean.parseBoolean(value.toString());
        }

        logger.warn("InternalExecutor#convertType - cannot convert {} to {}, returning as-is",
                value.getClass(), targetType);
        return value;
    }
}
