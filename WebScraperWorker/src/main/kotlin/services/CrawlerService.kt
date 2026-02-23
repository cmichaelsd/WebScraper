package org.example.services

import io.ktor.client.request.get
import io.ktor.client.HttpClient
import io.ktor.client.statement.bodyAsText

class CrawlerService(private val client: HttpClient) {
    suspend fun crawl(seedUrls: List<String>, maxDepth: Int) {
        for (url in seedUrls) {
            crawlSingle(url, 0 , maxDepth)
        }
    }

    suspend fun crawlSingle(url: String, depth: Int, maxDepth: Int): List<String> {
        if (depth > maxDepth) return emptyList()
        val response = client.get(url)
        val html = response.bodyAsText()
        return extractLinks(html)
    }

    private fun extractLinks(html: String): List<String> {
        val regex = Regex("""href=["'](https?://[^"']+)["']""")
        return regex.findAll(html)
            .map { it.groupValues[1] }
            .toList()
    }
}