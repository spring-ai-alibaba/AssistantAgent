package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.ColumnInfoBO;
import com.alibaba.assistant.agent.data.model.DatasourceDefinition;
import com.alibaba.assistant.agent.data.model.TableInfoBO;
import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultNl2SqlServiceTest {

    @Mock
    private SchemaProvider schemaProvider;

    @Mock
    private ChatModel chatModel;

    @Mock
    private SqlExecutionProvider sqlExecutionProvider;

    @Mock
    private DatasourceProvider datasourceProvider;

    private DefaultNl2SqlService nl2SqlService;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        nl2SqlService = new DefaultNl2SqlService(
                schemaProvider,
                chatModel,
                sqlExecutionProvider,
                datasourceProvider
        );
    }

    @Test
    void shouldThrowExceptionForNullSystemId() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql(null, "query", null)
        );
    }

    @Test
    void shouldThrowExceptionForEmptySystemId() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("", "query", null)
        );
    }

    @Test
    void shouldThrowExceptionForNullQuery() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("test-system", null, null)
        );
    }

    @Test
    void shouldThrowExceptionForEmptyQuery() {
        assertThrows(IllegalArgumentException.class, () ->
                nl2SqlService.generateSql("test-system", "", null)
        );
    }

    @Test
    void shouldThrowExceptionWhenSchemaNotFound() throws Exception {
        when(schemaProvider.getTableList(eq("invalid-system"), isNull(), isNull()))
                .thenReturn(Collections.emptyList());

        Nl2SqlException exception = assertThrows(Nl2SqlException.class, () ->
                nl2SqlService.generateSql("invalid-system", "query", null)
        );

        assertTrue(exception.getMessage().contains("Schema not found"));
    }

    @Test
    void shouldGenerateSqlForSmallSchema() throws Exception {
        // Mock schema with 5 tables (no filtering)
        List<TableInfoBO> tables = createMockTables(5);
        when(schemaProvider.getTableList(eq("test-system"), isNull(), isNull()))
                .thenReturn(tables);

        // Mock datasource for dialect
        DatasourceDefinition datasource = DatasourceDefinition.builder()
                .type("mysql")
                .build();
        when(datasourceProvider.getBySystemId(eq("test-system")))
                .thenReturn(Optional.of(datasource));

        // Mock LLM response
        when(chatModel.call(anyString()))
                .thenReturn("```sql\nSELECT * FROM users WHERE active = 1\n```");

        String sql = nl2SqlService.generateSql("test-system", "查询活跃用户", null);

        assertEquals("SELECT * FROM users WHERE active = 1", sql);
        verify(chatModel, times(1)).call(anyString());
    }

    private List<TableInfoBO> createMockTables(int count) {
        List<TableInfoBO> tables = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            TableInfoBO table = new TableInfoBO();
            table.setName("table" + i);
            table.setDescription("Table " + i);
            table.setColumns(Arrays.asList(createMockColumn("id"), createMockColumn("name")));
            tables.add(table);
        }
        return tables;
    }

    private ColumnInfoBO createMockColumn(String name) {
        ColumnInfoBO column = new ColumnInfoBO();
        column.setName(name);
        column.setDescription(name + " column");
        column.setType("VARCHAR");
        return column;
    }

    @Test
    void shouldFilterSchemaForLargeDatabase() throws Exception {
        // Mock schema with 15 tables (triggers filtering)
        List<TableInfoBO> tables = createMockTables(15);
        when(schemaProvider.getTableList(eq("test-system"), isNull(), isNull()))
                .thenReturn(tables);

        // Mock datasource
        DatasourceDefinition datasource = DatasourceDefinition.builder()
                .type("mysql")
                .build();
        when(datasourceProvider.getBySystemId(eq("test-system")))
                .thenReturn(Optional.of(datasource));

        // Mock LLM responses
        // First call: schema filter
        when(chatModel.call(contains("RELEVANT TABLES")))
                .thenReturn("[\"table1\", \"table2\"]");

        // Second call: SQL generation
        when(chatModel.call(contains("SELECT")))
                .thenReturn("```sql\nSELECT * FROM table1\n```");

        String sql = nl2SqlService.generateSql("test-system", "查询table1数据", null);

        assertNotNull(sql);
        assertEquals("SELECT * FROM table1", sql);
        verify(chatModel, times(2)).call(anyString()); // Two LLM calls
    }
}
