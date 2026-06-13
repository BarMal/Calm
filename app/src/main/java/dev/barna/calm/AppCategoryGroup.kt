package dev.barna.calm

data class AppCategoryGroup(
    val category: AppCategory,
    val apps: List<AppEntry>,
)
