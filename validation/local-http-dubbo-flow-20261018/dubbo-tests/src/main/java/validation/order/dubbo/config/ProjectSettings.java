package validation.order.dubbo.config;
import java.io.InputStream;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.Properties;
public final class ProjectSettings {
    private static final Map<String,String> defaults = new LinkedHashMap<>();
    private ProjectSettings() {}
    public static synchronized void load() {
        String env = System.getProperty("test.env", System.getenv().getOrDefault("TEST_ENV", "local"));
        if (!env.matches("[A-Za-z0-9_-]+")) throw new IllegalArgumentException("Invalid test.env");
        String resource = "environments/" + env + ".properties";
        try (InputStream in = ProjectSettings.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) throw new IllegalStateException("Environment configuration not found: " + resource);
            Properties values = new Properties(); values.load(in);
            values.forEach((key, value) -> {
                String name = key.toString();
                if (System.getProperty(name) == null) {
                    System.setProperty(name, value.toString()); defaults.put(name, value.toString());
                }
            });
        } catch (java.io.IOException failure) { throw new IllegalStateException("Cannot load " + resource, failure); }
    }
    public static String value(String key) {
        String value = System.getProperty(key);
        if (value == null) throw new IllegalStateException("Project is not initialized or setting is missing: " + key);
        return value;
    }
    public static synchronized void clearDefaults() {
        defaults.forEach((key,value) -> { if (value.equals(System.getProperty(key))) System.clearProperty(key); });
        defaults.clear();
    }
}
