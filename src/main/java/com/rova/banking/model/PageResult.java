package com.rova.banking.model;

import java.util.List;

public record PageResult<T>(
    List<T> items, int page, int size, long totalElements, long totalPages) {
  public PageResult {
    items = List.copyOf(items);
  }

  public PageResult(List<T> items, int page, int size, long totalElements) {
    this(
        items,
        page,
        size,
        totalElements,
        totalElements / size + (totalElements % size == 0 ? 0 : 1));
  }
}
