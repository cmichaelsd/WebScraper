package services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.test.KoinTest
import org.webscraper.services.RobotsService
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RobotsServiceTest : KoinTest {
    @Test
    fun `allows url not blocked by robots`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            User-agent: *
                            Disallow:
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("text/plain")),
                    )
                }

            val client = HttpClient(mockEngine)
            val service = RobotsService(client)
            val result = service.isAllowed("https://example.com/page")

            assertTrue(result)
        }

    @Test
    fun `disallows url blocked by robots`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            User-agent: *
                            Disallow: /admin
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("text/plain")),
                    )
                }

            val client = HttpClient(mockEngine)
            val service = RobotsService(client)
            val result = service.isAllowed("https://example.com/admin")

            assertFalse(result)
        }

    @Test
    fun `returns delay amount when crawler delay exists in robots`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            User-agent: *
                            Disallow:
                            Crawl-delay: 5
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("text/plain")),
                    )
                }

            val client = HttpClient(mockEngine)
            val service = RobotsService(client)
            val result = service.getCrawlDelay("https://example.com/admin")

            assertEquals(5, result)
        }

    @Test
    fun `returns null when crawler delay does not exist in robots`() =
        runTest {
            val mockEngine =
                MockEngine {
                    respond(
                        content =
                            """
                            User-agent: *
                            Disallow:
                            """.trimIndent(),
                        status = HttpStatusCode.OK,
                        headers = headersOf("Content-Type" to listOf("text/plain")),
                    )
                }

            val client = HttpClient(mockEngine)
            val service = RobotsService(client)
            val result = service.getCrawlDelay("https://example.com/admin")

            assertNull(result)
        }
}
