package dev.barna.calm

data class RssFeedItem(
    val feedTitle: String,
    val title: String,
    val summary: String,
    val link: String,
    val publishedAt: Long,
)
