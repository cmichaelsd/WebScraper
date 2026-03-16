package services

import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.webscraper.db.JobRepository
import org.webscraper.db.PageRepository
import org.webscraper.models.Job
import org.webscraper.models.Page
import org.webscraper.services.CrawlerService
import org.webscraper.services.JobService
import java.util.UUID
import kotlin.test.Test

class JobServiceTest {
    private val jobRepository = mockk<JobRepository>(relaxed = true)
    private val pageRepository = mockk<PageRepository>(relaxed = true)
    private val crawlerService = mockk<CrawlerService>(relaxed = true)

    private val service = JobService(jobRepository, pageRepository, crawlerService)

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    private fun job(
        seedUrls: List<String> = listOf("https://example.com"),
        maxDepth: Int = 2,
    ) = Job(UUID.randomUUID(), seedUrls, maxDepth)

    private fun page(
        url: String = "https://example.com",
        depth: Int = 0,
    ) = Page(UUID.randomUUID(), url, depth)

    @Test
    fun `worker processes job and marks complete when all pages succeed`() =
        runTest {
            val job = job()
            val page = page()
            val done = CompletableDeferred<Unit>()

            every { jobRepository.claimJob("worker-1") } returns job andThen null
            every { pageRepository.claimNextPage(job.id) } returns page andThen null
            every { pageRepository.hasFailedPages(job.id) } returns false
            every { jobRepository.completeJob(job.id, "worker-1") } answers { done.complete(Unit) }

            val worker = launch { service.worker("worker-1") }
            done.await()
            worker.cancel()

            verify { jobRepository.completeJob(job.id, "worker-1") }
        }

    @Test
    fun `worker seeds pages with job seed urls`() =
        runTest {
            val job = job(seedUrls = listOf("https://example.com", "https://other.com"))
            val done = CompletableDeferred<Unit>()

            every { jobRepository.claimJob("worker-1") } returns job andThen null
            every { pageRepository.claimNextPage(job.id) } returns null
            every { jobRepository.completeJob(any(), any()) } answers { done.complete(Unit) }

            val worker = launch { service.worker("worker-1") }
            done.await()
            worker.cancel()

            verify { pageRepository.seedPages(job.id, listOf("https://example.com", "https://other.com")) }
        }

    @Test
    fun `worker marks job failed when pages have failures`() =
        runTest {
            val job = job()
            val page = page()
            val done = CompletableDeferred<Unit>()

            every { jobRepository.claimJob("worker-1") } returns job andThen null
            every { pageRepository.claimNextPage(job.id) } returns page andThen null
            every { pageRepository.hasFailedPages(job.id) } returns true
            every { jobRepository.markFailed(job.id, "worker-1", any()) } answers {
                done.complete(Unit)
                1
            }

            val worker = launch { service.worker("worker-1") }
            done.await()
            worker.cancel()

            verify { jobRepository.markFailed(job.id, "worker-1", "One or more pages failed") }
        }

    @Test
    fun `worker marks page failed when crawl throws an exception`() =
        runTest {
            val job = job()
            val page = page()
            val done = CompletableDeferred<Unit>()

            every { jobRepository.claimJob("worker-1") } returns job andThen null
            every { pageRepository.claimNextPage(job.id) } returns page andThen null
            coEvery { crawlerService.crawlSingle(any(), any(), any()) } throws Exception("timeout")
            every { pageRepository.hasFailedPages(job.id) } returns true
            every { jobRepository.markFailed(job.id, "worker-1", any()) } answers {
                done.complete(Unit)
                1
            }

            val worker = launch { service.worker("worker-1") }
            done.await()
            worker.cancel()

            verify { pageRepository.markFailed(page.id, "timeout") }
        }

    @Test
    fun `worker delays and retries when no job is available`() =
        runTest {
            val claimed = CompletableDeferred<Unit>()
            every { jobRepository.claimJob("worker-1") } answers {
                claimed.complete(Unit)
                null
            }

            val worker = launch { service.worker("worker-1") }
            claimed.await()
            worker.cancel()

            verify { jobRepository.claimJob("worker-1") }
        }

    @Test
    fun `maintenance calls reclaimStaleJobs and reclaimStalePages`() =
        runTest {
            val done = CompletableDeferred<Unit>()
            every { pageRepository.reclaimStalePages() } answers { done.complete(Unit) }

            val maintenance = launch { service.maintenance() }
            done.await()
            maintenance.cancel()

            verify { jobRepository.reclaimStaleJobs() }
            verify { pageRepository.reclaimStalePages() }
        }
}
