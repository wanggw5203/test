package io.github.aitestflow.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequirementImporterTest {
    @TempDir
    Path directory;

    @Test
    void readsMarkdownAsKnowledgeMaterial() throws Exception {
        Path file = directory.resolve("采购审批.md");
        Files.writeString(file, "# 原始需求\n允许采购员提交审批");

        var result = new RequirementImporter().read(List.of(file));

        assertTrue(result.text().contains("允许采购员提交审批"));
        assertEquals(List.of(file.toAbsolutePath().normalize()), result.sources());
        assertTrue(result.images().isEmpty());
    }

    @Test
    void extractsVisibleTextFromPrototypeHtml() throws Exception {
        Path file = directory.resolve("prototype.html");
        Files.writeString(file, "<html><body><button>提交审批</button><div>成功后返回列表</div></body></html>");

        var result = new RequirementImporter().read(List.of(file));

        assertTrue(result.text().contains("提交审批"));
        assertTrue(result.text().contains("成功后返回列表"));
    }
}
