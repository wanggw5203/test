package io.github.aitestflow.service;

import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LlmClientTest {
    @Test
    void parsesOpenAiCompatibleResponse() throws Exception {
        String body = "{\"choices\":[{\"message\":{\"content\":\"hello\"}}]}";
        assertEquals("hello", LlmClient.parseContent(body));
    }

    @Test
    void rejectsResponseWithoutContent() {
        assertThrows(IOException.class, () -> LlmClient.parseContent("{\"choices\":[]}"));
    }
}
