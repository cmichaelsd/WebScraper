package org.webscraper.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.webscraper.util.RulesCache
import org.webscraper.util.models.RobotsRules
import java.net.URI

class RobotsService(
    private val client: HttpClient,
    private val rulesCache: RulesCache,
) {
    companion object {
        private const val USER_AGENT = "WebScraperBot"
    }

    private val logger = LoggerFactory.getLogger(RobotsService::class.java)
    private val mutex = Mutex()

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
        rulesCache.get(domain)?.let { entry ->
            return entry
        }

        mutex.withLock {
            rulesCache.get(domain)?.let { entry ->
                return entry
            }

            return try {
                val rules =
                    client.get("https://$domain/robots.txt").bodyAsText().let {
                        parseRobots(it)
                    }
                rulesCache.add(domain, rules)
                rules
            } catch (e: Exception) {
                logger.warn("getRules: Failed to fetch robots.txt for $domain - ${e.message}. Allowing.")
                rulesCache.add(domain, null)
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
                    applies = agent == "*" || agent.equals(USER_AGENT, ignoreCase = true)
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
