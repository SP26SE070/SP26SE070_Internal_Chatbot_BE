package com.gsp26se114.chatbot_rag_be.payload.response;

import java.util.List;

public record PageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages,
    boolean last
) {
    public static <T> PageResponse<T> of(List<T> allItems, int page, int size) {
        int totalElements = allItems.size();
        int totalPages = size == 0 ? 1 : (int) Math.ceil((double) totalElements / size);
        int clampedPage = Math.max(0, Math.min(page, totalPages - 1));
        int fromIndex = clampedPage * size;
        int toIndex = Math.min(fromIndex + size, totalElements);
        List<T> content = fromIndex >= totalElements ? List.of() : allItems.subList(fromIndex, toIndex);
        boolean last = clampedPage >= totalPages - 1;
        return new PageResponse<>(content, clampedPage, size, totalElements, totalPages, last);
    }
}