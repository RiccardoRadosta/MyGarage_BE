package com.ilmiogarage.dto.common;

import java.util.List;
import org.springframework.data.domain.Page;

public record PagedResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalItems,
        int totalPages
) {
    public static <T> PagedResponse<T> from(Page<?> page, List<T> mappedItems) {
        return new PagedResponse<>(
                mappedItems,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
