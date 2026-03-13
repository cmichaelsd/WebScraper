package org.webscraper.util

import java.time.Duration
import java.time.Instant

class RulesCache {
    companion object {
        private const val MAX_CACHE_SIZE = 1000
        private val ttl = Duration.ofMinutes(5)
    }

    private val cache = linkedMapOf<String, CachedEntry>()

    fun get(domain: String): RobotsRules? {
        cache[domain]?.let { entry ->
            if (Duration.between(entry.fetchedAt, Instant.now()) < ttl) {
                return entry.rules
            }
        }
        return null
    }

    fun add(
        domain: String,
        rules: RobotsRules?,
    ) {
        if (cache.size >= MAX_CACHE_SIZE) cache.remove(cache.keys.first())
        cache[domain] = CachedEntry(rules, Instant.now())
    }
}
