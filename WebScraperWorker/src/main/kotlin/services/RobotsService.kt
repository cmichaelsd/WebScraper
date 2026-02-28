package org.example.services

import io.ktor.client.*
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.util.RobotsRules
import java.net.URI

class RobotsService(private val client: HttpClient) {
    private val cache = mutableMapOf<String, RobotsRules>()
    private val mutex = Mutex()
    private val userAgent = "WebScraperBot"

    suspend fun isAllowed(url: String): Boolean {
        val uri = URI(url)
        val domain = uri.host ?: return true
        val path = uri.path ?: "/"

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
        cache[domain]?.let { return it }

        mutex.withLock {
            cache[domain]?.let { return it }

            return try {
                val rules = client.get("${domain}/robots.txt").bodyAsText().let {
                    parseRobots(it)
                }
                cache[domain] = rules
                rules
            } catch (e: Exception) {
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
                    applies = agent == "*" || agent == userAgent
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