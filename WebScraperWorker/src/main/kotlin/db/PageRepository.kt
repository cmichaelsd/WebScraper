package org.webscraper.db

import org.slf4j.LoggerFactory
import org.webscraper.models.Page
import java.sql.Connection
import java.util.*
import javax.sql.DataSource

class PageRepository(private val dataSource: DataSource) {
    private val logger = LoggerFactory.getLogger(PageRepository::class.java)

    private inline fun <T> withConn(block: (Connection) -> T): T =
        dataSource.connection.use(block)

    fun seedPages(jobId: UUID, seedUrls: List<String>): Unit = withConn { conn ->
        val sql = """
            INSERT INTO pages (id, job_id, url, depth, status)
            VALUES (?, ?, ?, 0, '${Status.PENDING.name}')
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

    fun claimNextPage(jobId: UUID): Page? = withConn { conn ->
        conn.autoCommit = false

        val sql = """
            UPDATE pages
            SET status = '${Status.RUNNING.name}',
                claimed_at = NOW()
            WHERE id = (
                SELECT id FROM pages
                WHERE job_id = ?
                    AND status = '${Status.PENDING.name}'
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

    fun insertDiscovered(
        jobId: UUID,
        urls: List<String>,
        depth: Int
    ): Unit = withConn { conn ->
        val sql = """
            INSERT INTO pages (id, job_id, url, depth, status)
            VALUES (?, ?, ?, ?, '${Status.PENDING.name}')
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

    fun markCompleted(pageId: UUID): Boolean = withConn { conn ->
        val sql = """
            UPDATE pages
            SET status = '${Status.COMPLETED.name}'
            WHERE id = ?
        """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setObject(1, pageId)
            return it.executeUpdate() == 1
        }
    }

    fun markFailed(pageId: UUID, error: String?): Boolean = withConn { conn ->
        val sql = """
            UPDATE pages
            SET status = '${Status.FAILED.name}',
                error = ?
            WHERE id = ?
        """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setString(1, error)
            it.setObject(2, pageId)
            return it.executeUpdate() == 1
        }
    }

    fun hasUnfinishedPages(jobId: UUID): Boolean = withConn { conn ->
        val sql = """
            SELECT 1 FROM pages
            WHERE job_id = ?
              AND status IN ('${Status.PENDING.name}', '${Status.RUNNING.name}')
            LIMIT 1
        """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setObject(1, jobId)
            val resultSet = it.executeQuery()
            return resultSet.next()
        }
    }

    fun hasFailedPages(jobId: UUID): Boolean = withConn { conn ->
        val sql = """
            SELECT 1 FROM pages
            WHERE job_id = ?
                AND status = '${Status.FAILED.name}'
            LIMIT 1
        """.trimIndent()

        conn.prepareStatement(sql).use {
            it.setObject(1, jobId)
            val resultSet = it.executeQuery()
            return resultSet.next()
        }
    }

    fun reclaimStalePages() = withConn { conn ->
        val sql = """
            UPDATE pages
            SET status = '${Status.PENDING.name}',
                claimed_at = NULL
            WHERE status = '${Status.RUNNING.name}'
              AND claimed_at < NOW() - INTERVAL '30 seconds'
        """.trimIndent()

        conn.prepareStatement(sql).use {
            val updated = it.executeUpdate()
            if (updated > 0) {
                logger.info("reclaimStalePages: Reclaimed $updated stale page(s)")
            }
        }

    }
}