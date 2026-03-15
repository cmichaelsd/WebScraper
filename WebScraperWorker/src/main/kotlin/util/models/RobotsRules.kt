package org.webscraper.util.models

data class RobotsRules(
    val disallowed: List<String>,
    val crawlDelaySeconds: Long?,
)