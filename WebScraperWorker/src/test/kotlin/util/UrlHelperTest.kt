package util

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.webscraper.util.UrlHelper
import kotlin.test.Test

class UrlHelperTest {
    private val helper = UrlHelper()

    @Test
    fun `extractLinks returns empty list when no links in html`() {
        val result = helper.extractLinks("<html><body><p>no links</p></body></html>", "https://example.com")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractLinks returns absolute links`() {
        val html = """<a href="https://other.com/page">link</a>"""
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://other.com/page"), result)
    }

    @Test
    fun `extractLinks resolves relative links against base url`() {
        val html = """<a href="/about">link</a>"""
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://example.com/about"), result)
    }

    @Test
    fun `extractLinks strips fragment identifiers`() {
        val html = """<a href="https://example.com/page#section">link</a>"""
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://example.com/page"), result)
    }

    @Test
    fun `extractLinks deduplicates links`() {
        val html =
            """
            <a href="https://example.com/page">one</a>
            <a href="https://example.com/page">two</a>
            """.trimIndent()
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://example.com/page"), result)
    }

    @Test
    fun `extractLinks filters non-http and non-https schemes`() {
        val html =
            """
            <a href="mailto:foo@example.com">mail</a>
            <a href="ftp://files.example.com">ftp</a>
            <a href="https://example.com/page">ok</a>
            """.trimIndent()
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://example.com/page"), result)
    }

    @Test
    fun `extractLinks normalizes root url to trailing slash`() {
        val html = """<a href="https://example.com">link</a>"""
        val result = helper.extractLinks(html, "https://base.com")
        assertEquals(listOf("https://example.com/"), result)
    }

    @Test
    fun `extractLinks strips trailing slash from non-root paths`() {
        val html = """<a href="https://example.com/page/">link</a>"""
        val result = helper.extractLinks(html, "https://example.com")
        assertEquals(listOf("https://example.com/page"), result)
    }
}
