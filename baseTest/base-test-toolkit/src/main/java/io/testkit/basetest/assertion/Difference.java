package io.testkit.basetest.assertion;

public record Difference(Type type, String path, String expected, String actual) {
    public enum Type { MISSING, UNEXPECTED, VALUE, TYPE }

    @Override
    public String toString() {
        return type + " at " + path + ": expected=" + expected + ", actual=" + actual;
    }
}
