package com.example.automation.sdk;
/** Bind a verified SDK client supplied by your real SDK dependency; no fake SDK is created. */
public final class SdkAdapter<T> {
    private final T client;
    public SdkAdapter(T client) { this.client = java.util.Objects.requireNonNull(client); }
    public T client() { return client; }
}
