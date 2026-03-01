package db

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
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
    @Container
    val postgres = PostgreSQLContainer("postgres:16").apply {
        withDatabaseName("jobs")
        withUsername("jobs")
        withPassword("jobs")
    }

    private lateinit var dataSource: DataSource
    private lateinit var jobRepository: JobRepository

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
        dataSource.connection.use { conn ->
            conn.createStatement().execute("""
                INSERT INTO jobs (id, status, seed_urls, max_depth)
                VALUES (
                    gen_random_uuid(),
                    'PENDING',
                    '["https://example.com"]',
                    1
                )
            """.trimIndent())
        }

        val job = jobRepository.claimJob("worker-1")
        assertNotNull(job)

        dataSource.connection.use { conn ->
            val resultSet = conn.createStatement().executeQuery("SELECT status FROM jobs")
            resultSet.next()
            assertEquals(Status.RUNNING.name, resultSet.getString("status"))
        }
    }
}