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
package com.alibaba.assistant.agent.planning.permission;

import com.alibaba.assistant.agent.planning.model.ActionDefinition;
import com.alibaba.assistant.agent.planning.model.DataPermissionConfig;
import com.alibaba.assistant.agent.planning.permission.adapter.GovPermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.adapter.OaPermissionAdapter;
import com.alibaba.assistant.agent.planning.permission.adapter.PermissionAdapterRegistry;
import com.alibaba.assistant.agent.planning.permission.model.*;
import com.alibaba.assistant.agent.planning.permission.service.DefaultPermissionService;
import com.alibaba.assistant.agent.planning.permission.service.InMemoryIdentityMappingService;
import com.alibaba.assistant.agent.planning.permission.service.UnifiedChatService;
import com.alibaba.assistant.agent.planning.permission.spi.PermissionCheckResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Multi-System Permission Integration Tests.
 * <p>
 * Tests the complete permission flow for OA and Government platform scenarios.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class MultiSystemPermissionIntegrationTest {

    private PermissionAdapterRegistry adapterRegistry;
    private InMemoryIdentityMappingService identityMappingService;
    private DefaultPermissionService permissionService;
    private UnifiedChatService unifiedChatService;

    @BeforeEach
    void setUp() {
        // Create adapters
        OaPermissionAdapter oaAdapter = new OaPermissionAdapter();
        GovPermissionAdapter govAdapter = new GovPermissionAdapter();

        // Create registry
        adapterRegistry = new PermissionAdapterRegistry(Arrays.asList(oaAdapter, govAdapter));

        // Create services
        identityMappingService = new InMemoryIdentityMappingService();
        permissionService = new DefaultPermissionService(adapterRegistry, identityMappingService);
        unifiedChatService = new UnifiedChatService(identityMappingService, permissionService);
    }

    @Nested
    @DisplayName("OA System Permission Tests")
    class OaSystemTests {

        @Test
        @DisplayName("Employee can only apply leave and update own tasks")
        void testEmployeePermission() {
            // Given: 张三是OA系统员工
            String platformUserId = "U001";
            String systemId = "oa-system";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证员工权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "oa:attendance:apply-leave",
                    "oa:task:update-status"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.SELF);
            assertThat(permission.getFilter("userId")).isNotNull();
        }

        @Test
        @DisplayName("Manager can query department attendance and tasks")
        void testManagerPermission() {
            // Given: 李四是OA系统经理
            String platformUserId = "U002";
            String systemId = "oa-system";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证经理权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "oa:attendance:apply-leave",
                    "oa:attendance:query-late",
                    "oa:task:update-status",
                    "oa:task:query-progress"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.DEPARTMENT);
            assertThat(permission.getFilter("departmentId")).isNotNull();
            assertThat(permission.getFilter("departmentId").getValue()).isEqualTo("tech-001");
        }

        @Test
        @DisplayName("Director can query all company data")
        void testDirectorPermission() {
            // Given: 王五是OA系统大领导
            String platformUserId = "U003";
            String systemId = "oa-system";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证大领导权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "oa:attendance:query-late",
                    "oa:attendance:query-statistics",
                    "oa:task:query-progress",
                    "oa:task:query-statistics"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.ORGANIZATION);
            // 大领导没有过滤条件，可以看全公司数据
            assertThat(permission.getFilters()).isEmpty();
        }

        @Test
        @DisplayName("Employee cannot query late attendance")
        void testEmployeeCannotQueryLate() {
            // Given: 张三是OA系统员工
            String platformUserId = "U001";
            String systemId = "oa-system";

            // When: 检查查询迟到权限
            PermissionCheckResult result = unifiedChatService.checkPermission(
                    platformUserId, systemId, "oa:attendance:query-late", null);

            // Then: 应该被拒绝
            assertThat(result.isDenied()).isTrue();
            assertThat(result.getErrorCode()).isEqualTo("NO_PERMISSION");
        }
    }

    @Nested
    @DisplayName("Government Platform Permission Tests")
    class GovPlatformTests {

        @Test
        @DisplayName("Citizen can query process and create appointments")
        void testCitizenPermission() {
            // Given: 张三是政务平台群众
            String platformUserId = "U001";
            String systemId = "gov-platform";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证群众权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "gov:service:query-process",
                    "gov:appointment:create"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.SELF);
        }

        @Test
        @DisplayName("Staff can manage appointments and publish content")
        void testStaffPermission() {
            // Given: 赵六是政务平台业务人员
            String platformUserId = "U004";
            String systemId = "gov-platform";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证业务人员权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "gov:appointment:set-limit",
                    "gov:content:publish-article"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.DEPARTMENT);
            assertThat(permission.getFilter("bureauId")).isNotNull();
        }

        @Test
        @DisplayName("Leader can query statistics")
        void testLeaderPermission() {
            // Given: 王五是政务平台分管领导
            String platformUserId = "U003";
            String systemId = "gov-platform";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 验证分管领导权限
            assertThat(permission.getAllowedActions()).containsExactlyInAnyOrder(
                    "gov:stats:query-appointment",
                    "gov:stats:query-satisfaction"
            );
            assertThat(permission.getDataScope()).isEqualTo(DataScopeType.DEPARTMENT_TREE);

            // 验证满意度查询标记为不可用
            assertThat(permission.getContext("satisfactionAvailable")).isEqualTo(false);
        }
    }

    @Nested
    @DisplayName("Cross-System Tests")
    class CrossSystemTests {

        @Test
        @DisplayName("User can access multiple systems with different identities")
        void testMultipleSystemAccess() {
            // Given: 张三同时绑定了OA和政务平台
            String platformUserId = "U001";

            // When: 获取可访问系统列表
            List<AccessibleSystem> systems = identityMappingService.getAccessibleSystems(platformUserId);

            // Then: 应该有两个已绑定的系统
            assertThat(systems).hasSize(2);
            assertThat(systems).allMatch(AccessibleSystem::isBound);

            // 验证OA系统身份
            AccessibleSystem oaSystem = systems.stream()
                    .filter(s -> "oa-system".equals(s.getSystemId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(oaSystem.getExternalUserId()).isEqualTo("zhang.san@company.com");

            // 验证政务平台身份
            AccessibleSystem govSystem = systems.stream()
                    .filter(s -> "gov-platform".equals(s.getSystemId()))
                    .findFirst()
                    .orElseThrow();
            assertThat(govSystem.getExternalUserId()).isEqualTo("320102199001011234");
        }

        @Test
        @DisplayName("User has different permissions in different systems")
        void testDifferentPermissionsInSystems() {
            // Given: 王五在OA是大领导，在政务平台是分管领导
            String platformUserId = "U003";

            // When: 获取两个系统的权限
            StandardPermission oaPermission = permissionService.getPermission(platformUserId, "oa-system", null);
            StandardPermission govPermission = permissionService.getPermission(platformUserId, "gov-platform", null);

            // Then: OA系统权限
            assertThat(oaPermission.getDataScope()).isEqualTo(DataScopeType.ORGANIZATION);
            assertThat(oaPermission.getAllowedActions()).contains("oa:attendance:query-statistics");

            // Then: 政务平台权限
            assertThat(govPermission.getDataScope()).isEqualTo(DataScopeType.DEPARTMENT_TREE);
            assertThat(govPermission.getAllowedActions()).contains("gov:stats:query-appointment");
        }
    }

    @Nested
    @DisplayName("Data Permission Injection Tests")
    class DataPermissionInjectionTests {

        @Test
        @DisplayName("Department filter is injected for manager")
        void testDepartmentFilterInjection() {
            // Given: 李四是经理，要查询迟到情况
            String platformUserId = "U002";
            String systemId = "oa-system";

            // 创建Action定义
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("oa:attendance:query-late")
                    .actionName("查询迟到早退")
                    .dataPermissionConfig(DataPermissionConfig.builder()
                            .enabled(true)
                            .filterMapping(Map.of("departmentId", "deptId"))
                            .enforced(true)
                            .build())
                    .build();

            // When: 获取注入权限后的参数
            Map<String, Object> userParams = Map.of("date", "2024-01-20");
            Map<String, Object> finalParams = unifiedChatService.getParametersWithPermission(
                    platformUserId, systemId, action, userParams, null);

            // Then: 参数应该包含注入的部门ID
            assertThat(finalParams).containsEntry("date", "2024-01-20");
            assertThat(finalParams).containsEntry("deptId", "tech-001");
        }

        @Test
        @DisplayName("No filter injected for organization-wide access")
        void testNoFilterForOrganizationAccess() {
            // Given: 王五是大领导，可以看全公司
            String platformUserId = "U003";
            String systemId = "oa-system";

            // 创建Action定义
            ActionDefinition action = ActionDefinition.builder()
                    .actionId("oa:attendance:query-statistics")
                    .actionName("查询考勤统计")
                    .dataPermissionConfig(DataPermissionConfig.builder()
                            .enabled(true)
                            .enforced(true)
                            .build())
                    .build();

            // When: 获取注入权限后的参数
            Map<String, Object> userParams = Map.of("quarter", "Q4");
            Map<String, Object> finalParams = unifiedChatService.getParametersWithPermission(
                    platformUserId, systemId, action, userParams, null);

            // Then: 只有用户参数，没有额外的过滤条件
            assertThat(finalParams).containsOnlyKeys("quarter");
        }
    }

    @Nested
    @DisplayName("Identity Binding Tests")
    class IdentityBindingTests {

        @Test
        @DisplayName("Unbound user gets empty permission")
        void testUnboundUserPermission() {
            // Given: 创建一个新用户，没有绑定任何系统
            String platformUserId = "U999";
            String systemId = "oa-system";

            // When: 获取权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);

            // Then: 应该返回空权限
            assertThat(permission.getAllowedActions()).isEmpty();
        }

        @Test
        @DisplayName("Can bind new identity")
        void testBindNewIdentity() {
            // Given: 新用户
            String platformUserId = "U999";
            String systemId = "oa-system";

            // When: 绑定身份
            identityMappingService.bindIdentity(
                    platformUserId,
                    systemId,
                    "new.user@company.com",
                    "新用户",
                    Map.of("role", "employee", "deptId", "hr-001", "deptName", "人事部")
            );

            // Then: 应该能获取到权限
            StandardPermission permission = permissionService.getPermission(platformUserId, systemId, null);
            assertThat(permission.getAllowedActions()).isNotEmpty();
            assertThat(permission.getUserId()).isEqualTo("new.user@company.com");
        }

        @Test
        @DisplayName("Can unbind identity")
        void testUnbindIdentity() {
            // Given: 有绑定的用户
            String platformUserId = "U001";
            String systemId = "oa-system";
            assertThat(identityMappingService.isBound(platformUserId, systemId)).isTrue();

            // When: 解绑
            identityMappingService.unbindIdentity(platformUserId, systemId);

            // Then: 应该不再绑定
            assertThat(identityMappingService.isBound(platformUserId, systemId)).isFalse();

            // 重新绑定（恢复测试数据）
            identityMappingService.bindIdentity(
                    platformUserId, systemId, "zhang.san@company.com", "张三",
                    Map.of("role", "employee", "deptId", "tech-001", "deptName", "技术部")
            );
        }
    }

    @Nested
    @DisplayName("Unified Chat Service Tests")
    class UnifiedChatServiceTests {

        @Test
        @DisplayName("Chat with valid user and system")
        void testChatWithValidUserAndSystem() {
            // Given: 有效的请求
            UnifiedChatService.ChatRequest request = new UnifiedChatService.ChatRequest();
            request.setPlatformUserId("U001");
            request.setTargetSystem("oa-system");
            request.setMessage("帮我请一天假");

            // When: 处理聊天
            UnifiedChatService.ChatResponse response = unifiedChatService.processChat(request);

            // Then: 应该成功
            assertThat(response.isSuccess()).isTrue();
            assertThat(response.getIdentity()).isNotNull();
            assertThat(response.getIdentity().getExternalUserId()).isEqualTo("zhang.san@company.com");
            assertThat(response.getPermission()).isNotNull();
            assertThat(response.getPermission().getAllowedActions()).contains("oa:attendance:apply-leave");
        }

        @Test
        @DisplayName("Chat with unbound system returns error")
        void testChatWithUnboundSystem() {
            // Given: 孙七只绑定了OA，没绑定政务平台
            UnifiedChatService.ChatRequest request = new UnifiedChatService.ChatRequest();
            request.setPlatformUserId("U005");
            request.setTargetSystem("gov-platform");
            request.setMessage("查询办理流程");

            // When: 处理聊天
            UnifiedChatService.ChatResponse response = unifiedChatService.processChat(request);

            // Then: 应该返回未绑定错误
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("NOT_BOUND");
        }

        @Test
        @DisplayName("Chat without target system returns error")
        void testChatWithoutTargetSystem() {
            // Given: 没有指定目标系统
            UnifiedChatService.ChatRequest request = new UnifiedChatService.ChatRequest();
            request.setPlatformUserId("U001");
            request.setMessage("帮我请假");

            // When: 处理聊天
            UnifiedChatService.ChatResponse response = unifiedChatService.processChat(request);

            // Then: 应该返回系统未指定错误
            assertThat(response.isSuccess()).isFalse();
            assertThat(response.getErrorCode()).isEqualTo("SYSTEM_NOT_SPECIFIED");
        }
    }
}
