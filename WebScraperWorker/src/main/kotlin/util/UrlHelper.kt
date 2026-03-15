package org.webscraper.util

import java.net.URI

class UrlHelper {
    fun extractLinks(
        html: String,
        baseUrl: String,
    ): List<String> {
        val base = URI(baseUrl)
        val regex = Regex("""href=["']([^"']+)["']""")
        return regex.findAll(html)
            .map { it.groupValues[1].substringBefore('#') }
            .filter { it.isNotBlank() }
            .mapNotNull { href ->
                try {
                    val resolved = base.resolve(href)
                    if (resolved.scheme in listOf("http", "https")) {
                        normalizeUrl(resolved)
                    } else {
                        null
                    }
                } catch (e: Exception) {
                    null
                }
            }
            .distinct()
            .toList()
    }

    private fun normalizeUrl(uri: URI): String {
        val path = uri.path.trimEnd('/').ifEmpty { "/" }
        return URI(
            uri.scheme.lowercase(),
            uri.authority?.lowercase(),
            path,
            uri.query,
            null,
        ).toString()
    }
}