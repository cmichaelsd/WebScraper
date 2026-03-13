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
import org.webscraper.db.Status
import java.io.File
import java.sql.Connection
import java.sql.ResultSet
import javax.sql.DataSource
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class JobRepositoryTest {
    companion object {
        private const val DB_SCHEMA_PATH = "src/test/resources/schema.sql"
        private const val WORKER_ID = "worker-1"
        private const val OTHER_WORKER_ID = "worker-2"
        private const val RESULT_SET_STATUS_KEY = "status"
        private const val RESULT_SET_ATTEMPT_COUNT_KEY = "attempt_count"
        private const val SELECT_STATUS_FROM_JOBS = "SELECT status FROM jobs"
        private const val SELECT_STATUS_AND_ATTEMPT_COUNT_FROM_JOBS = "SELECT attempt_count, status FROM jobs"

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
    }

    @BeforeEach
    fun clean() {
        dataSource.connection.use { conn ->
            conn.createStatement().execute("TRUNCATE jobs CASCADE")
        }
    }

    @Test
    fun `claimJob transitions PENDING to RUNNING`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)
        assertNotNull(job)

        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_FROM_JOBS)
            assertEquals(Status.RUNNING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `completeJob transitions RUNNING to COMPLETE when job is owned by worker`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!

        jobRepository.completeJob(job.id, WORKER_ID)

        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_FROM_JOBS)
            assertEquals(Status.COMPLETED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `completeJob does nothing when job is not owned by worker`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!

        jobRepository.completeJob(job.id, OTHER_WORKER_ID)

        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_FROM_JOBS)
            assertEquals(Status.RUNNING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markFailed transitions RUNNING to PENDING when jobs attempt count is less than 3`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)

        jobRepository.markFailed(job!!.id, WORKER_ID, null)

        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_FROM_JOBS)
            assertEquals(Status.PENDING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
        }
    }

    @Test
    fun `markFailed does nothing when job is not currently claimed by a different worker`() {
        seedJobRequest()

        val job = jobRepository.claimJob(WORKER_ID)!!
        jobRepository.markFailed(job.id, OTHER_WORKER_ID, null)

        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_AND_ATTEMPT_COUNT_FROM_JOBS)
            assertEquals(Status.RUNNING.name, resultSet.getString(RESULT_SET_STATUS_KEY))
            assertEquals(0, resultSet.getInt(RESULT_SET_ATTEMPT_COUNT_KEY))
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
            val resultSet = getItemFromDB(conn, SELECT_STATUS_AND_ATTEMPT_COUNT_FROM_JOBS)
            assertEquals(Status.FAILED.name, resultSet.getString(RESULT_SET_STATUS_KEY))
            assertEquals(3, resultSet.getInt(RESULT_SET_ATTEMPT_COUNT_KEY))
        }
    }

    @Test
    fun `reclaimStaleJobs transitions RUNNING to PENDING when job has not been updated for 30 seconds`() {
        seedJobRequest()

        jobRepository.claimJob(WORKER_ID)
        dataSource.connection.use { conn ->
            conn.createStatement().executeUpdate(
                """
                UPDATE jobs
                SET heartbeat_at = NOW() - INTERVAL '31 seconds'
                """.trimIndent(),
            )
        }

        jobRepository.reclaimStaleJobs()
        dataSource.connection.use { conn ->
            val resultSet = getItemFromDB(conn, SELECT_STATUS_FROM_JOBS)
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
        val wasUpdated = jobRepository.updateHeartbeat(job.id, OTHER_WORKER_ID)
        assertFalse(wasUpdated)
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

    private fun getItemFromDB(
        conn: Connection,
        query: String,
    ): ResultSet {
        val resultSet = conn.createStatement().executeQuery(query)
        resultSet.next()
        return resultSet
    }
}
