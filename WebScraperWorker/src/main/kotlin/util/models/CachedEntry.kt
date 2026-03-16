package org.webscraper.util.models

import java.time.Instant

data class CachedEntry(
    val rules: RobotsRules?,
    val fetchedAt: Instant,
)
