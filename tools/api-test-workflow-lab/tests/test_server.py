import importlib.util
import os
import sys
import tempfile
import unittest
from pathlib import Path


SERVER_PATH = Path(__file__).resolve().parents[1] / "server.py"
SPEC = importlib.util.spec_from_file_location("workflow_lab_server", SERVER_PATH)
server = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = server
SPEC.loader.exec_module(server)


class WorkflowLabServerTest(unittest.TestCase):
    def test_llm_status_is_safe_without_key(self):
        previous = os.environ.pop("LLM_API_KEY", None)
        try:
            status = server.llm_status()
            self.assertFalse(status["configured"])
            self.assertNotIn("apiKey", status)
        finally:
            if previous is not None:
                os.environ["LLM_API_KEY"] = previous

    def test_llm_stage_loads_skill_context(self):
        context = server.skill_context("requirement-to-tad")
        self.assertIn("name: requirement-to-tad", context)

    def test_inspect_project_counts_framework_evidence(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "pom.xml").write_text("<project/>", encoding="utf-8")
            test_dir = root / "src/test/java/demo"
            resource_dir = root / "src/test/resources/demo"
            api_dir = root / "src/main/java/demo"
            test_dir.mkdir(parents=True)
            resource_dir.mkdir(parents=True)
            api_dir.mkdir(parents=True)
            (test_dir / "DemoTests.java").write_text("class DemoTests {}", encoding="utf-8")
            (test_dir / "DemoPreparer.java").write_text("class DemoPreparer {}", encoding="utf-8")
            (resource_dir / "demo.yaml").write_text("testDataSuite: {}", encoding="utf-8")
            (resource_dir / "demo.json5").write_text("{}", encoding="utf-8")
            (api_dir / "DemoApi.java").write_text("class DemoApi {}", encoding="utf-8")

            evidence = server.inspect_project(root)

            self.assertTrue(evidence["pom"])
            self.assertEqual(1, evidence["testClasses"])
            self.assertEqual(1, evidence["suiteFiles"])
            self.assertEqual(1, evidence["json5Files"])
            self.assertEqual(1, evidence["apiClasses"])
            self.assertEqual(1, evidence["dataPreparers"])

    def test_prepare_session_copies_non_git_project(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "pom.xml").write_text("<project/>", encoding="utf-8")
            (root / "src").mkdir()
            (root / "src/example.txt").write_text("ok", encoding="utf-8")

            session = server.prepare_session(root, "copy")

            self.assertNotEqual(root, session.workspace)
            self.assertTrue((session.workspace / "src/example.txt").is_file())

    def test_prepare_session_rejects_missing_pom(self):
        with tempfile.TemporaryDirectory() as directory:
            with self.assertRaisesRegex(ValueError, "pom.xml"):
                server.prepare_session(Path(directory), "copy")


if __name__ == "__main__":
    unittest.main()
