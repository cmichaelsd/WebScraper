package org.example.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.example.util.RobotsRules
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class RobotsService(private val client: HttpClient) {
    private val cache = ConcurrentHashMap<String, RobotsRules>()
    private val mutex = Mutex()
    private val userAgent = "WebScraperBot"

    suspend fun isAllowed(url: String): Boolean {
        val uri = URI(url)
        val domain = uri.scheme + "://" + uri.host
        val path = uri.path ?: "/"

        val rules = getRules(domain) ?: return true

        return rules.disallowed.none {
            path.startsWith(it)
        }
    }

    suspend fun getCrawlDelay(url: String): Long? {
        val uri = URI(url)
        val domain = uri.scheme + "://" + uri.host
        val rules = getRules(domain)
        return rules?.crawlDelaySeconds
    }

    private suspend fun getRules(domain: String): RobotsRules? {
        cache[domain]?.let { return it }

        mutex.withLock {
            cache[domain]?.let { return it }

            return try {
                val robotsUrl = "$domain/robots.txt"
                val response = client.get(robotsUrl)
                val body = response.bodyAsText()
                val rules = parseRobots(body)
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