package com.alibaba.assistant.agent.data.nl2sql;

import com.alibaba.assistant.agent.data.model.nl2sql.Nl2SqlException;
import com.alibaba.assistant.agent.data.spi.DatasourceProvider;
import com.alibaba.assistant.agent.data.spi.SchemaProvider;
import com.alibaba.assistant.agent.data.spi.SqlExecutionProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.ai.chat.model.ChatModel;

import java.util.Collections;

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
}
