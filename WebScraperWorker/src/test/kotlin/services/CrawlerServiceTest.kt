package services

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.mockk.Runs
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.webscraper.services.CrawlerService
import org.webscraper.services.RobotsService
import org.webscraper.util.DomainLimiter
import org.webscraper.util.UrlHelper
import kotlin.test.Test

class CrawlerServiceTest {
    private val robots = mockk<RobotsService>()
    private val domainLimiter = mockk<DomainLimiter>()
    private val urlHelper = mockk<UrlHelper>()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun service(client: HttpClient) = CrawlerService(robots, client, domainLimiter, urlHelper)

    @Test
    fun `crawlSingle returns empty list when depth exceeds maxDepth`() =
        runTest {
            val result = service(mockk()).crawlSingle("https://example.com", depth = 3, maxDepth = 2)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `crawlSingle returns empty list when robots disallows`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns false

            val result = service(mockk()).crawlSingle("https://example.com", 0, 2)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `crawlSingle returns extracted links on successful response`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs
            every { urlHelper.extractLinks(any(), any()) } returns
                listOf(
                    "https://www.google.com/",
                    "https://www.example.com/page",
                )

            val client =
                HttpClient(
                    MockEngine {
                        respond(
                            content = "<html><body><a href='https://www.google.com/'/></body></html>",
                            status = HttpStatusCode.OK,
                            headers = headersOf("Content-Type" to listOf("text/html")),
                        )
                    },
                )

            val result = service(client).crawlSingle("https://example.com", 0, 2)

            assertEquals(listOf("https://www.google.com/", "https://www.example.com/page"), result)
        }

    @Test
    fun `crawlSingle calls throttleDomain before fetching`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs
            every { urlHelper.extractLinks(any(), any()) } returns emptyList()

            val client =
                HttpClient(
                    MockEngine {
                        respond(content = "<html></html>", status = HttpStatusCode.OK)
                    },
                )

            service(client).crawlSingle("https://example.com", 0, 2)

            coVerify(exactly = 1) { domainLimiter.throttleDomain("https://example.com") }
        }

    @Test
    fun `crawlSingle returns empty list after exhausting retries on 429`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs

            val client =
                HttpClient(
                    MockEngine {
                        respond(content = "", status = HttpStatusCode.TooManyRequests)
                    },
                )

            val result = service(client).crawlSingle("https://example.com", 0, 2)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `crawlSingle returns empty list after exhausting retries on 503`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs

            val client =
                HttpClient(
                    MockEngine {
                        respond(content = "", status = HttpStatusCode.ServiceUnavailable)
                    },
                )

            val result = service(client).crawlSingle("https://example.com", 0, 2)
            assertTrue(result.isEmpty())
        }

    @Test
    fun `crawlSingle retries after 429 with Retry-After header`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs
            every { urlHelper.extractLinks(any(), any()) } returns emptyList()

            var callCount = 0
            val client =
                HttpClient(
                    MockEngine {
                        callCount++
                        if (callCount == 1) {
                            respond(
                                content = "",
                                status = HttpStatusCode.TooManyRequests,
                                headers = headersOf("Retry-After" to listOf("10")),
                            )
                        } else {
                            respond(content = "<html></html>", status = HttpStatusCode.OK)
                        }
                    },
                )

            service(client).crawlSingle("https://example.com", 0, 2)

            assertEquals(2, callCount)
        }

    @Test
    fun `crawlSingle returns empty list after exhausting retries on repeated exceptions`() =
        runTest {
            coEvery { robots.isAllowed(any()) } returns true
            coEvery { domainLimiter.throttleDomain(any()) } just Runs

            val client =
                HttpClient(
                    MockEngine {
                        throw Exception("Connection refused")
                    },
                )

            val result = service(client).crawlSingle("https://example.com", 0, 2)
            assertTrue(result.isEmpty())
        }
}
