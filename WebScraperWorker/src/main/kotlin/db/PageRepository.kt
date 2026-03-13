package org.webscraper.db

import org.slf4j.LoggerFactory
import org.webscraper.models.Page
import java.sql.Connection
import java.util.UUID
import javax.sql.DataSource

class PageRepository(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(PageRepository::class.java)

    private inline fun <T> withConn(block: (Connection) -> T): T = dataSource.connection.use(block)

    fun seedPages(
        jobId: UUID,
        seedUrls: List<String>,
    ): Unit =
        withConn { conn ->
            conn.autoCommit = false
            val sql =
                """
                INSERT INTO pages (id, job_id, url, depth, status)
                VALUES (?, ?, ?, 0, ?)
                ON CONFLICT (job_id, url) DO NOTHING
                """.trimIndent()

            try {
                conn.prepareStatement(sql).use {
                    for (url in seedUrls) {
                        it.setObject(1, UUID.randomUUID())
                        it.setObject(2, jobId)
                        it.setString(3, url)
                        it.setString(4, Status.PENDING.name)
                        it.addBatch()
                    }
                    it.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }

    fun claimNextPage(jobId: UUID): Page? =
        withConn { conn ->
            conn.autoCommit = false

            val sql =
                """
                UPDATE pages
                SET status = ?,
                    claimed_at = NOW()
                WHERE id = (
                    SELECT id FROM pages
                    WHERE job_id = ?
                        AND status = ?
                    ORDER BY depth
                    FOR UPDATE SKIP LOCKED
                    LIMIT 1
                )
                RETURNING id, url, depth
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.RUNNING.name)
                it.setObject(2, jobId)
                it.setString(3, Status.PENDING.name)
                val resultSet = it.executeQuery()

                if (resultSet.next()) {
                    val page =
                        Page(
                            id = resultSet.getObject("id", UUID::class.java),
                            url = resultSet.getString("url"),
                            depth = resultSet.getInt("depth"),
                        )
                    conn.commit()
                    return page
                } else {
                    conn.rollback()
                    return null
                }
            }
        }

    fun insertDiscovered(
        jobId: UUID,
        urls: List<String>,
        depth: Int,
    ): Unit =
        withConn { conn ->
            conn.autoCommit = false
            val sql =
                """
                INSERT INTO pages (id, job_id, url, depth, status)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (job_id, url) DO NOTHING
                """.trimIndent()

            try {
                conn.prepareStatement(sql).use {
                    for (url in urls) {
                        it.setObject(1, UUID.randomUUID())
                        it.setObject(2, jobId)
                        it.setString(3, url)
                        it.setInt(4, depth)
                        it.setString(5, Status.PENDING.name)
                        it.addBatch()
                    }
                    it.executeBatch()
                }
                conn.commit()
            } catch (e: Exception) {
                conn.rollback()
                throw e
            }
        }

    fun markCompleted(pageId: UUID): Boolean =
        withConn { conn ->
            val sql =
                """
                UPDATE pages
                SET status = ?
                WHERE id = ?
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.COMPLETED.name)
                it.setObject(2, pageId)
                return it.executeUpdate() == 1
            }
        }

    fun markFailed(
        pageId: UUID,
        error: String?,
    ): Boolean =
        withConn { conn ->
            val sql =
                """
                UPDATE pages
                SET status = ?,
                    error = ?
                WHERE id = ?
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.FAILED.name)
                it.setString(2, error)
                it.setObject(3, pageId)
                return it.executeUpdate() == 1
            }
        }

    fun hasUnfinishedPages(jobId: UUID): Boolean =
        withConn { conn ->
            val sql =
                """
                SELECT 1 FROM pages
                WHERE job_id = ?
                  AND status IN (?, ?)
                LIMIT 1
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                it.setString(2, Status.PENDING.name)
                it.setString(3, Status.RUNNING.name)
                val resultSet = it.executeQuery()
                return resultSet.next()
            }
        }

    fun hasFailedPages(jobId: UUID): Boolean =
        withConn { conn ->
            val sql =
                """
                SELECT 1 FROM pages
                WHERE job_id = ?
                    AND status = ?
                LIMIT 1
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setObject(1, jobId)
                it.setString(2, Status.FAILED.name)
                val resultSet = it.executeQuery()
                return resultSet.next()
            }
        }

    fun reclaimStalePages() =
        withConn { conn ->
            val sql =
                """
                UPDATE pages
                SET status = ?,
                    claimed_at = NULL
                WHERE status = ?
                  AND claimed_at < NOW() - INTERVAL '30 seconds'
                """.trimIndent()

            conn.prepareStatement(sql).use {
                it.setString(1, Status.PENDING.name)
                it.setString(2, Status.RUNNING.name)
                val updated = it.executeUpdate()
                if (updated > 0) {
                    logger.info("reclaimStalePages: Reclaimed $updated stale page(s)")
                }
            }
        }
}
