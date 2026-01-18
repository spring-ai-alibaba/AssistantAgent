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
package com.alibaba.assistant.agent.data.executor;

import com.alibaba.assistant.agent.data.model.QueryResult;
import com.alibaba.assistant.agent.data.model.ColumnInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.*;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Executes SQL queries and converts results to QueryResult objects.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlExecutor {

    private static final Logger logger = LoggerFactory.getLogger(SqlExecutor.class);

    private static final int DEFAULT_MAX_ROWS = 1000;
    private static final int DEFAULT_TIMEOUT_SECONDS = 30;

    /**
     * Execute SQL query and return structured results.
     *
     * @param connection database connection
     * @param sql SQL statement
     * @param maxRows maximum number of rows to return
     * @return QueryResult containing columns and rows
     * @throws SQLException if query execution fails
     */
    public static QueryResult executeQuery(Connection connection, String sql, int maxRows) throws SQLException {
        long startTime = System.currentTimeMillis();

        try (Statement statement = connection.createStatement()) {
            statement.setMaxRows(maxRows);
            statement.setQueryTimeout(DEFAULT_TIMEOUT_SECONDS);

            try (ResultSet rs = statement.executeQuery(sql)) {
                QueryResult result = buildQueryResult(rs, maxRows);
                result.setSql(sql);
                result.setExecutionTimeMs(System.currentTimeMillis() - startTime);

                logger.info("SqlExecutor#executeQuery - executed SQL, rows={}, time={}ms",
                    result.getRows().size(), result.getExecutionTimeMs());

                return result;
            }
        }
    }

    /**
     * Execute SQL query with default max rows.
     */
    public static QueryResult executeQuery(Connection connection, String sql) throws SQLException {
        return executeQuery(connection, sql, DEFAULT_MAX_ROWS);
    }

    private static QueryResult buildQueryResult(ResultSet rs, int maxRows) throws SQLException {
        QueryResult result = new QueryResult();

        // Build column info
        ResultSetMetaData metaData = rs.getMetaData();
        int columnCount = metaData.getColumnCount();
        List<ColumnInfo> columns = new ArrayList<>();

        for (int i = 1; i <= columnCount; i++) {
            ColumnInfo column = new ColumnInfo();
            column.setName(metaData.getColumnName(i));
            column.setType(metaData.getColumnTypeName(i));
            columns.add(column);
        }
        result.setColumns(columns);

        // Build rows
        int rowCount = 0;
        while (rs.next() && rowCount < maxRows) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 1; i <= columnCount; i++) {
                row.put(metaData.getColumnName(i), rs.getObject(i));
            }
            result.addRow(row);
            rowCount++;
        }

        // Check if truncated
        if (rs.next()) {
            result.setTruncated(true);
            result.setTotalRows(maxRows + 1); // At least maxRows + 1
        } else {
            result.setTotalRows(rowCount);
        }

        return result;
    }
}
