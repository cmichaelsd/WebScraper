package org.webscraper.util

import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.withLock
import org.webscraper.services.RobotsService
import org.webscraper.util.models.DomainThrottle


class DomainLimiter(
    private val robotsService: RobotsService,
    private val clock: () -> Long = { System.currentTimeMillis() }
) {
    companion object {
        private const val DOMAIN_DELAY_MS = 2000L
        private const val MAX_DOMAIN_ENTRIES = 5000
        private const val DOMAIN_EVICT_AGE_MS = 300_000L // 5 minutes
    }

    private val map = ConcurrentHashMap<String, DomainThrottle>()

    suspend fun throttleDomain(url: String) {
        val domain = URI(url).host ?: return

        if (map.size > MAX_DOMAIN_ENTRIES) {
            val cutoff = clock() - DOMAIN_EVICT_AGE_MS
            map.entries.removeIf { it.value.lastRequestTime < cutoff }
        }

        val limiter =
            map.computeIfAbsent(domain) {
                DomainThrottle()
            }

        limiter.mutex.withLock {
            val crawlDelaySeconds = robotsService.getCrawlDelay(url)
            val effectiveDelay = (crawlDelaySeconds?.times(1000)) ?: DOMAIN_DELAY_MS

            val now = clock()
            val elapsed = now - limiter.lastRequestTime

            if (elapsed < effectiveDelay) {
                delay(effectiveDelay - elapsed)
            }

            limiter.lastRequestTime = clock()
        }
    }
}