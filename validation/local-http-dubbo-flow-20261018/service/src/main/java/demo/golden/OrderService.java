package demo.golden;

import java.util.Map;

/** Real exported Dubbo interface; the same rule set is also exposed over HTTP. */
public interface OrderService {
    Map<String, Object> create(Map<String, Object> request);
}
