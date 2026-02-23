package org.example.db

import org.example.models.Page
import java.util.*

object PageRepository {
    fun seedPages(jobId: UUID, seedUrls: List<String>) {
        Database.dataSource.connection.use { conn ->
            val sql = """
                INSERT INTO pages (id, job_id, url, depth, status)
                VALUES (?, ?, ?, 0, 'PENDING')
                ON CONFLICT (job_id, url) DO NOTHING
            """.trimIndent()

            conn.prepareStatement(sql).use {
                for (url in seedUrls) {
                    it.setObject(1, UUID.randomUUID())
                    it.setObject(2, jobId)
                    it.setString(3, url)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }
    }

    fun claimNextPage(jobId: UUID): Page? {
        Database.dataSource.connection.use { conn ->
            conn.autoCommit = false

            val sql = """
                UPDATE pages
                SET status = 'RUNNING'
                WHERE id = (
                    SELECT id FROM pages
                    WHERE job_id = ?
                        AND status = 'PENDING'
                    ORDER BY depth
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, url, depth
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                val resultSet = it.executeQuery()

                return if (resultSet.next()) {
                    conn.commit()
                    Page(
                        id = resultSet.getObject("id", UUID::class.java),
                        url = resultSet.getString ("url"),
                        depth = resultSet.getInt("depth")
                    )
                } else {
                    conn.rollback()
                    null
                }
            }
        }
    }

    fun insertDiscovered(
        jobId: UUID,
        urls: List<String>,
        depth: Int
    ) {
        Database.dataSource.connection.use { conn ->

            val sql = """
                INSERT INTO pages (id, job_id, url, depth, status)
                VALUES (?, ?, ?, ?, 'PENDING')
                ON CONFLICT (job_id, url) DO NOTHING
            """.trimIndent()

            conn.prepareStatement(sql).use {
                for (url in urls) {
                    it.setObject(1, UUID.randomUUID())
                    it.setObject(2, jobId)
                    it.setString(3, url)
                    it.setInt(4, depth)
                    it.addBatch()
                }
                it.executeBatch()
            }
        }
    }

    fun markCompleted(pageId: UUID) {
        Database.dataSource.connection.use { conn ->
            val sql = """
                UPDATE pages
                SET status = 'COMPLETED'
                WHERE id = ?
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, pageId)
                it.executeUpdate()
            }
        }
    }

    fun markFailed(pageId: UUID, error: String?) {
        Database.dataSource.connection.use { conn ->
            val sql = """
                UPDATE pages
                SET status = 'FAILED',
                    error = ?
                WHERE id = ?
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, error)
                it.setObject(2, pageId)
                it.executeUpdate()
            }
        }
    }

    fun hasUnfinishedPages(jobId: UUID): Boolean {
        Database.dataSource.connection.use { conn ->
            val sql = """
                SELECT 1 FROM pages
                WHERE job_id = ?
                  AND status IN ('PENDING', 'RUNNING')
                LIMIT 1
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                val resultSet = it.executeQuery()
                return resultSet.next()
            }
        }
    }

    fun hasFailedPages(jobId: UUID): Boolean {
        Database.dataSource.connection.use { conn ->
            val sql = """
                SELECT 1 FROM pages
                WHERE job_id = ?
                    AND status = 'FAILED'
                LIMIT 1
            """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                val resultSet = it.executeQuery()
                return resultSet.next()
            }
        }
    }

    fun reclaimStalePages() {
        Database.dataSource.connection.use { conn ->
            val sql = """
                UPDATE pages
                SET status = 'PENDING'
                WHERE status = 'RUNNING'
                  AND created_at < NOW() - INTERVAL '30 seconds'
            """.trimIndent()

            conn.prepareStatement(sql).use {
                val updated = it.executeUpdate()
                if (updated > 0) {
                    println("Reclaimed $updated stale page(s)")
                }
            }
        }
    }
}