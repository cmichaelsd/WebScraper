package org.webscraper.util.models

import java.time.Instant
import org.webscraper.util.models.RobotsRules

data class CachedEntry(
    val rules: RobotsRules?,
    val fetchedAt: Instant,
)