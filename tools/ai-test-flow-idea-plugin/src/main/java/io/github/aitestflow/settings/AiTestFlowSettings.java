package io.github.aitestflow.settings;

import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

@Service(Service.Level.PROJECT)
@State(name = "AiTestFlowSettings", storages = @Storage("ai-test-flow.xml"))
public final class AiTestFlowSettings implements PersistentStateComponent<AiTestFlowSettings.StateData> {
    public static final class StateData {
        public String baseUrl = "https://api.openai.com/v1";
        public String model = "gpt-5-mini";
        public double temperature = 0.1;
        public boolean demoMode = true;
        public String goldenDataset = "order-create-v1";
    }

    private StateData state = new StateData();

    public static AiTestFlowSettings getInstance(Project project) {
        return project.getService(AiTestFlowSettings.class);
    }

    @Override
    public @Nullable StateData getState() {
        return state;
    }

    @Override
    public void loadState(@NotNull StateData state) {
        this.state = state;
    }

    public StateData data() {
        return state;
    }
}
