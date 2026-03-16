package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.webscraper.db.JobRepository
import org.webscraper.db.PageRepository
import org.webscraper.db.Status
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PageRepositoryTest {
    companion object {
        private const val DB_SCHEMA_PATH = "src/test/resources/schema.sql"
        private const val WORKER_ID = "worker-1"
        private const val RESULT_SET_STATUS_KEY = "status"

        private val validUrls =
            listOf(
                "https://example.com",
                "http://abc.xyz",
                "https://google.com",
            )

        @Container
        @JvmStatic
        val postgres =
            PostgreSQLContainer("postgres:16").apply {
                withDatabaseName("jobs")
                withUsername("jobs")
                withPassword("jobs")
            }
    }

    private lateinit var dataSource: DataSource
    private lateinit var jobRepository: JobRepository
    private lateinit var pageRepository: PageRepository

    @BeforeAll
    fun setup() {
        val rawSchema = File(DB_SCHEMA_PATH).readText()
        val cleanedSchema =
            rawSchema.lines()
                .filterNot { it.startsWith("\\") }
                .joinToString("\n")

        postgres.createConnection("").use { conn ->
            conn.createStatement().use { statement ->
                statement.execute(cleanedSchema)
            }
        }

        val config =
            HikariConfig().apply {
                jdbcUrl = postgres.jdbcUrl
                username = postgres.username
                password = postgres.password
                maximumPoolSize = 2
            }

        dataSource = HikariDataSource(config)
        jobRepository = JobRepository(dataSource)
        pageRepository = PageRepository(dataSource)
    }

    @BeforeEach
    fun clean() {
        dataSource.connection.use { conn ->
            conn.createStatement().execute("TRUNCATE jobs CASCADE")
        }
    }

    @Test
    fun `seedPages inserts seed URLs into pages table at depth 0`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages WHERE depth = 0")
            resultSet.next()
            assertEquals(3, resultSet.getInt(1))
        }
    }

    @Test
    fun `seedPages does not insert duplicate URLs`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.seedPages(job.id, validUrls)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages")
            resultSet.next()
            assertEquals(3, resultSet.getInt(1))
        }
    }

    @Test
    fun `claimNextPage transitions status from PENDING to RUNNING`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.claimNextPage(job.id)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages WHERE status = '${Status.RUNNING.name}'")
            resultSet.next()
            assertEquals(1, resultSet.getInt(1))
        }
    }

    @Test
    fun `claimNextPage returns the page with URL and depth`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))

        val page = pageRepository.claimNextPage(job.id)!!
        assertNotNull(page.id)
        assertEquals("https://example.com", page.url)
        assertEquals(0, page.depth)
    }

    @Test
    fun `claimNextPage returns null when no PENDING pages exist`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!

        val page = pageRepository.claimNextPage(job.id)
        assertNull(page)
    }

    @Test
    fun `claimNextPage returns pages in BFS order (lower depth first)`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.insertDiscovered(job.id, listOf("https://example.com/deep"), 1)

        // Claim and complete the depth-0 page so it's out of the way
        val firstPage = pageRepository.claimNextPage(job.id)!!
        assertEquals(0, firstPage.depth)
        pageRepository.markCompleted(firstPage.id)

        val secondPage = pageRepository.claimNextPage(job.id)!!
        assertEquals(1, secondPage.depth)
    }

    @Test
    fun `claimNextPage only claims pages for the specified job`() {
        seedJobRequest()
        seedJobRequest()
        val job1 = jobRepository.claimJob(WORKER_ID)!!
        val job2 = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job1.id, listOf("https://job1.com"))
        pageRepository.seedPages(job2.id, listOf("https://job2.com"))

        val page = pageRepository.claimNextPage(job1.id)!!
        assertEquals("https://job1.com", page.url)
    }

    @Test
    fun `insertDiscovered does not add URLs that are already in the job`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.insertDiscovered(job.id, validUrls, 1)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages")
            resultSet.next()
            assertEquals(3, resultSet.getInt(1))
        }
    }

    @Test
    fun `insertDiscovered adds new URLs that have not been seen by the job`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.insertDiscovered(
            job.id,
            listOf("https://youtube.com", "http://test.com"),
            1,
        )

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages")
            resultSet.next()
            assertEquals(5, resultSet.getInt(1))
        }
    }

    @Test
    fun `insertDiscovered stores pages at the given depth`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.insertDiscovered(job.id, listOf("https://example.com/page"), 2)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT depth FROM pages WHERE url = 'https://example.com/page'")
            resultSet.next()
            assertEquals(2, resultSet.getInt("depth"))
        }
    }

    @Test
    fun `markCompleted transitions status from RUNNING to COMPLETED`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        val page = pageRepository.claimNextPage(job.id)!!

        val result = pageRepository.markCompleted(page.id)

        assertTrue(result)
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM pages WHERE id = '${page.id}'")
            resultSet.next()
            assertEquals(Status.COMPLETED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markCompleted returns false when page does not exist`() {
        val result = pageRepository.markCompleted(java.util.UUID.randomUUID())
        assertFalse(result)
    }

    @Test
    fun `markFailed transitions status from RUNNING to FAILED`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        val page = pageRepository.claimNextPage(job.id)!!

        val result = pageRepository.markFailed(page.id, null)

        assertTrue(result)
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM pages WHERE id = '${page.id}'")
            resultSet.next()
            assertEquals(Status.FAILED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markFailed stores the error message`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        val page = pageRepository.claimNextPage(job.id)!!

        pageRepository.markFailed(page.id, "connection refused")

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT error FROM pages WHERE id = '${page.id}'")
            resultSet.next()
            assertEquals("connection refused", resultSet.getString("error"))
        }
    }

    @Test
    fun `markFailed returns false when page does not exist`() {
        val result = pageRepository.markFailed(java.util.UUID.randomUUID(), null)
        assertFalse(result)
    }

    @Test
    fun `hasUnfinishedPages returns true when a job has PENDING or RUNNING pages`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.claimNextPage(job.id)!!

        val result = pageRepository.hasUnfinishedPages(job.id)
        assertTrue(result)
    }

    @Test
    fun `hasUnfinishedPages returns false when a job has no PENDING or RUNNING pages`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        val page = pageRepository.claimNextPage(job.id)!!
        pageRepository.markFailed(page.id, null)

        val result = pageRepository.hasUnfinishedPages(job.id)
        assertFalse(result)
    }

    @Test
    fun `hasUnfinishedPages returns false when a job has no pages`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!

        val result = pageRepository.hasUnfinishedPages(job.id)
        assertFalse(result)
    }

    @Test
    fun `hasFailedPages returns true when a job has FAILED pages`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        val page = pageRepository.claimNextPage(job.id)!!
        pageRepository.markFailed(page.id, null)

        val result = pageRepository.hasFailedPages(job.id)
        assertTrue(result)
    }

    @Test
    fun `hasFailedPages returns false when a job has no FAILED pages`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.claimNextPage(job.id)!!

        val result = pageRepository.hasFailedPages(job.id)
        assertFalse(result)
    }

    @Test
    fun `reclaimStalePages transitions RUNNING to PENDING when claimed_at is older than 30 seconds`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.claimNextPage(job.id)
        dataSource.connection.use { conn ->
            conn.createStatement().executeUpdate(
                """
                UPDATE pages
                SET claimed_at = NOW() - INTERVAL '31 seconds'
                """.trimIndent(),
            )
        }

        pageRepository.reclaimStalePages()
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM pages")
            resultSet.next()
            assertEquals(Status.PENDING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `reclaimStalePages does nothing when claimed_at is less than 30 seconds ago`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.claimNextPage(job.id)

        pageRepository.reclaimStalePages()
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM pages WHERE status = '${Status.RUNNING.name}'")
            resultSet.next()
            assertEquals(Status.RUNNING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    private fun seedJobRequest() {
        dataSource.connection.use { conn ->
            conn.createStatement().execute(
                """
                INSERT INTO jobs (id, status, seed_urls, max_depth)
                VALUES (
                    gen_random_uuid(),
                    '${Status.PENDING.name}',
                    '["https://example.com"]',
                    1
                )
                """.trimIndent(),
            )
        }
    }
}
