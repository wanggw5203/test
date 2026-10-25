package validation.order.bootstrap;
import validation.order.config.ProjectSettings;
import io.testkit.basetest.runtime.*;
import java.nio.file.Path;
import org.testng.IExecutionListener;
/** Project-owned composition root. Add real identity/DataFactory providers here. */
public final class ProjectBootstrap implements IExecutionListener {
    private AutomationRuntime previous;
    @Override public void onExecutionStart() {
        ProjectSettings.load();
        previous = RuntimeRegistry.current();
        JsonLinesResultPublisher report = new JsonLinesResultPublisher(Path.of(ProjectSettings.value("testkit.report.file")));
        // Unsupported business capabilities remain explicit, not simulated as configured.
        RuntimeRegistry.install(new AutomationRuntime(null, null, report, null, report, null, null, null));
    }
    @Override public void onExecutionFinish() {
        try { if (previous != null) RuntimeRegistry.install(previous); }
        finally { ProjectSettings.clearDefaults(); }
    }
}
