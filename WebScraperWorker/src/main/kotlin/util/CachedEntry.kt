package org.webscraper.util

import java.time.Instant

data class CachedEntry(
    val rules: RobotsRules?,
    val fetchedAt: Instant,
)
