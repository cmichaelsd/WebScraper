package org.example.db

import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import org.example.models.Job
import org.example.services.CrawlerService
import org.koin.java.KoinJavaComponent.getKoin
import java.util.*
import javax.sql.DataSource

class JobRepository(private val dataSource: DataSource) {
    fun claimJob(workerId: String): Job? {
        dataSource.connection.use { conn ->
            conn.autoCommit = false

            val sql = """
                UPDATE jobs
                SET status = '${Status.RUNNING.name}',
                    claimed_by = ?,
                    claimed_at = NOW(),
                    heartbeat_at = NOW(),
                    updated_at = NOW()
                WHERE id = (
                    SELECT id FROM jobs
                    WHERE status = '${Status.PENDING.name}'
                    ORDER BY created_at
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, seed_urls, max_depth
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, workerId)

                val resultSet = it.executeQuery()

                return if (resultSet.next()) {
                    conn.commit()

                    val seedUrlsJson = resultSet.getString("seed_urls")

                    Job(
                        id = resultSet.getObject("id", UUID::class.java),
                        seedUrls = Json.decodeFromString(seedUrlsJson),
                        maxDepth = resultSet.getInt("max_depth")
                    )
                } else {
                    conn.rollback()
                    null
                }
            }
        }
    }

    suspend fun processJob(
        job: Job,
        workerId: String
    ) {
        coroutineScope {
            val crawler: CrawlerService = getKoin().get()

            PageRepository.seedPages(job.id, job.seedUrls)

            val heartbeatJob = launch {
                while (isActive) {
                    updateHeartbeat(job.id, workerId)
                    delay(5_000)
                }
            }

            try {
                while (true) {
                    val page = PageRepository.claimNextPage(job.id) ?: break
                    try {
                        val discoveredUrls = crawler.crawlSingle(page.url, page.depth, job.maxDepth)
                        PageRepository.insertDiscovered(job.id, discoveredUrls, page.depth + 1)
                        PageRepository.markCompleted(page.id)
                    } catch (e: Exception) {
                        PageRepository.markFailed(page.id, e.stackTraceToString())
                    }
                }

                if (!PageRepository.hasUnfinishedPages(job.id)) {
                    if (PageRepository.hasFailedPages(job.id)) {
                        markFailed(job.id, workerId, "One or more pages failed")
                    } else {
                        completeJob(job.id, workerId)
                    }
                }
            } catch (e: Exception) {
                markFailed(job.id, workerId, e.message)
            } finally {
                heartbeatJob.cancelAndJoin()
            }
        }
    }

    fun completeJob(jobId: UUID, workerId: String) {
        dataSource.connection.use { conn ->
            val sql = """
                UPDATE jobs
                SET status = '${Status.COMPLETED.name}',
                    updated_at = NOW(),
                    completed_at = NOW()
                WHERE id = ? AND claimed_by = ?
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                it.setString(2, workerId)
                val updated = it.executeUpdate()
                if (updated == 0) {
                    println("Job no longer owned by this worker.")
                }
            }
        }
    }

    fun markFailed(jobId: UUID, workerId: String, error: String?) {
        dataSource.connection.use { conn ->
            val sql = """
                UPDATE jobs
                SET status = CASE
                        WHEN attempt_count >= 3 THEN '${Status.FAILED.name}'
                        ELSE '${Status.PENDING.name}'
                    END,
                    attempt_count = attempt_count + 1,
                    last_error = ?,
                    updated_at = NOW()
                WHERE id = ? AND claimed_by = ?
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, error)
                it.setObject(2, jobId)
                it.setString(3, workerId)
                it.executeUpdate()
            }
        }
    }

    fun reclaimStaleJobs() {
        dataSource.connection.use { conn ->
            val sql = """
                UPDATE jobs
                SET status = '${Status.PENDING.name}',
                    claimed_by = NULL,
                    claimed_at = NULL,
                    heartbeat_at = NULL,
                    updated_at = NOW()
                WHERE status = '${Status.RUNNING.name}'
                    AND heartbeat_at < NOW() - INTERVAL '30 seconds'
                    AND attempt_count < 3
            """.trimIndent()

            conn.prepareStatement(sql).use {
                val updated = it.executeUpdate()
                if (updated > 0) {
                    println("Reclaimed $updated stale job(s)")
                }
            }
        }
    }

    fun updateHeartbeat(jobId: UUID, workerId: String) {
        dataSource.connection.use { conn ->
            val sql = """
                UPDATE jobs
                SET heartbeat_at = NOW(),
                    updated_at = NOW()
                WHERE id = ? AND claimed_by = ?
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                it.setString(2, workerId)
                it.executeUpdate()
            }
        }
    }
}