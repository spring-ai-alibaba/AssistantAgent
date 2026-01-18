/*
 * Copyright 2025 the original author or authors.
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
package com.alibaba.assistant.agent.data.security;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Validates SQL statements for read-only operations.
 * Ensures only SELECT statements are allowed, blocking DML/DDL operations.
 *
 * @author Assistant Agent Team
 * @since 1.0.0
 */
public class SqlSecurityValidator {

    private static final List<String> FORBIDDEN_KEYWORDS = Arrays.asList(
        "INSERT", "UPDATE", "DELETE", "DROP", "TRUNCATE",
        "ALTER", "CREATE", "GRANT", "REVOKE"
    );

    private static final Pattern COMMENT_PATTERN = Pattern.compile("--.*|/\\*.*?\\*/", Pattern.DOTALL);

    /**
     * Validates that the SQL statement is read-only (SELECT only).
     *
     * @param sql SQL statement to validate
     * @throws SecurityException if SQL contains forbidden operations
     * @throws IllegalArgumentException if SQL is null or empty
     */
    public void validateReadOnly(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            throw new IllegalArgumentException("SQL statement cannot be null or empty");
        }

        // Remove comments
        String cleanSql = COMMENT_PATTERN.matcher(sql).replaceAll(" ");
        String normalized = cleanSql.trim().toUpperCase();

        // Must start with SELECT
        if (!normalized.startsWith("SELECT")) {
            throw new SecurityException("Only SELECT statements are allowed");
        }

        // Check for forbidden keywords
        for (String keyword : FORBIDDEN_KEYWORDS) {
            if (normalized.contains(keyword)) {
                throw new SecurityException("SQL contains forbidden keyword: " + keyword);
            }
        }
    }
}
