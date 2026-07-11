package com.wanggw.api.scaffold;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ScaffoldGeneratorCliTest {
    @Test
    void printsEmbeddedExampleSpec() {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        int exitCode = ScaffoldGeneratorCli.run(new String[]{"example"},
                new PrintStream(buffer, true, StandardCharsets.UTF_8), System.err);

        assertEquals(0, exitCode);
        String output = buffer.toString(StandardCharsets.UTF_8);
        assertTrue(output.contains("apiCode: apicode_41032_5201_generated"));
        assertTrue(output.contains("fieldHints:"));
    }
}
