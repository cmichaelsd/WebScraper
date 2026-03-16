package services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.webscraper.services.RobotsService
import org.webscraper.util.RulesCache
import kotlin.test.Test

class RobotsServiceTest {
    private fun clientWith(
        content: String,
        status: HttpStatusCode = HttpStatusCode.OK,
    ): HttpClient =
        HttpClient(
            MockEngine {
                respond(
                    content = content,
                    status = status,
                    headers = headersOf("Content-Type" to listOf("text/plain")),
                )
            },
        )

    private fun service(client: HttpClient) = RobotsService(client, RulesCache())

    @Test
    fun `isAllowed returns true when disallow list is empty`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: *
                    Disallow:
                    """.trimIndent(),
                )

            assertTrue(service(client).isAllowed("https://example.com/page"))
        }

    @Test
    fun `isAllowed returns false when path matches disallow rule`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: *
                    Disallow: /admin
                    """.trimIndent(),
                )

            assertFalse(service(client).isAllowed("https://example.com/admin"))
        }

    @Test
    fun `isAllowed returns true when path does not match any disallow rule`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: *
                    Disallow: /admin
                    """.trimIndent(),
                )

            assertTrue(service(client).isAllowed("https://example.com/page"))
        }

    @Test
    fun `isAllowed returns true when robots fetch throws an exception`() =
        runTest {
            val client = HttpClient(MockEngine { throw Exception("Connection refused") })

            assertTrue(service(client).isAllowed("https://example.com/page"))
        }

    @Test
    fun `isAllowed returns true when url has no host`() =
        runTest {
            assertTrue(service(clientWith("")).isAllowed("not-a-url"))
        }

    @Test
    fun `isAllowed ignores rules for other user-agents`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: Googlebot
                    Disallow: /secret

                    User-agent: *
                    Disallow: /admin
                    """.trimIndent(),
                )

            // /secret is only disallowed for Googlebot, not for us
            assertTrue(service(client).isAllowed("https://example.com/secret"))
            assertFalse(service(client).isAllowed("https://example.com/admin"))
        }

    @Test
    fun `isAllowed uses cached rules and makes only one http request`() =
        runTest {
            var requestCount = 0
            val client =
                HttpClient(
                    MockEngine {
                        requestCount++
                        respond(
                            content =
                                """
                                User-agent: *
                                Disallow: /admin
                                """.trimIndent(),
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf("text/plain")),
                        )
                    },
                )

            val svc = service(client)
            svc.isAllowed("https://example.com/page")
            svc.isAllowed("https://example.com/other")

            assertEquals(1, requestCount)
        }

    @Test
    fun `getCrawlDelay returns delay when crawl-delay is present`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: *
                    Disallow:
                    Crawl-delay: 5
                    """.trimIndent(),
                )

            assertEquals(5, service(client).getCrawlDelay("https://example.com"))
        }

    @Test
    fun `getCrawlDelay returns null when crawl-delay is absent`() =
        runTest {
            val client =
                clientWith(
                    """
                    User-agent: *
                    Disallow:
                    """.trimIndent(),
                )

            assertNull(service(client).getCrawlDelay("https://example.com"))
        }

    @Test
    fun `getCrawlDelay returns null when url has no host`() =
        runTest {
            assertNull(service(clientWith("")).getCrawlDelay("not-a-url"))
        }
}
