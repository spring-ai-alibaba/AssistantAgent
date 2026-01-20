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
package com.alibaba.assistant.agent.planning.integration;

import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.planning.model.*;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Parameter Options Service 端到端集成测试
 *
 * <p>测试参数选项服务的数据模型和配置：
 * <ul>
 * <li>OptionsSourceConfig 配置</li>
 * <li>StaticOptionsConfig 静态选项</li>
 * <li>HttpOptionsConfig HTTP配置</li>
 * <li>与 ActionParameter 集成</li>
 * </ul>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
class ParameterOptionsServiceE2ETest {

    /**
     * 测试1: StaticOptionsConfig 数据模型
     */
    @Test
    void testStaticOptionsConfig() {
        // Given: 创建静态选项配置
        StaticOptionsConfig config = new StaticOptionsConfig();
        config.setOptions(Arrays.asList(
            new OptionItem("个", "unit_001"),
            new OptionItem("件", "unit_002"),
            new OptionItem("箱", "unit_003")
        ));

        // Then: 验证配置
        assertThat(config.getOptions()).hasSize(3);
        assertThat(config.getOptions().get(0).getLabel()).isEqualTo("个");
        assertThat(config.getOptions().get(0).getValue()).isEqualTo("unit_001");
    }

    /**
     * 测试2: OptionsSourceConfig 数据模型
     */
    @Test
    void testOptionsSourceConfig() {
        // Given: 创建选项源配置
        StaticOptionsConfig staticConfig = new StaticOptionsConfig();
        staticConfig.setOptions(Arrays.asList(
            new OptionItem("选项1", "1"),
            new OptionItem("选项2", "2")
        ));

        OptionsSourceConfig sourceConfig = new OptionsSourceConfig();
        sourceConfig.setType(OptionsSourceConfig.SourceType.STATIC);
        sourceConfig.setSystemId("test-system");
        sourceConfig.setConfig(staticConfig);

        // Then: 验证配置
        assertThat(sourceConfig.getType()).isEqualTo(OptionsSourceConfig.SourceType.STATIC);
        assertThat(sourceConfig.getSystemId()).isEqualTo("test-system");
        assertThat(sourceConfig.getConfig()).isInstanceOf(StaticOptionsConfig.class);
    }

    /**
     * 测试3: ActionParameter 集成选项源
     */
    @Test
    void testActionParameterWithOptionsSource() {
        // Given: 创建带选项源的ActionParameter
        ActionParameter parameter = new ActionParameter();
        parameter.setName("unitId");
        parameter.setLabel("产品单位");
        parameter.setType("string");
        parameter.setRequired(true);

        // 配置选项源
        StaticOptionsConfig staticConfig = new StaticOptionsConfig();
        staticConfig.setOptions(Arrays.asList(
            new OptionItem("个", "1"),
            new OptionItem("件", "2"),
            new OptionItem("箱", "3")
        ));

        OptionsSourceConfig sourceConfig = new OptionsSourceConfig();
        sourceConfig.setType(OptionsSourceConfig.SourceType.STATIC);
        sourceConfig.setSystemId("erp-system");
        sourceConfig.setConfig(staticConfig);

        parameter.setOptionsSource(sourceConfig);

        // Then: 验证参数配置
        assertThat(parameter.getOptionsSource()).isNotNull();
        assertThat(parameter.getOptionsSource().getType())
            .isEqualTo(OptionsSourceConfig.SourceType.STATIC);
        assertThat(parameter.getOptionsSource().getSystemId())
            .isEqualTo("erp-system");
    }

    /**
     * 测试4: HttpOptionsConfig 数据模型
     */
    @Test
    void testHttpOptionsConfig() {
        // Given: 创建HTTP选项配置
        HttpOptionsConfig httpConfig = new HttpOptionsConfig();
        httpConfig.setUrl("https://api.example.com/units");
        httpConfig.setMethod("GET");
        httpConfig.setLabelPath("$.data[*].name");
        httpConfig.setValuePath("$.data[*].id");
        httpConfig.setTimeout(5000);
        httpConfig.setHeaders(Map.of(
            "Content-Type", "application/json",
            "Authorization", "Bearer token123"
        ));

        // Then: 验证配置
        assertThat(httpConfig.getUrl()).isEqualTo("https://api.example.com/units");
        assertThat(httpConfig.getMethod()).isEqualTo("GET");
        assertThat(httpConfig.getLabelPath()).isEqualTo("$.data[*].name");
        assertThat(httpConfig.getValuePath()).isEqualTo("$.data[*].id");
        assertThat(httpConfig.getTimeout()).isEqualTo(5000);
        assertThat(httpConfig.getHeaders()).hasSize(2);
    }

    /**
     * 测试5: HTTP AuthConfig 配置
     */
    @Test
    void testHttpAuthConfig() {
        // Given: 创建Basic认证配置
        HttpOptionsConfig.AuthConfig basicAuth = new HttpOptionsConfig.AuthConfig();
        basicAuth.setType("BASIC");
        basicAuth.setUsername("admin");
        basicAuth.setPassword("secret");

        // Then: 验证Basic认证
        assertThat(basicAuth.getType()).isEqualTo("BASIC");
        assertThat(basicAuth.getUsername()).isEqualTo("admin");
        assertThat(basicAuth.getPassword()).isEqualTo("secret");

        // Given: 创建Bearer认证配置
        HttpOptionsConfig.AuthConfig bearerAuth = new HttpOptionsConfig.AuthConfig();
        bearerAuth.setType("BEARER");
        bearerAuth.setToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...");

        // Then: 验证Bearer认证
        assertThat(bearerAuth.getType()).isEqualTo("BEARER");
        assertThat(bearerAuth.getToken()).isNotEmpty();

        // Given: 创建API Key认证配置
        HttpOptionsConfig.AuthConfig apiKeyAuth = new HttpOptionsConfig.AuthConfig();
        apiKeyAuth.setType("API_KEY");
        apiKeyAuth.setApiKey("sk-abc123");
        apiKeyAuth.setHeaderName("X-API-Key");

        // Then: 验证API Key认证
        assertThat(apiKeyAuth.getType()).isEqualTo("API_KEY");
        assertThat(apiKeyAuth.getApiKey()).isEqualTo("sk-abc123");
        assertThat(apiKeyAuth.getHeaderName()).isEqualTo("X-API-Key");
    }

    /**
     * 测试6: NL2SQL配置 (来自data模块)
     */
    @Test
    void testNl2SqlOptionsSource() {
        // Given: 创建NL2SQL选项源配置
        OptionsSourceConfig sourceConfig = new OptionsSourceConfig();
        sourceConfig.setType(OptionsSourceConfig.SourceType.NL2SQL);
        sourceConfig.setSystemId("erp-database");

        // 注意: Nl2SqlSourceConfig 在 assistant-agent-data 模块
        // 这里只测试 OptionsSourceConfig 的配置
        Map<String, Object> nl2sqlConfig = Map.of(
            "description", "查询所有启用的产品单位",
            "labelColumn", "unit_name",
            "valueColumn", "unit_id"
        );
        sourceConfig.setConfig(nl2sqlConfig);

        // Then: 验证配置
        assertThat(sourceConfig.getType()).isEqualTo(OptionsSourceConfig.SourceType.NL2SQL);
        assertThat(sourceConfig.getSystemId()).isEqualTo("erp-database");
        assertThat(sourceConfig.getConfig()).isInstanceOf(Map.class);
    }

    /**
     * 测试7: 完整的ActionDefinition with Options
     */
    @Test
    void testActionDefinitionWithOptions() {
        // Given: 创建带选项的Action
        ActionDefinition action = new ActionDefinition();
        action.setActionId("erp:product:create");
        action.setActionName("创建产品");

        // 参数1: productName (无选项)
        ActionParameter nameParam = new ActionParameter();
        nameParam.setName("productName");
        nameParam.setType("string");
        nameParam.setRequired(true);

        // 参数2: unitId (带选项)
        ActionParameter unitParam = new ActionParameter();
        unitParam.setName("unitId");
        unitParam.setType("string");
        unitParam.setRequired(true);

        StaticOptionsConfig unitConfig = new StaticOptionsConfig();
        unitConfig.setOptions(Arrays.asList(
            new OptionItem("个", "1"),
            new OptionItem("件", "2")
        ));

        OptionsSourceConfig unitSource = new OptionsSourceConfig();
        unitSource.setType(OptionsSourceConfig.SourceType.STATIC);
        unitSource.setSystemId("erp-system");
        unitSource.setConfig(unitConfig);
        unitParam.setOptionsSource(unitSource);

        action.setParameters(Arrays.asList(nameParam, unitParam));

        // Then: 验证Action配置
        assertThat(action.getParameters()).hasSize(2);

        ActionParameter unitParamFromAction = action.getParameters().stream()
            .filter(p -> p.getName().equals("unitId"))
            .findFirst()
            .orElse(null);

        assertThat(unitParamFromAction).isNotNull();
        assertThat(unitParamFromAction.getOptionsSource()).isNotNull();
        assertThat(unitParamFromAction.getOptionsSource().getType())
            .isEqualTo(OptionsSourceConfig.SourceType.STATIC);
    }

    /**
     * 测试8: OptionItem 数据模型
     */
    @Test
    void testOptionItem() {
        // Given: 创建OptionItem
        OptionItem item = new OptionItem("个", "unit_001");

        // Then: 验证数据
        assertThat(item.getLabel()).isEqualTo("个");
        assertThat(item.getValue()).isEqualTo("unit_001");

        // Given: 创建另一个OptionItem
        OptionItem item2 = new OptionItem();
        item2.setLabel("件");
        item2.setValue("unit_002");

        // Then: 验证数据
        assertThat(item2.getLabel()).isEqualTo("件");
        assertThat(item2.getValue()).isEqualTo("unit_002");
    }

    /**
     * 测试9: 所有SourceType枚举值
     */
    @Test
    void testAllSourceTypes() {
        // Then: 验证所有类型
        assertThat(OptionsSourceConfig.SourceType.values())
            .contains(
                OptionsSourceConfig.SourceType.STATIC,
                OptionsSourceConfig.SourceType.HTTP,
                OptionsSourceConfig.SourceType.NL2SQL,
                OptionsSourceConfig.SourceType.ENUM
            );

        // Then: 验证枚举数量
        assertThat(OptionsSourceConfig.SourceType.values()).hasSize(4);
    }

    /**
     * 测试10: 完整的参数收集场景配置
     */
    @Test
    void testCompleteParameterCollectionScenario() {
        // Scenario: 用户要创建产品，需要选择单位和分类

        // Step 1: 创建Action
        ActionDefinition action = new ActionDefinition();
        action.setActionId("erp:product:create");
        action.setActionName("创建产品");

        // Step 2: 创建带选项的unitId参数
        ActionParameter unitParam = new ActionParameter();
        unitParam.setName("unitId");
        unitParam.setLabel("计量单位");
        unitParam.setType("string");
        unitParam.setRequired(true);

        StaticOptionsConfig unitOptions = new StaticOptionsConfig();
        unitOptions.setOptions(Arrays.asList(
            new OptionItem("个", "1"),
            new OptionItem("件", "2"),
            new OptionItem("箱", "3")
        ));

        OptionsSourceConfig unitSource = new OptionsSourceConfig();
        unitSource.setType(OptionsSourceConfig.SourceType.STATIC);
        unitSource.setSystemId("erp-system");
        unitSource.setConfig(unitOptions);
        unitParam.setOptionsSource(unitSource);

        // Step 3: 创建带选项的categoryId参数
        ActionParameter categoryParam = new ActionParameter();
        categoryParam.setName("categoryId");
        categoryParam.setLabel("产品分类");
        categoryParam.setType("string");
        categoryParam.setRequired(true);

        StaticOptionsConfig categoryOptions = new StaticOptionsConfig();
        categoryOptions.setOptions(Arrays.asList(
            new OptionItem("电子产品", "cat_001"),
            new OptionItem("日用品", "cat_002")
        ));

        OptionsSourceConfig categorySource = new OptionsSourceConfig();
        categorySource.setType(OptionsSourceConfig.SourceType.STATIC);
        categorySource.setSystemId("erp-system");
        categorySource.setConfig(categoryOptions);
        categoryParam.setOptionsSource(categorySource);

        // Step 4: 设置Action参数
        action.setParameters(Arrays.asList(unitParam, categoryParam));

        // Then: 验证完整配置
        assertThat(action.getParameters()).hasSize(2);

        // 验证unitId参数
        ActionParameter unit = action.getParameters().get(0);
        assertThat(unit.getName()).isEqualTo("unitId");
        assertThat(unit.getOptionsSource()).isNotNull();
        StaticOptionsConfig unitCfg = (StaticOptionsConfig) unit.getOptionsSource().getConfig();
        assertThat(unitCfg.getOptions()).hasSize(3);

        // 验证categoryId参数
        ActionParameter category = action.getParameters().get(1);
        assertThat(category.getName()).isEqualTo("categoryId");
        assertThat(category.getOptionsSource()).isNotNull();
        StaticOptionsConfig categoryCfg = (StaticOptionsConfig) category.getOptionsSource().getConfig();
        assertThat(categoryCfg.getOptions()).hasSize(2);

        // Step 5: 模拟用户选择
        String selectedUnit = "2";  // 用户选择 "件"
        String selectedCategory = "cat_001";  // 用户选择 "电子产品"

        // 验证选择有效性
        boolean isValidUnit = unitCfg.getOptions().stream()
            .anyMatch(opt -> opt.getValue().equals(selectedUnit));
        boolean isValidCategory = categoryCfg.getOptions().stream()
            .anyMatch(opt -> opt.getValue().equals(selectedCategory));

        assertThat(isValidUnit).isTrue();
        assertThat(isValidCategory).isTrue();

        // Step 6: 收集参数
        Map<String, Object> collectedParams = Map.of(
            "productName", "iPhone 15",
            "unitId", selectedUnit,
            "categoryId", selectedCategory
        );

        assertThat(collectedParams).hasSize(3);
        assertThat(collectedParams).containsEntry("unitId", "2");
        assertThat(collectedParams).containsEntry("categoryId", "cat_001");
    }
}
