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
package com.alibaba.assistant.agent.data.spi;

import com.alibaba.assistant.agent.data.model.OptionItem;
import com.alibaba.assistant.agent.data.model.OptionsSourceConfig;

import java.util.List;
import java.util.Map;

/**
 * SPI for fetching parameter options from various sources.
 * <p>
 * Provides a unified interface for retrieving dynamic option lists for
 * action parameters. Supports multiple source types including SQL queries,
 * HTTP APIs, and NL2SQL conversions.
 * <p>
 * This interface is used during the parameter collection flow to populate
 * dropdown lists, suggestion lists, and other selection UI components.
 * <p>
 * The provider handles:
 * <ul>
 *     <li>SQL source: Execute SQL queries to fetch options</li>
 *     <li>API source: Call HTTP endpoints to retrieve options</li>
 *     <li>NL2SQL source: Convert natural language to SQL and execute</li>
 *     <li>Context parameter substitution for dependent parameters</li>
 * </ul>
 * <p>
 * Example implementation:
 * <pre>
 * {@code
 * @Component
 * public class DefaultParameterOptionsProvider implements ParameterOptionsProvider {
 *     @Override
 *     public List<OptionItem> fetchOptions(String systemId,
 *                                          OptionsSourceConfig config,
 *                                          Map<String, Object> context) {
 *         switch (config.getSourceType()) {
 *             case SQL:
 *                 return fetchFromSql(config.getSql(), context);
 *             case API:
 *                 return fetchFromApi(config.getApi(), context);
 *             case NL2SQL:
 *                 return fetchFromNl2Sql(config.getNl2sql(), context);
 *             default:
 *                 return Collections.emptyList();
 *         }
 *     }
 * }
 * }
 * </pre>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public interface ParameterOptionsProvider {

    /**
     * Fetches options based on configuration.
     * <p>
     * Retrieves a list of selectable options for a parameter based on the
     * specified configuration. The configuration determines the source type
     * (SQL, API, or NL2SQL) and provides the necessary details to fetch
     * the options.
     * <p>
     * The context map contains previously collected parameters that may be
     * needed to fetch dependent options. For example, fetching cities may
     * require a previously selected country.
     * <p>
     * Example usage:
     * <pre>
     * // Fetch department options using SQL
     * OptionsSourceConfig config = new OptionsSourceConfig();
     * config.setSourceType(SourceType.SQL);
     * SqlSourceConfig sqlConfig = new SqlSourceConfig();
     * sqlConfig.setSql("SELECT name, id FROM departments WHERE status = 'ACTIVE'");
     * sqlConfig.setLabelColumn("name");
     * sqlConfig.setValueColumn("id");
     * config.setSql(sqlConfig);
     *
     * Map<String, Object> context = new HashMap<>();
     * List<OptionItem> options = provider.fetchOptions("system-123", config, context);
     *
     * // Example with dependent parameter
     * Map<String, Object> contextWithDept = new HashMap<>();
     * contextWithDept.put("departmentId", 5);
     * SqlSourceConfig employeeConfig = new SqlSourceConfig();
     * employeeConfig.setSql("SELECT name, id FROM employees WHERE dept_id = :deptId");
     * employeeConfig.setLabelColumn("name");
     * employeeConfig.setValueColumn("id");
     * Map<String, String> paramMapping = new HashMap<>();
     * paramMapping.put("deptId", "departmentId");
     * employeeConfig.setParamMapping(paramMapping);
     * // Fetch employees for the selected department
     * </pre>
     *
     * @param systemId the system identifier (agent ID) for datasource resolution
     * @param config the options source configuration defining how to fetch options
     * @param context the context map containing already collected parameters,
     *                used for parameter substitution in SQL queries or API calls
     * @return a list of option items, never null but may be empty
     * @throws IllegalArgumentException if configuration is invalid or missing required fields
     */
    List<OptionItem> fetchOptions(String systemId,
                                   OptionsSourceConfig config,
                                   Map<String, Object> context);
}
