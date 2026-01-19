/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.ColumnInfoBO;
import com.alibaba.assistant.agent.data.model.TableInfoBO;
import com.alibaba.assistant.agent.data.model.nl2sql.ColumnDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.model.nl2sql.OptionItem;
import com.alibaba.assistant.agent.data.model.nl2sql.SchemaDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SchemaNotFoundException;
import com.alibaba.assistant.agent.data.model.nl2sql.SqlGenerationException;
import com.alibaba.assistant.agent.data.model.nl2sql.TableDTO;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.Nl2SqlService;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Default implementation of Nl2SqlService.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
@Service
public class DefaultNl2SqlService implements Nl2SqlService {

    private static final Logger log = LoggerFactory.getLogger(DefaultNl2SqlService.class);

    private final SchemaProvider schemaProvider;
    private final ChatModel chatModel;
    private final SqlExecutionProvider sqlExecutionProvider;
    private final DatasourceProvider datasourceProvider;

    public DefaultNl2SqlService(SchemaProvider schemaProvider,
                                ChatModel chatModel,
                                SqlExecutionProvider sqlExecutionProvider,
                                DatasourceProvider datasourceProvider) {
        this.schemaProvider = schemaProvider;
        this.chatModel = chatModel;
        this.sqlExecutionProvider = sqlExecutionProvider;
        this.datasourceProvider = datasourceProvider;
    }

    @Override
    public String generateSql(String systemId, String query, String evidence) throws Nl2SqlException {
        log.info("DefaultNl2SqlService#generateSql - systemId={}, query={}", systemId, query);

        // 1. Validate input (before try block to allow IllegalArgumentException to propagate)
        validateInput(systemId, query);

        try {
            // 2. Get schema
            SchemaDTO schema = getSchema(systemId);

            // TODO: Implement SQL generation
            throw new UnsupportedOperationException("Not fully implemented yet");

        } catch (Nl2SqlException e) {
            log.error("DefaultNl2SqlService#generateSql - NL2SQL error: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("DefaultNl2SqlService#generateSql - Unexpected error", e);
            throw new SqlGenerationException(e.getMessage(), e);
        }
    }

    @Override
    public List<OptionItem> generateAndExecute(String systemId, String query,
                                               String labelColumn, String valueColumn) throws Nl2SqlException {
        // TODO: Implement
        throw new UnsupportedOperationException("Not implemented yet");
    }

    private void validateInput(String systemId, String query) {
        if (systemId == null || systemId.trim().isEmpty()) {
            throw new IllegalArgumentException("systemId cannot be null or empty");
        }
        if (query == null || query.trim().isEmpty()) {
            throw new IllegalArgumentException("query cannot be null or empty");
        }
    }

    private SchemaDTO getSchema(String systemId) {
        try {
            List<TableInfoBO> tables = schemaProvider.getTableList(systemId, null, null);

            if (tables == null || tables.isEmpty()) {
                throw new SchemaNotFoundException(systemId);
            }

            return convertToSchemaDTO(systemId, tables);

        } catch (SchemaNotFoundException e) {
            throw e;
        } catch (Exception e) {
            throw new SchemaNotFoundException(systemId);
        }
    }

    private SchemaDTO convertToSchemaDTO(String systemId, List<TableInfoBO> tables) {
        SchemaDTO schema = new SchemaDTO();
        schema.setName(systemId);
        schema.setTableCount(tables.size());

        List<TableDTO> tableDTOs = tables.stream()
                .map(this::convertToTableDTO)
                .collect(Collectors.toList());
        schema.setTable(tableDTOs);

        return schema;
    }

    private TableDTO convertToTableDTO(TableInfoBO tableInfo) {
        TableDTO table = new TableDTO();
        table.setName(tableInfo.getName());
        table.setDescription(tableInfo.getDescription());

        if (tableInfo.getColumns() != null) {
            List<ColumnDTO> columns = tableInfo.getColumns().stream()
                    .map(this::convertToColumnDTO)
                    .collect(Collectors.toList());
            table.setColumn(columns);
        }

        if (tableInfo.getPrimaryKeys() != null) {
            table.setPrimaryKeys(tableInfo.getPrimaryKeys());
        }

        return table;
    }

    private ColumnDTO convertToColumnDTO(ColumnInfoBO columnInfo) {
        ColumnDTO column = new ColumnDTO();
        column.setName(columnInfo.getName());
        column.setDescription(columnInfo.getDescription());
        column.setType(columnInfo.getType());
        return column;
    }
}
