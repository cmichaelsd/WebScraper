package org.webscraper.services

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.webscraper.util.CachedEntry
import org.webscraper.util.RobotsRules
import java.net.URI
import java.time.Duration
import java.time.Instant

class RobotsService(private val client: HttpClient) {
    private val logger = LoggerFactory.getLogger(RobotsService::class.java)

    private val cache = mutableMapOf<String, CachedEntry>()
    private val mutex = Mutex()
    private val userAgent = "WebScraperBot"
    private val ttl = Duration.ofMinutes(5)

    suspend fun isAllowed(url: String): Boolean {
        val uri = URI(url)
        val domain = uri.host ?: return true
        val path = uri.path.ifBlank { "/" }

        val rules = getRules(domain) ?: return true

        return rules.disallowed.none {
            path.startsWith(it)
        }
    }

    suspend fun getCrawlDelay(url: String): Long? {
        val uri = URI(url)
        val domain = uri.host ?: return null
        val rules = getRules(domain)
        return rules?.crawlDelaySeconds
    }

    private suspend fun getRules(domain: String): RobotsRules? {
        cache[domain]?.let { entry ->
            if (Duration.between(entry.fetchedAt, Instant.now()) < ttl) {
                return entry.rules
            }
        }

        mutex.withLock {
            cache[domain]?.let { entry ->
                if (Duration.between(entry.fetchedAt, Instant.now()) < ttl) {
                    return entry.rules
                }
            }

            return try {
                val rules = client.get("https://${domain}/robots.txt").bodyAsText().let {
                    parseRobots(it)
                }
                cache[domain] = CachedEntry(rules, Instant.now())
                rules
            } catch (e: Exception) {
                logger.warn("getRules: Failed to fetch robots.txt for $domain - ${e.message}. Disallowing by default.")
                null
            }
        }
    }

    private fun parseRobots(content: String): RobotsRules {
        val lines = content.lines()
        var applies = false
        val disallowed = mutableListOf<String>()
        var crawlDelay: Long? = null

        for (line in lines) {
            val trimmed = line.trim()

            when {
                trimmed.startsWith("User-agent:", ignoreCase = true) -> {
                    val agent = trimmed.substringAfter(":").trim()
                    applies = agent == "*" || agent.equals(userAgent, ignoreCase = true)
                }

                applies && trimmed.startsWith("Disallow:", ignoreCase = true) -> {
                    val path = trimmed.substringAfter(":").trim()
                    if (path.isNotEmpty()) disallowed.add(path)
                }

                applies && trimmed.startsWith("Crawl-delay:", ignoreCase = true) -> {
                    crawlDelay = trimmed.substringAfter(":").trim().toLongOrNull()
                }
            }
        }

        return RobotsRules(disallowed, crawlDelay)
    }
}