package validation.service;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

public final class OrderRules {
    public record Result(int httpStatus, Map<String, Object> body) {}
    private final AtomicBoolean faultyZero = new AtomicBoolean(false);
    public boolean faulty() { return faultyZero.get(); }
    public void faulty(boolean enabled) { faultyZero.set(enabled); }

    public Result create(Map<String, Object> request) {
        if (request == null || request.get("sku") == null || request.get("sku").toString().isBlank())
            return new Result(400, Map.of("code", "SKU_REQUIRED"));
        Object quantity = request.get("quantity");
        if (!(quantity instanceof Number number) || number.doubleValue() <= 0) {
            if (faultyZero.get()) return new Result(200, Map.of("code", "BUG_ZERO_ACCEPTED"));
            return new Result(400, Map.of("code", "INVALID_QUANTITY"));
        }
        return new Result(201, Map.of("code", "OK", "orderId", "ORDER-DEMO-001"));
    }
}
