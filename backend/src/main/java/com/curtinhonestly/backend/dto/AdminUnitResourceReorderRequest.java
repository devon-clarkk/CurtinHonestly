package com.curtinhonestly.backend.dto;

import java.util.List;

/** Bulk sort-order update: each entry sets one row's sortOrder. Unknown ids are ignored. */
public record AdminUnitResourceReorderRequest(List<Item> items) {
    public record Item(String id, int sortOrder) {}
}
