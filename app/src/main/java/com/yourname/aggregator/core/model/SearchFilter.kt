package com.yourname.aggregator.core.model

enum class SortOrder(val label: String) {
    RELEVANCE("Relevance"),
    LONGEST("Longest First"),
    SHORTEST("Shortest First"),
    MOST_VIEWED("Most Viewed")
}

data class SearchFilter(
    val enabledSourceIds: Set<String> = emptySet(), // Empty = all registered providers active
    val minDurationSeconds: Int = 0,
    val maxDurationSeconds: Int? = null,
    val excludedTags: Set<String> = emptySet(),
    val sortOrder: SortOrder = SortOrder.RELEVANCE
)
