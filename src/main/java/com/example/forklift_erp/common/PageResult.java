package com.example.forklift_erp.common;

import lombok.Data;

import java.util.List;

@Data
public class PageResult<T> {
    private List<T> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;

    public static <T> PageResult<T> of(List<T> content, int page, int size, long totalElements) {
        PageResult<T> result = new PageResult<>();
        result.setContent(content);
        result.setPage(page);
        result.setSize(size);
        result.setTotalElements(totalElements);
        result.setTotalPages(size <= 0 ? 0 : (int) Math.ceil((double) totalElements / size));
        result.setFirst(page <= 0);
        result.setLast(result.getTotalPages() == 0 || page >= result.getTotalPages() - 1);
        return result;
    }
}
