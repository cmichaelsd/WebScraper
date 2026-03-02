package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.example.db.JobRepository
import org.example.db.Status
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.TestInstance
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.io.File
import javax.sql.DataSource
import kotlin.test.Test

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobRepositoryTest {
    companion object {
        private const val WORKER_ID = "worker-1"
        private const val RESULT_SET_STATUS_KEY = "status"
        private const val RESULT_SET_ATTEMPT_COUNT_KEY = "attempt_count"
    }

    private lateinit var dataSource: DataSource
    private lateinit var jobRepository: JobRepository

    @Container
    val postgres = PostgreSQLContainer("postgres:16").apply {
        withDatabaseName("jobs")
        withUsername("jobs")
        withPassword("jobs")
    }

    @BeforeAll
    fun setup() {
        postgres.start()

        val rawSchema = File("src/test/resources/schema.sql").readText()
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
    }

    @Test
    fun `claimJob transitions PENDING to RUNNING`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)
        assertNotNull(job)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM jobs")
            resultSet.next()
            assertEquals(Status.RUNNING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `completeJob transitions RUNNING to COMPLETE`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)
        assertNotNull(job)

        jobRepository.completeJob(job!!.id, WORKER_ID)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM jobs")
            resultSet.next()
            assertEquals(Status.COMPLETED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markFailed transitions RUNNING to PENDING when jobs attempt count is less than 3`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)
        assertNotNull(job)

        jobRepository.markFailed(job!!.id, WORKER_ID, null)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM jobs")
            resultSet.next()
            assertEquals(Status.PENDING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markFailed transitions RUNNING to FAILED when jobs attempt count is greater than or equal to 3`() {
        seedJobRequest()

        repeat(3) {
            val job = jobRepository.claimJob(WORKER_ID)!!
            jobRepository.markFailed(job.id, WORKER_ID, null)
        }

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT attempt_count, status FROM jobs")
            resultSet.next()
            assertEquals(Status.FAILED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
            assertEquals(3, resultSet.getInt(RESULT_SET_ATTEMPT_COUNT_KEY))
        }
    }

    @Test
    fun `reclaimStaleJobs transitions RUNNING to PENDING when job has not been updated for 30 seconds`() {
        seedJobRequest()

        jobRepository.claimJob(WORKER_ID)
        dataSource.connection.use { conn ->
            conn.createStatement().executeUpdate("""
                UPDATE jobs
                SET heartbeat_at = NOW() - INTERVAL '31 seconds'
            """.trimIndent())
        }

        jobRepository.reclaimStaleJobs()
        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM jobs")
            resultSet.next()
            assertEquals(Status.PENDING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `updateHeartbeat updates heartbeat_at when worker owns job`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        val wasUpdated = jobRepository.updateHeartbeat(job.id, WORKER_ID)
        assertTrue(wasUpdated)
    }

    @Test
    fun `updateHeartbeat does not update heartbeat_at when worker does not own job`() {
        seedJobRequest()
        val job = jobRepository.claimJob(WORKER_ID)!!
        val wasUpdated = jobRepository.updateHeartbeat(job.id, "OTHER_WORKER_ID")
        assertFalse(wasUpdated)
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