package io.github.aitestflow.settings;

import com.intellij.credentialStore.CredentialAttributes;
import com.intellij.ide.passwordSafe.PasswordSafe;
import com.intellij.openapi.project.Project;

public final class ApiKeyStore {
    private ApiKeyStore() {}

    private static CredentialAttributes attributes(Project project) {
        String suffix = project.getLocationHash();
        return new CredentialAttributes("AI Test Flow API Key", suffix);
    }

    public static String get(Project project) {
        String value = PasswordSafe.getInstance().getPassword(attributes(project));
        return value == null ? "" : value;
    }

    public static void set(Project project, String value) {
        PasswordSafe.getInstance().setPassword(attributes(project), value == null ? "" : value.trim());
    }
}
