package util

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.webscraper.services.RobotsService
import org.webscraper.util.DomainLimiter
import kotlin.test.Test

class DomainLimiterTest {
    private val robots = mockk<RobotsService>()

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttleDomain delays by default delay on sequential requests to same domain`() =
        runTest {
            coEvery { robots.getCrawlDelay(any()) } returns null
            val limiter = DomainLimiter(robots, clock = { currentTime })

            limiter.throttleDomain("https://example.com")
            limiter.throttleDomain("https://example.com")

            // each call waits 2000ms: 2000 (first) + 2000 (second) = 4000
            assertEquals(4000, currentTime)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttleDomain does not delay when sufficient time has elapsed between requests`() =
        runTest {
            coEvery { robots.getCrawlDelay(any()) } returns null
            val limiter = DomainLimiter(robots, clock = { currentTime })

            limiter.throttleDomain("https://example.com") // delays 2000ms, lastRequestTime = 2000
            advanceTimeBy(2000) // currentTime = 4000

            val before = currentTime
            limiter.throttleDomain("https://example.com") // elapsed = 4000 - 2000 = 2000, not < 2000 → no delay

            assertEquals(before, currentTime)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttleDomain uses robots crawl delay when available`() =
        runTest {
            coEvery { robots.getCrawlDelay(any()) } returns 5L
            val limiter = DomainLimiter(robots, clock = { currentTime })

            limiter.throttleDomain("https://example.com")

            assertEquals(5000, currentTime)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `throttleDomain throttles domains independently`() =
        runTest {
            coEvery { robots.getCrawlDelay(any()) } returns null
            val limiter = DomainLimiter(robots, clock = { currentTime })

            limiter.throttleDomain("https://example.com") // delays 2000ms, currentTime = 2000

            // other.com: elapsed = 2000 - 0 = 2000, not < 2000 → no delay
            val before = currentTime
            limiter.throttleDomain("https://other.com")

            assertEquals(before, currentTime)
        }

    @Test
    fun `throttleDomain returns immediately when url has no host`() =
        runTest {
            val limiter = DomainLimiter(robots, clock = { 0L })
            limiter.throttleDomain("not-a-url") // should not throw or delay
        }
}
