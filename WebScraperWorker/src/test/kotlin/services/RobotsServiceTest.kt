package services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.test.runTest
import org.example.services.RobotsService
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.test.KoinTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RobotsServiceTest : KoinTest {
    @Test
    fun `allows url not blocked by robots`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    User-agent: *
                    Disallow:
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val client = HttpClient(mockEngine)
        val service = RobotsService(client)
        val result = service.isAllowed("https://example.com/page")

        assertTrue(result)
    }

    @Test
    fun `disallows url blocked by robots`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    User-agent: *
                    Disallow: /admin
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val client = HttpClient(mockEngine)
        val service = RobotsService(client)
        val result = service.isAllowed("https://example.com/admin")

        assertFalse(result)
    }

    @Test
    fun `returns delay amount when crawler delay exists in robots`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    User-agent: *
                    Disallow:
                    Crawl-delay: 5
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val client = HttpClient(mockEngine)
        val service = RobotsService(client)
        val result = service.getCrawlDelay("https://example.com/admin")

        assertEquals(5, result)
    }

    @Test
    fun `returns null when crawler delay does not exist in robots`() = runTest {
        val mockEngine = MockEngine {
            respond(
                content = """
                    User-agent: *
                    Disallow:
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/plain"))
            )
        }

        val client = HttpClient(mockEngine)
        val service = RobotsService(client)
        val result = service.getCrawlDelay("https://example.com/admin")

        assertNull(result)
    }
}