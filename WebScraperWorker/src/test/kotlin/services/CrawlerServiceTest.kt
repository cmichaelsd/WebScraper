package services

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.example.services.CrawlerService
import org.example.services.RobotsService
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import kotlin.test.Test

class CrawlerServiceTest {
    @Test
    fun `returns empty list when robots disallows`() = runTest {
        val robots = mockk<RobotsService>()
        val client = mockk<HttpClient>()

        coEvery {
            robots.isAllowed(any())
        } returns false

        val service = CrawlerService(robots, client)
        val result = service.crawlSingle(
            "https://example.com",
            0,
            2
        )

        assertTrue(result.isEmpty())
    }

    @Test
    fun `returns extracted links as a list when robots allows`() = runTest {
        val robots = mockk<RobotsService>()
        val mockEngine = MockEngine {
            respond(
                content = """
                    <html>
                        <body>
                            <a href="https://www.google.com"/>
                            <a href="http://www.test.com"/>
                            <a href="https://www.abc.xyz"/>
                        </body>
                    </html>
                """.trimIndent(),
                status = HttpStatusCode.OK,
                headers = headersOf("Content-Type" to listOf("text/html"))
            )
        }

        val client = HttpClient(mockEngine)

        coEvery {
            robots.isAllowed(any())
        } returns true

        coEvery {
            robots.getCrawlDelay(any())
        } returns null

        val service = CrawlerService(robots, client)
        val result = service.crawlSingle(
            "https://example.com",
            0,
            2
        )

        assertEquals(
            listOf(
                "https://www.google.com",
                "http://www.test.com",
                "https://www.abc.xyz"
            ),
            result
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttles domain globally when headers contain crawler delay`() = runTest {
        val robotsService = mockk<RobotsService>()

        coEvery {
            robotsService.isAllowed(any())
        } returns true

        coEvery {
            robotsService.getCrawlDelay(any())
        } returns 5

        val mockEngine = MockEngine {
            respond(
                content = "<html></htlm>",
                status = HttpStatusCode.OK
            )
        }

        val client = HttpClient(mockEngine)
        val service = CrawlerService(
            robotsService,
            client,
            clock = { currentTime }
        )

        // First request should have no delay
        service.crawlSingle(
            "https://example.com",
            0,
            2
        )

        val timeAfterFirst = currentTime

        // Second request should delay 5000ms
        service.crawlSingle(
            "https://example.com",
            0,
            2
        )

        val timeAfterSecond = currentTime

        val elapsed = timeAfterSecond - timeAfterFirst

        assertEquals(5000, elapsed)
    }

}