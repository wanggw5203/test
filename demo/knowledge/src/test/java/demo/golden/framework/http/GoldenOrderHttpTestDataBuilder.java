package demo.golden.framework.http;

import io.testkit.basetest.data.CaseContext;
import java.util.Map;
import java.util.LinkedHashMap;

/** Business data extension point. Wire the project's DataFactory/DataBuilder here. */
public class GoldenOrderHttpTestDataBuilder {
    public Map<String, Object> prepare(CaseContext context, Map<String, Object> request) {
        // TODO: resolve runtime data references from verified business builders.
        // Preserve explicit negative inputs; never replace them with valid data.
        return new LinkedHashMap<>(request);
    }
    public void cleanup(CaseContext context, Map<String, Object> request) {
        // TODO: release only the data created by this invocation.
    }
}
