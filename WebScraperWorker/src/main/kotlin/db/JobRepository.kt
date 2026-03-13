package org.webscraper.db

import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import org.webscraper.models.Job
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class JobRepository(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(JobRepository::class.java)

    private inline fun <T> withConn(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun claimJob(workerId: String): Job? =
        withConn { conn ->
            conn.autoCommit = false

            val sql =
                """
                UPDATE jobs
                SET status = ?,
                    claimed_by = ?,
                    claimed_at = NOW(),
                    heartbeat_at = NOW(),
                    updated_at = NOW()
                WHERE id = (
                    SELECT id FROM jobs
                    WHERE status = ?
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, seed_urls, max_depth
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.RUNNING.name)
                it.setString(2, workerId)
                it.setString(3, Status.PENDING.name)

                val resultSet = it.executeQuery()

                if (resultSet.next()) {
                    val seedUrlsJson = resultSet.getString("seed_urls")
                    val job =
                        Job(
                            id = resultSet.getObject("id", UUID::class.java),
                            seedUrls = Json.decodeFromString(seedUrlsJson),
                            maxDepth = resultSet.getInt("max_depth"),
                        )

                    conn.commit()
                    return job
                } else {
                    conn.rollback()
                    return null
                }
            }
        }

    fun completeJob(
        jobId: UUID,
        workerId: String,
    ) = withConn { conn ->
        val sql =
            """
            UPDATE jobs
            SET status = ?,
                updated_at = NOW(),
                completed_at = NOW()
            WHERE id = ?
                AND claimed_by = ?
                AND status = ?
            """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setString(1, Status.COMPLETED.name)
            it.setObject(2, jobId)
            it.setString(3, workerId)
            it.setString(4, Status.RUNNING.name)
            val updated = it.executeUpdate()
            if (updated == 0) {
                logger.warn("completeJob: No rows updated – job $jobId not owned by $workerId")
            }
        }
    }

    fun markFailed(
        jobId: UUID,
        workerId: String,
        error: String?,
    ) = withConn { conn ->
        val sql =
            """
            UPDATE jobs
            SET status = CASE
                    WHEN attempt_count + 1 >= 3 THEN ?
                    ELSE ?
                END,
                attempt_count = attempt_count + 1,
                last_error = ?,
                updated_at = NOW()
            WHERE id = ? AND claimed_by = ?
            """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setString(1, Status.FAILED.name)
            it.setString(2, Status.PENDING.name)
            it.setString(3, error)
            it.setObject(4, jobId)
            it.setString(5, workerId)
            it.executeUpdate()
        }
    }

    fun reclaimStaleJobs() =
        withConn { conn ->
            val sql =
                """
                UPDATE jobs
                SET status = ?,
                    claimed_by = NULL,
                    claimed_at = NULL,
                    heartbeat_at = NULL,
                    updated_at = NOW()
                WHERE status = ?
                    AND heartbeat_at < NOW() - INTERVAL '30 seconds'
                    AND attempt_count < 3
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.PENDING.name)
                it.setString(2, Status.RUNNING.name)
                val updated = it.executeUpdate()
                if (updated > 0) {
                    logger.info("reclaimStaleJobs: Reclaimed $updated stale job(s)")
                }
            }
        }

    fun updateHeartbeat(
        jobId: UUID,
        workerId: String,
    ): Boolean =
        withConn { conn ->
            val sql =
                """
                UPDATE jobs
                SET heartbeat_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND claimed_by = ?
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                it.setString(2, workerId)
                return it.executeUpdate() == 1
            }
        }
}
