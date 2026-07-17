package io.testkit.basetest.data;

import io.testkit.basetest.assertion.JsonDiff;
import org.testng.Assert;
import org.testng.annotations.Test;

import java.lang.reflect.Method;
import java.nio.file.Path;
import java.util.List;

public class DataDriveUtilTest {
    public record Request(String keyword, Integer limit) {}
    public record Response(Integer code, List<String> data) {}

    public void sample(CaseContext context, Request request, Response response) {
    }

    @Test
    public void loadsYamlAndJson5Payloads() throws Exception {
        Method method = getClass().getMethod("sample", CaseContext.class, Request.class, Response.class);
        Path suite = Path.of("src/test/resources/cases/search.yaml");

        Object[][] rows = DataDriveUtil.loadTestData(method, suite);

        Assert.assertEquals(rows.length, 1);
        Assert.assertEquals(((CaseContext) rows[0][0]).name(), "search by keyword");
        Assert.assertEquals(rows[0][1], new Request("coffee", 10));
        Assert.assertEquals(rows[0][2], new Response(0, List.of("shop-a")));
    }

    @Test
    public void comparesStrictAndLenientJson() {
        Response expected = new Response(0, List.of("shop-a"));
        Assert.assertTrue(JsonDiff.strict(expected, expected).isEmpty());
        Assert.assertEquals(JsonDiff.strict(expected, new Response(1, List.of("shop-a"))).size(), 1);
        Assert.assertTrue(JsonDiff.lenient(new Request("coffee", null),
                new Request("coffee", 10), "$.limit").isEmpty());
        Assert.assertTrue(JsonDiff.strict(
                java.util.Map.of("first", java.util.Map.of("id", 1), "second", java.util.Map.of("id", 2)),
                java.util.Map.of("first", java.util.Map.of("id", 9), "second", java.util.Map.of("id", 8)),
                "$.*.id").isEmpty());
    }
}
