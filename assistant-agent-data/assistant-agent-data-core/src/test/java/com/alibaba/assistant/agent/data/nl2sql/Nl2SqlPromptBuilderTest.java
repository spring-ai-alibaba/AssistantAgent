package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.ColumnDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SchemaDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.SqlGenerationDTO;
import com.alibaba.assistant.agent.data.model.nl2sql.TableDTO;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class Nl2SqlPromptBuilderTest {

    @Test
    void shouldBuildSqlGenerationPrompt() {
        TableDTO table = new TableDTO();
        table.setName("users");
        table.setDescription("User accounts");

        ColumnDTO column = new ColumnDTO();
        column.setName("id");
        column.setType("INT");
        column.setDescription("User ID");
        table.setColumn(Arrays.asList(column));
        table.setPrimaryKeys(Arrays.asList("id"));

        SchemaDTO schema = new SchemaDTO();
        schema.setName("testdb");
        schema.setTable(Arrays.asList(table));

        SqlGenerationDTO dto = SqlGenerationDTO.builder()
                .query("查询所有用户")
                .evidence("用户表名为users")
                .schemaDTO(schema)
                .dialect("mysql")
                .executionDescription("Generate SQL for user query")
                .build();

        String prompt = Nl2SqlPromptBuilder.buildSqlGenerationPrompt(dto);

        assertNotNull(prompt);
        assertTrue(prompt.contains("查询所有用户"));
        assertTrue(prompt.contains("用户表名为users"));
        assertTrue(prompt.contains("mysql"));
        assertTrue(prompt.contains("users"));
    }

    @Test
    void shouldBuildSchemaInfo() {
        TableDTO table = new TableDTO();
        table.setName("orders");
        table.setDescription("Order records");

        ColumnDTO col1 = new ColumnDTO();
        col1.setName("order_id");
        col1.setType("BIGINT");
        col1.setDescription("Order ID");

        ColumnDTO col2 = new ColumnDTO();
        col2.setName("user_id");
        col2.setType("INT");
        col2.setDescription("User ID");
        col2.setData(Arrays.asList("1", "2", "3"));

        table.setColumn(Arrays.asList(col1, col2));
        table.setPrimaryKeys(Arrays.asList("order_id"));

        SchemaDTO schema = new SchemaDTO();
        schema.setName("ecommerce");
        schema.setTable(Arrays.asList(table));
        schema.setForeignKeys(Arrays.asList("orders.user_id -> users.id"));

        String schemaInfo = Nl2SqlPromptBuilder.buildSchemaInfo(schema);

        assertTrue(schemaInfo.contains("【DB_ID】ecommerce"));
        assertTrue(schemaInfo.contains("# Table: orders"));
        assertTrue(schemaInfo.contains("order_id:BIGINT"));
        assertTrue(schemaInfo.contains("Primary Key"));
        assertTrue(schemaInfo.contains("Examples: [1,2,3]"));
        assertTrue(schemaInfo.contains("【Foreign keys】"));
    }

    @Test
    void shouldBuildSchemaFilterPrompt() {
        List<String> tables = Arrays.asList("users", "orders", "products", "categories");
        String query = "查询用户的订单信息";

        String prompt = Nl2SqlPromptBuilder.buildSchemaFilterPrompt(query, tables);

        assertNotNull(prompt);
        assertTrue(prompt.contains("查询用户的订单信息"));
        assertTrue(prompt.contains("users"));
        assertTrue(prompt.contains("orders"));
    }
}
