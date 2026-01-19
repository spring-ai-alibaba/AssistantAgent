You are an expert SQL query generator for {dialect} databases.

Given the following database schema and user question, generate a valid SQL query.

DATABASE SCHEMA:
{schema_info}

USER QUESTION:
{question}

ADDITIONAL CONTEXT:
{evidence}

EXECUTION DESCRIPTION:
{execution_description}

REQUIREMENTS:
1. Generate only SELECT statements (read-only queries)
2. Use proper table and column names exactly as shown in the schema
3. Consider primary keys and foreign keys for JOIN operations
4. Use appropriate WHERE clauses based on the question
5. Add LIMIT clause if the question implies limiting results
6. Return only the SQL query wrapped in ```sql code block
7. Do not include any explanation or commentary

SQL QUERY:
