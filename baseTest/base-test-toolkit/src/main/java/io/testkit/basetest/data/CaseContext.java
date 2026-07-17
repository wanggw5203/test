package io.testkit.basetest.data;

import java.util.List;
import java.util.Map;

public record CaseContext(
        String name,
        String thought,
        String mockId,
        List<String> users,
        Map<String, Object> extraParams) {
    public CaseContext {
        users = List.copyOf(users == null ? List.of() : users);
        extraParams = Map.copyOf(extraParams == null ? Map.of() : extraParams);
    }

    public static CaseContext from(TestCaseData data) {
        return new CaseContext(data.getName(), data.getThought(), data.getMockId(),
                data.getUsers(), data.getExtraParams());
    }
}
