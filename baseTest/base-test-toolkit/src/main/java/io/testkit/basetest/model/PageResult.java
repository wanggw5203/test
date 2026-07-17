package io.testkit.basetest.model;

import java.util.List;

public record PageResult<T>(List<T> items, long total, int page, int size) {
    public PageResult {
        items = List.copyOf(items == null ? List.of() : items);
        if (total < 0) throw new IllegalArgumentException("total must be >= 0");
        if (page < 0) throw new IllegalArgumentException("page must be >= 0");
        if (size <= 0) throw new IllegalArgumentException("size must be > 0");
    }

    public int pageCount() {
        return (int) Math.ceil((double) total / size);
    }
}
