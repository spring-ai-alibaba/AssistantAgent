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
package com.alibaba.assistant.agent.data.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Represents the result of a SQL query execution.
 * This class encapsulates the columns, rows, metadata, and execution statistics
 * for a database query, providing utility methods for formatting and display.
 *
 * <p>The QueryResult supports multiple output formats including string arrays
 * and markdown tables, making it suitable for both programmatic processing and
 * human-readable presentation.</p>
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class QueryResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * List of column metadata describing the structure of the result set.
     */
    private List<ColumnInfo> columns = new ArrayList<>();

    /**
     * List of result rows, where each row is a map of column names to values.
     */
    private List<Map<String, Object>> rows = new ArrayList<>();

    /**
     * Total number of rows in the complete result set (may exceed rows.size() if truncated).
     */
    private int totalRows;

    /**
     * Time taken to execute the query in milliseconds.
     */
    private long executionTimeMs;

    /**
     * The SQL statement that was executed to produce this result.
     */
    private String sql;

    /**
     * Indicates whether the result set has been truncated (not all rows returned).
     */
    private boolean truncated;

    /**
     * Default constructor.
     */
    public QueryResult() {
    }

    /**
     * Gets the list of column metadata.
     *
     * @return list of column information
     */
    public List<ColumnInfo> getColumns() {
        return columns;
    }

    /**
     * Sets the list of column metadata.
     *
     * @param columns list of column information
     */
    public void setColumns(List<ColumnInfo> columns) {
        this.columns = columns;
    }

    /**
     * Gets the list of result rows.
     *
     * @return list of rows as maps of column names to values
     */
    public List<Map<String, Object>> getRows() {
        return rows;
    }

    /**
     * Sets the list of result rows.
     *
     * @param rows list of rows as maps of column names to values
     */
    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows;
    }

    /**
     * Gets the total number of rows in the complete result set.
     *
     * @return total row count
     */
    public int getTotalRows() {
        return totalRows;
    }

    /**
     * Sets the total number of rows in the complete result set.
     *
     * @param totalRows total row count
     */
    public void setTotalRows(int totalRows) {
        this.totalRows = totalRows;
    }

    /**
     * Gets the query execution time in milliseconds.
     *
     * @return execution time in milliseconds
     */
    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    /**
     * Sets the query execution time in milliseconds.
     *
     * @param executionTimeMs execution time in milliseconds
     */
    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    /**
     * Gets the SQL statement that was executed.
     *
     * @return SQL statement
     */
    public String getSql() {
        return sql;
    }

    /**
     * Sets the SQL statement that was executed.
     *
     * @param sql SQL statement
     */
    public void setSql(String sql) {
        this.sql = sql;
    }

    /**
     * Checks if the result set has been truncated.
     *
     * @return true if truncated, false otherwise
     */
    public boolean isTruncated() {
        return truncated;
    }

    /**
     * Sets whether the result set has been truncated.
     *
     * @param truncated true if truncated, false otherwise
     */
    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    /**
     * Adds a single row to the result set.
     *
     * @param row map of column names to values
     * @throws NullPointerException if row is null
     */
    public void addRow(Map<String, Object> row) {
        Objects.requireNonNull(row, "row cannot be null");
        this.rows.add(row);
    }

    /**
     * Converts the result set to a simple 2D string array format for display.
     * Each row is represented as an array of string values, with NULL values
     * represented as the string "NULL".
     *
     * @return 2D string array where result[row][col] represents the value,
     *         or empty array if no rows exist
     */
    public String[][] toStringArray() {
        if (rows.isEmpty() || columns.isEmpty()) {
            return new String[0][];
        }
        String[][] result = new String[rows.size()][columns.size()];
        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> row = rows.get(i);
            for (int j = 0; j < columns.size(); j++) {
                Object value = row.get(columns.get(j).getName());
                result[i][j] = value == null ? "NULL" : value.toString();
            }
        }
        return result;
    }

    /**
     * Formats the result set as a markdown table with headers and rows.
     * Includes a truncation notice if the result set was truncated.
     *
     * @return markdown-formatted table string, or "No results." if no columns exist
     */
    public String toMarkdownTable() {
        if (columns.isEmpty()) {
            return "No results.";
        }
        StringBuilder sb = new StringBuilder();

        // Header
        sb.append("| ");
        for (ColumnInfo col : columns) {
            String colName = col.getName();
            sb.append(colName != null ? colName : "UNNAMED").append(" | ");
        }
        sb.append("\n|");
        for (int i = 0; i < columns.size(); i++) {
            sb.append("---|");
        }
        sb.append("\n");

        // Rows
        for (Map<String, Object> row : rows) {
            if (row == null) {
                continue; // Skip null rows
            }
            sb.append("| ");
            for (ColumnInfo col : columns) {
                Object value = row.get(col.getName());
                sb.append(value == null ? "NULL" : value.toString()).append(" | ");
            }
            sb.append("\n");
        }

        if (truncated) {
            sb.append("\n*Results truncated. Total rows: ").append(totalRows).append("*");
        }

        return sb.toString();
    }

    /**
     * Returns a string representation of this QueryResult for debugging purposes.
     *
     * @return string representation including all fields
     */
    @Override
    public String toString() {
        return "QueryResult{" +
                "columns=" + columns +
                ", rows=" + rows +
                ", totalRows=" + totalRows +
                ", executionTimeMs=" + executionTimeMs +
                ", sql='" + sql + '\'' +
                ", truncated=" + truncated +
                '}';
    }

    /**
     * Compares this QueryResult with another object for equality.
     * Two QueryResult objects are equal if they have the same columns, rows,
     * totalRows, sql, and truncated values. Execution time is excluded from
     * equality comparison as it may vary between runs.
     *
     * @param o object to compare with
     * @return true if equal, false otherwise
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        QueryResult that = (QueryResult) o;
        return totalRows == that.totalRows &&
                truncated == that.truncated &&
                Objects.equals(columns, that.columns) &&
                Objects.equals(rows, that.rows) &&
                Objects.equals(sql, that.sql);
    }

    /**
     * Generates a hash code for this QueryResult.
     * Execution time is excluded from the hash code calculation for consistency
     * with the equals() method.
     *
     * @return hash code value
     */
    @Override
    public int hashCode() {
        return Objects.hash(columns, rows, totalRows, sql, truncated);
    }

}
