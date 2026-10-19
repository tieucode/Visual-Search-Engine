package com.visualsearch.dto.search;

import com.visualsearch.enums.SearchType;
import java.util.List;
import java.util.UUID;

public record SearchPageData<T>(UUID searchId, SearchType searchType, List<T> items,
                             int size, boolean hasMore, String nextCursor) {}
