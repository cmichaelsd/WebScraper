package org.example.services

import io.ktor.client.request.get
import io.ktor.client.HttpClient
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import org.example.util.DomainThrottle
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

class CrawlerService(private val client: HttpClient) {
    companion object {
        private const val DOMAIN_DELAY_MS = 2000L
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACK_OFF_MS = 2000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    private val robotsService = RobotsService(client)
    private val globalLimiter = Semaphore(5)
    private val domainLimiters = ConcurrentHashMap<String, DomainThrottle>()

//    suspend fun crawl(seedUrls: List<String>, maxDepth: Int) {
//        for (url in seedUrls) {
//            crawlSingle(url, 0 , maxDepth)
//        }
//    }

    suspend fun crawlSingle(
        url: String,
        depth: Int,
        maxDepth: Int
    ): List<String> {
        if (depth > maxDepth) return emptyList()

        if (!robotsService.isAllowed(url)) return emptyList()

        var attempt = 0
        var backoff = INITIAL_BACK_OFF_MS

        while (attempt < MAX_RETRIES) {
            try {
                throttleDomain(url)

                val response: HttpResponse = globalLimiter.withPermit {
                    client.get(url)
                }

                val status = response.status.value

                if (status == 429 || status == 503) {
                    val retryAfterHeader = response.headers["Retry-After"]
                    val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
                    val waitTime = retryAfterSeconds?.times(1000) ?: backoff.coerceAtMost(MAX_BACKOFF_MS)

                    delay(waitTime)

                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                    ++attempt
                    continue
                }

                val html = response.bodyAsText()
                return extractLinks(html)

            } catch (e: Exception) {
                delay(backoff.coerceAtMost(MAX_BACKOFF_MS))
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                ++attempt
            }
        }

        return emptyList()
    }

    private suspend fun throttleDomain(url: String) {
        val domain = try {
            URI(url).host ?: return
        } catch (e: Exception) {
            return
        }

        val limiter = domainLimiters.computeIfAbsent(domain) {
            DomainThrottle()
        }

        limiter.mutex.withLock {
            val crawlDelaySeconds = robotsService.getCrawlDelay(url)
            val effectiveDelay = (crawlDelaySeconds?.times(1000)) ?: DOMAIN_DELAY_MS

            val now = System.currentTimeMillis()
            val elapsed = now - limiter.lastRequestTime

            if (elapsed < effectiveDelay) {
                delay(DOMAIN_DELAY_MS - elapsed)
            }

            limiter.lastRequestTime = System.currentTimeMillis()
        }
    }

    private fun extractLinks(html: String): List<String> {
        val regex = Regex("""href=["'](https?://[^"']+)["']""")
        return regex.findAll(html)
            .map { it.groupValues[1] }
            .toList()
    }
}