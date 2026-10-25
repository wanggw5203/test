package io.github.aitestflow.service;

import com.intellij.openapi.project.Project;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 读取随插件发布的测试分析 Skills。知识库项目不需要安装或维护 Skill 文件。
 */
public final class SkillRepository {
    private static final int MAX_CONTEXT_CHARS = 120_000;
    private static final Map<String, List<String>> BUILTIN_FILES = Map.of(
            "requirement-to-tad", List.of("SKILL.md", "references/tad-analysis.md"),
            "tad-to-test-yaml", List.of("SKILL.md", "references/test-yaml.md"),
            "test-yaml-to-web-atc", List.of("SKILL.md", "references/web-atc.md"),
            "api-test-automation-workflow", List.of("SKILL.md", "references/generator-spec.md", "references/api-feedback-contract.md"),
            "close-atc-feedback-loop", List.of("SKILL.md", "references/failure-and-repair.md", "references/feedback-report.md"),
            "requirement-to-atc-workflow", List.of("SKILL.md", "references/pipeline-contract.md")
    );

    public SkillRepository(Project project) {
        // 保留 Project 参数以维持服务调用接口；Skill 内容与用户项目解耦。
    }

    public String load(String skillName) throws IOException {
        List<String> files = BUILTIN_FILES.get(skillName);
        if (files == null) throw new IOException("插件未内置 Skill: " + skillName);
        StringBuilder context = new StringBuilder();
        ClassLoader loader = SkillRepository.class.getClassLoader();
        for (String file : files) {
            String resource = "builtin-skills/" + skillName + "/" + file;
            try (InputStream input = loader.getResourceAsStream(resource)) {
                if (input == null) throw new IOException("插件内置 Skill 文件缺失: " + resource);
                context.append("\n\n===== ").append(file).append("（插件内置） =====\n")
                        .append(new String(input.readAllBytes(), StandardCharsets.UTF_8));
            }
            if (context.length() > MAX_CONTEXT_CHARS) {
                throw new IOException("插件内置 Skill 上下文超过 " + MAX_CONTEXT_CHARS + " 字符");
            }
        }
        return context.toString();
    }
}
