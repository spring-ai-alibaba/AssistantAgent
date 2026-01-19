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

import static org.junit.jupiter.api.Assertions.*;

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
}
