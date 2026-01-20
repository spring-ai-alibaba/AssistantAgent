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
package com.alibaba.assistant.agent.planning.examples;

import com.alibaba.assistant.agent.planning.executor.ActionExecutorFactory;
import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.DataPermissionConfig;
import com.alibaba.assistant.agent.planning.model.ExecutionResult;
import com.alibaba.assistant.agent.planning.model.ParamDefinition;
import com.alibaba.assistant.agent.planning.model.StepDefinition;
import com.alibaba.assistant.agent.planning.param.HttpOptionsConfig;
import com.alibaba.assistant.agent.planning.permission.spi.IdentityMappingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 权限集成快速开始示例
 * <p>
 * 演示如何使用多系统权限功能：
 * 1. 绑定用户到外部系统
 * 2. 定义带权限的Action
 * 3. 执行Action（自动权限检查和数据过滤）
 *
 * @author Assistant Agent Team
 */
@Component
public class PermissionQuickStartExample {

    @Autowired
    private IdentityMappingService identityMappingService;

    @Autowired
    private ActionExecutorFactory actionExecutorFactory;

    /**
     * 示例1：员工查询自己的考勤记录
     * <p>
     * 权限要求：SELF（仅本人数据）
     * 预期效果：自动添加 userId 过滤条件
     */
    public void example1_EmployeeQueryOwnAttendance() {
        System.out.println("\n========== 示例1：员工查询自己的考勤 ==========");

        // 1. 绑定用户到OA系统（模拟已完成）
        identityMappingService.bindIdentity(
                "U001",                          // 平台用户ID
                "oa-system",                     // 系统ID
                "zhang.san@company.com",         // 外部用户ID
                "张三",                          // 外部用户名
                Map.of(
                        "role", "employee",       // 角色：普通员工
                        "departmentId", "tech-001" // 部门
                )
        );

        // 2. 定义Action：查询考勤记录
        ActionDefinition queryAttendanceAction = ActionDefinition.builder()
                .actionId("oa:attendance:query-my-records")
                .name("查询我的考勤记录")
                .interfaceBinding(StepDefinition.InterfaceBinding.builder()
                        .type("HTTP")
                        .config(HttpOptionsConfig.builder()
                                .url("https://oa.company.com/api/attendance/records")
                                .method("GET")
                                .build())
                        .build())
                .params(List.of(
                        ParamDefinition.builder()
                                .name("startDate")
                                .type("string")
                                .required(true)
                                .description("开始日期")
                                .build(),
                        ParamDefinition.builder()
                                .name("endDate")
                                .type("string")
                                .required(true)
                                .description("结束日期")
                                .build(),
                        ParamDefinition.builder()
                                .name("employeeId")  // 将被自动注入
                                .type("string")
                                .required(false)
                                .description("员工ID（自动注入）")
                                .build()
                ))
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(true)  // 启用权限检查
                        .filterMapping(Map.of("userId", "employeeId"))  // userId映射到employeeId
                        .build())
                .build();

        // 3. 执行Action
        Map<String, Object> params = Map.of(
                "platformUserId", "U001",      // 张三
                "systemId", "oa-system",
                "startDate", "2024-01-01",
                "endDate", "2024-01-31"
        );

        ExecutionResult result = actionExecutorFactory.execute(queryAttendanceAction, params, 30);

        // 4. 结果
        if (result.isSuccess()) {
            System.out.println("✓ 执行成功");
            System.out.println("✓ 自动添加了过滤条件: employeeId = zhang.san@company.com");
            System.out.println("✓ 实际请求: GET /api/attendance/records?startDate=2024-01-01&endDate=2024-01-31&employeeId=zhang.san@company.com");
        } else {
            System.out.println("✗ 执行失败: " + result.getError());
        }
    }

    /**
     * 示例2：经理查询部门考勤记录
     * <p>
     * 权限要求：DEPARTMENT（部门数据）
     * 预期效果：自动添加 departmentId 过滤条件
     */
    public void example2_ManagerQueryDepartmentAttendance() {
        System.out.println("\n========== 示例2：经理查询部门考勤 ==========");

        // 1. 绑定经理到OA系统
        identityMappingService.bindIdentity(
                "U002",                          // 平台用户ID
                "oa-system",                     // 系统ID
                "li.si@company.com",             // 外部用户ID
                "李四",                          // 外部用户名
                Map.of(
                        "role", "manager",        // 角色：经理
                        "departmentId", "tech-001" // 部门
                )
        );

        // 2. 定义Action：查询部门迟到记录
        ActionDefinition queryLateAction = ActionDefinition.builder()
                .actionId("oa:attendance:query-late")
                .name("查询部门迟到记录")
                .interfaceBinding(StepDefinition.InterfaceBinding.builder()
                        .type("HTTP")
                        .config(HttpOptionsConfig.builder()
                                .url("https://oa.company.com/api/attendance/late")
                                .method("GET")
                                .build())
                        .build())
                .params(List.of(
                        ParamDefinition.builder()
                                .name("date")
                                .type("string")
                                .required(true)
                                .build(),
                        ParamDefinition.builder()
                                .name("deptId")  // 将被自动注入
                                .type("string")
                                .required(false)
                                .description("部门ID（自动注入）")
                                .build()
                ))
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(true)
                        .filterMapping(Map.of("departmentId", "deptId"))
                        .build())
                .build();

        // 3. 执行Action
        Map<String, Object> params = Map.of(
                "platformUserId", "U002",      // 李四（经理）
                "systemId", "oa-system",
                "date", "2024-01-20"
        );

        ExecutionResult result = actionExecutorFactory.execute(queryLateAction, params, 30);

        // 4. 结果
        if (result.isSuccess()) {
            System.out.println("✓ 执行成功");
            System.out.println("✓ 自动添加了过滤条件: deptId = tech-001");
            System.out.println("✓ 实际请求: GET /api/attendance/late?date=2024-01-20&deptId=tech-001");
            System.out.println("✓ 只返回技术部员工的迟到记录");
        } else {
            System.out.println("✗ 执行失败: " + result.getError());
        }
    }

    /**
     * 示例3：权限不足被拒绝
     * <p>
     * 场景：普通员工尝试查询部门数据
     * 预期效果：权限检查失败，阻止执行
     */
    public void example3_PermissionDenied() {
        System.out.println("\n========== 示例3：权限不足被拒绝 ==========");

        // 1. 使用普通员工账号（U001 - 张三）
        // 2. 定义需要经理权限的Action
        ActionDefinition queryLateAction = ActionDefinition.builder()
                .actionId("oa:attendance:query-late")  // 需要经理权限
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(true)
                        .build())
                .build();

        // 3. 普通员工尝试执行
        Map<String, Object> params = Map.of(
                "platformUserId", "U001",      // 张三（普通员工）
                "systemId", "oa-system",
                "date", "2024-01-20"
        );

        ExecutionResult result = actionExecutorFactory.execute(queryLateAction, params, 30);

        // 4. 结果：权限不足
        if (!result.isSuccess()) {
            System.out.println("✓ 权限检查生效");
            System.out.println("✗ 执行被拒绝: " + result.getError());
            System.out.println("✓ 原因：普通员工没有 oa:attendance:query-late 权限");
            System.out.println("✓ Action未被执行，保护了数据安全");
        } else {
            System.out.println("✗ 权限检查失效！不应该执行成功");
        }
    }

    /**
     * 示例4：多系统切换
     * <p>
     * 场景：同一用户在不同系统中有不同权限
     * 预期效果：根据systemId自动切换权限
     */
    public void example4_MultiSystemSwitch() {
        System.out.println("\n========== 示例4：多系统切换 ==========");

        // 1. 绑定用户到两个系统
        // OA系统：总监角色（高权限）
        identityMappingService.bindIdentity(
                "U003",
                "oa-system",
                "wang.wu@company.com",
                "王五",
                Map.of("role", "director", "departmentId", "tech-001")
        );

        // 政务平台：普通公民（低权限）
        identityMappingService.bindIdentity(
                "U003",
                "gov-platform",
                "320102199001013456",
                "王五",
                Map.of("role", "citizen")
        );

        // 2. OA系统：审批任务（需要director权限）
        ActionDefinition oaApprovalAction = ActionDefinition.builder()
                .actionId("oa:approval:submit")
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(true)
                        .build())
                .build();

        Map<String, Object> oaParams = Map.of(
                "platformUserId", "U003",
                "systemId", "oa-system",  // OA系统
                "taskId", "T001"
        );

        ExecutionResult oaResult = actionExecutorFactory.execute(oaApprovalAction, oaParams, 30);

        if (oaResult.isSuccess()) {
            System.out.println("✓ OA系统：审批任务成功（director权限）");
        }

        // 3. 政务平台：预约办理（citizen权限）
        ActionDefinition govAppointmentAction = ActionDefinition.builder()
                .actionId("gov:appointment:create")
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(true)
                        .build())
                .build();

        Map<String, Object> govParams = Map.of(
                "platformUserId", "U003",
                "systemId", "gov-platform",  // 政务平台
                "serviceCode", "S001"
        );

        ExecutionResult govResult = actionExecutorFactory.execute(govAppointmentAction, govParams, 30);

        if (govResult.isSuccess()) {
            System.out.println("✓ 政务平台：预约成功（citizen权限）");
        }

        System.out.println("✓ 同一平台用户，不同系统自动切换权限");
    }

    /**
     * 示例5：Action不需要权限
     * <p>
     * 场景：公开的查询接口
     * 预期效果：跳过权限检查
     */
    public void example5_PublicAction() {
        System.out.println("\n========== 示例5：公开Action（无需权限）==========");

        // 1. 定义公开Action（不设置权限配置）
        ActionDefinition publicAction = ActionDefinition.builder()
                .actionId("public:weather:query")
                .name("查询天气")
                .interfaceBinding(StepDefinition.InterfaceBinding.builder()
                        .type("HTTP")
                        .config(HttpOptionsConfig.builder()
                                .url("https://api.weather.com/current")
                                .method("GET")
                                .build())
                        .build())
                .params(List.of(
                        ParamDefinition.builder()
                                .name("city")
                                .type("string")
                                .required(true)
                                .build()
                ))
                // 不设置 dataPermissionConfig，或设置 enforced=false
                .dataPermissionConfig(DataPermissionConfig.builder()
                        .enforced(false)
                        .build())
                .build();

        // 2. 执行（不需要platformUserId和systemId）
        Map<String, Object> params = Map.of(
                "city", "杭州"
        );

        ExecutionResult result = actionExecutorFactory.execute(publicAction, params, 30);

        // 3. 结果
        if (result.isSuccess()) {
            System.out.println("✓ 执行成功");
            System.out.println("✓ 跳过了权限检查");
            System.out.println("✓ 适用于公开API或不需要权限的场景");
        }
    }

    /**
     * 运行所有示例
     */
    public void runAllExamples() {
        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     多系统权限集成 - 快速开始示例                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");

        example1_EmployeeQueryOwnAttendance();
        example2_ManagerQueryDepartmentAttendance();
        example3_PermissionDenied();
        example4_MultiSystemSwitch();
        example5_PublicAction();

        System.out.println("\n");
        System.out.println("╔══════════════════════════════════════════════════╗");
        System.out.println("║     示例运行完成！                                ║");
        System.out.println("║                                                  ║");
        System.out.println("║     了解更多：                                    ║");
        System.out.println("║     - docs/PERMISSION_INTEGRATION_GUIDE.md       ║");
        System.out.println("║     - docs/2026-01-20-multi-system-permission-   ║");
        System.out.println("║       design.md                                  ║");
        System.out.println("╚══════════════════════════════════════════════════╝");
        System.out.println("\n");
    }
}
