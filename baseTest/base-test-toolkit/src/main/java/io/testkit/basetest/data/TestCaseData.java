package io.testkit.basetest.data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class TestCaseData {
    private String name;
    private Boolean enabled = true;
    private String thought;
    private String mockId;
    private List<String> users = new ArrayList<>();
    private List<String> request = new ArrayList<>();
    private List<String> expect = new ArrayList<>();
    private Map<String, Object> extraParams = new LinkedHashMap<>();

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getThought() { return thought; }
    public void setThought(String thought) { this.thought = thought; }
    public String getMockId() { return mockId; }
    public void setMockId(String mockId) { this.mockId = mockId; }
    public List<String> getUsers() { return users; }
    public void setUsers(List<String> users) { this.users = safeList(users); }
    public List<String> getRequest() { return request; }
    public void setRequest(List<String> request) { this.request = safeList(request); }
    public List<String> getExpect() { return expect; }
    public void setExpect(List<String> expect) { this.expect = safeList(expect); }
    public Map<String, Object> getExtraParams() { return extraParams; }
    public void setExtraParams(Map<String, Object> extraParams) {
        this.extraParams = extraParams == null ? new LinkedHashMap<>() : new LinkedHashMap<>(extraParams);
    }

    private static List<String> safeList(List<String> values) {
        return values == null ? new ArrayList<>() : new ArrayList<>(values);
    }
}
