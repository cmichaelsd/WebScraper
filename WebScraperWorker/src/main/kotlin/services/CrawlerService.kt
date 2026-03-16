package org.webscraper.services

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.delay
import org.webscraper.util.DomainLimiter
import org.webscraper.util.UrlHelper

class CrawlerService(
    private val robotsService: RobotsService,
    private val client: HttpClient,
    private val domainLimiter: DomainLimiter,
    private val urlHelper: UrlHelper,
) {
    companion object {
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACK_OFF_MS = 2000L
        private const val MAX_BACKOFF_MS = 60_000L
    }

    suspend fun crawlSingle(
        url: String,
        depth: Int,
        maxDepth: Int,
    ): List<String> {
        if (depth > maxDepth) return emptyList()

        if (!robotsService.isAllowed(url)) return emptyList()

        var attempt = 0
        var backoff = INITIAL_BACK_OFF_MS

        while (attempt < MAX_RETRIES) {
            try {
                domainLimiter.throttleDomain(url)

                val response: HttpResponse = client.get(url)

                val status = response.status.value

                if (status == HttpStatusCode.TooManyRequests.value || status == HttpStatusCode.ServiceUnavailable.value) {
                    val retryAfterHeader = response.headers["Retry-After"]
                    val retryAfterSeconds = retryAfterHeader?.toLongOrNull()
                    val waitTime = retryAfterSeconds?.times(1000) ?: backoff.coerceAtMost(MAX_BACKOFF_MS)

                    delay(waitTime)

                    backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                    ++attempt
                    continue
                }

                val html = response.bodyAsText()
                return urlHelper.extractLinks(html, url)
            } catch (e: Exception) {
                delay(backoff.coerceAtMost(MAX_BACKOFF_MS))
                backoff = (backoff * 2).coerceAtMost(MAX_BACKOFF_MS)
                ++attempt
            }
        }

        return emptyList()
    }
}
