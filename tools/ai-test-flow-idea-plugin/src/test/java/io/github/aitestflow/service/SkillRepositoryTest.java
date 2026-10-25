package io.github.aitestflow.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRepositoryTest {
    @Test
    void loadsSkillsFromPluginResourcesWithoutProjectFiles() throws Exception {
        SkillRepository repository = new SkillRepository(null);

        String tadSkill = repository.load("requirement-to-tad");
        String testcaseSkill = repository.load("tad-to-test-yaml");

        assertTrue(tadSkill.contains("需求转 TAD"));
        assertTrue(tadSkill.contains("TAD 分析规则"));
        assertTrue(testcaseSkill.contains("TAD 转 YAML"));
        assertTrue(testcaseSkill.contains("YAML 测试用例规则"));
    }
}
