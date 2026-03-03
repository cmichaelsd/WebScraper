package org.webscraper.util

data class RobotsRules(
    val disallowed: List<String>,
    val crawlDelaySeconds: Long?
)
