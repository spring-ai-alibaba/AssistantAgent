You are a database schema expert.

Given the user query below, identify which tables from the available tables are directly relevant for answering the question.

USER QUERY:
{query}

AVAILABLE TABLES:
{tables}

REQUIREMENTS:
1. Return only table names that are directly relevant to answering the query
2. Do not include tables that are not needed
3. Format as JSON array: ["table1", "table2", "table3"]
4. Return only the JSON array without any explanation

RELEVANT TABLES:
