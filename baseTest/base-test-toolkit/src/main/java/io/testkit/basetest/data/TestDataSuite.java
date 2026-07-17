package io.testkit.basetest.data;

import java.util.ArrayList;
import java.util.List;

public class TestDataSuite {
    private String description;
    private List<TestCaseData> cases = new ArrayList<>();
    private List<String> debugCases = new ArrayList<>();

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public List<TestCaseData> getCases() { return cases; }
    public void setCases(List<TestCaseData> cases) {
        this.cases = cases == null ? new ArrayList<>() : new ArrayList<>(cases);
    }
    public List<String> getDebugCases() { return debugCases; }
    public void setDebugCases(List<String> debugCases) {
        this.debugCases = debugCases == null ? new ArrayList<>() : new ArrayList<>(debugCases);
    }
}
