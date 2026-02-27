package services

import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.example.services.RobotsService
import org.example.util.interfaces.RobotsFetcher
import org.junit.jupiter.api.Assertions.*
import kotlin.test.Test

class RobotsServiceTest {
    @Test
    fun `allows url not blocked by robots`() = runTest {
        val fetcher = mockk<RobotsFetcher>()

        coEvery {
            fetcher.fetchRobots("example.com")
        } returns """
            User-agent: *
            Disallow:
        """.trimIndent()

        val service = RobotsService(fetcher)

        val result = service.isAllowed("https://example.com/page")

        assertTrue(result)
    }

    @Test
    fun `blocks url disallowed by robots`() = runTest {
        val fetcher = mockk<RobotsFetcher>()

        coEvery {
            fetcher.fetchRobots("example.com")
        } returns """
            User-agent: *
            Disallow: /admin
        """.trimIndent()

        val service = RobotsService(fetcher)

        val result = service.isAllowed("https://example.com/admin")

        assertFalse(result)
    }

}