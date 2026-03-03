package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import org.junit.jupiter.api.Assertions.*
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
import java.util.UUID
import javax.sql.DataSource
import kotlin.test.Test

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PageRepositoryTest {
    companion object {
        private const val DB_SCHEMA_PATH = "src/test/resources/schema.sql"
        private const val WORKER_ID = "worker-1"
        private const val OTHER_WORKER_ID = "worker-2"
        private const val RESULT_SET_STATUS_KEY = "status"

        private val validUrls = listOf(
            "https://example.com",
            "http://abc.xyz",
            "https://google.com"
        )

        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16").apply {
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
        val cleanedSchema = rawSchema.lines()
            .filterNot { it.startsWith("\\") }
            .joinToString("\n")

        postgres.createConnection("").use { conn ->
            conn.createStatement().use { statement ->
                statement.execute(cleanedSchema)
            }
        }

        val config = HikariConfig().apply {
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
    fun `seedPages inserts new pages into pages table`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages")
            resultSet.next()

            // Get value of first returned row.
            assertEquals(3, resultSet.getInt(1))
        }
    }

    @Test
    fun `claimNextPage transitions statues PENDING to RUNNING when job id matches`() {
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
    fun `insertDiscovered does not add urls into pages when urls have been seen by current job`() {
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
    fun `insertDiscovered add urls into pages when urls have not been seen by current job`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.insertDiscovered(
            job.id,
            listOf("https://youtube.com", "http://test.com"),
            1
        )

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT COUNT(*) FROM pages")
            resultSet.next()
            assertEquals(5, resultSet.getInt(1))
        }
    }

    @Test
    fun `markComplete transitions status RUNNING to COMPLETE`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        val page = pageRepository.claimNextPage(job.id)!!
        val result = pageRepository.markCompleted(page.id)
        assertTrue(result)
    }

    @Test
    fun `markFailed transitions status RUNNING to FAILED`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        val page = pageRepository.claimNextPage(job.id)!!
        val result = pageRepository.markFailed(page.id, null)
        assertTrue(result)
    }

    @Test
    fun `hasUnfinishedPages returns true when a job has pages of status PENDING or RUNNING`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, validUrls)
        pageRepository.claimNextPage(job.id)!!
        val result = pageRepository.hasUnfinishedPages(job.id)
        assertTrue(result)
    }

    @Test
    fun `hasUnfinishedPages returns false when a job has no pages of status PENDING or RUNNING`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        val page = pageRepository.claimNextPage(job.id)!!
        pageRepository.markFailed(page.id, null)

        val result = pageRepository.hasUnfinishedPages(job.id)
        assertFalse(result)
    }

    @Test
    fun `hasFailedPages returns true when a job has pages of status FAILED`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        val page = pageRepository.claimNextPage(job.id)!!
        pageRepository.markFailed(page.id, null)

        val result = pageRepository.hasFailedPages(job.id)
        assertTrue(result)
    }

    @Test
    fun `hasFailedPages returns false when a job has no pages of status FAILED`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.claimNextPage(job.id)!!

        val result = pageRepository.hasFailedPages(job.id)
        assertFalse(result)
    }

    @Test
    fun `reclaimStalePages transitions RUNNING to PENDING when created_at is 30 seconds from current time`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        pageRepository.seedPages(job.id, listOf("https://example.com"))
        pageRepository.claimNextPage(job.id)
        dataSource.connection.use { conn ->
            conn.createStatement().executeUpdate("""
                UPDATE pages
                SET created_at = NOW() - INTERVAL '31 seconds'
            """.trimIndent())
        }

        pageRepository.reclaimStalePages()
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM pages")
            resultSet.next()
            assertEquals(Status.PENDING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `reclaimStalePages does nothing when created_at is less than 30 seconds from now`() {
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
            conn.createStatement().execute("""
                INSERT INTO jobs (id, status, seed_urls, max_depth)
                VALUES (
                    gen_random_uuid(),
                    '${Status.PENDING.name}',
                    '["https://example.com"]',
                    1
                )
            """.trimIndent())
        }
    }
}