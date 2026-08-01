package sample;

import io.testkit.basetest.data.DataBuilder;

public class SampleBuilder {
    @DataBuilder(description = "create a reusable record", groups = {"data"})
    public String createRecord(String name) {
        return "created:" + name;
    }
}
