package org.webscraper.services

import kotlinx.coroutines.*
import org.webscraper.db.JobRepository
import org.webscraper.db.PageRepository
import org.webscraper.models.Job

class JobService(
    private val jobRepository: JobRepository,
    private val pageRepository: PageRepository,
    private val crawlerService: CrawlerService
) {
     suspend fun worker(workerId: String) = coroutineScope {
        while (isActive) {
            try {
                val job = jobRepository.claimJob(workerId)

                if (job == null) {
                    delay(2_000)
                    continue
                }

                println("Claimed job ${job.id}")
                processJob(job, workerId)
            } catch (e: Exception) {
                println("Worker error: ${e.message}")
                delay(3_000)
            }
        }
    }

     suspend fun maintenance() = coroutineScope {
        while (isActive) {
            try {
                jobRepository.reclaimStaleJobs()
                pageRepository.reclaimStalePages()
                delay(10_000)
            } catch (e: Exception) {
                println("Maintenance error: ${e.message}")
            }
        }
    }

    private suspend fun processJob(job: Job, workerId: String) = coroutineScope {
        pageRepository.seedPages(job.id, job.seedUrls)

        val heartbeatJob = launch {
            while (isActive) {
                if (!jobRepository.updateHeartbeat(job.id, workerId)) {
                    throw CancellationException("Lost ownership of job ${job.id}")
                }
                delay(5_000)
            }
        }

        try {
            while (isActive) {
                val page = pageRepository.claimNextPage(job.id) ?: break
                try {
                    val discoveredUrls = crawlerService.crawlSingle(
                        page.url,
                        page.depth,
                        job.maxDepth
                    )

                    pageRepository.insertDiscovered(
                        job.id,
                        discoveredUrls,
                        page.depth + 1
                    )

                    pageRepository.markCompleted(page.id)
                } catch (e: Exception) {
                    pageRepository.markFailed(page.id, e.message)
                }
            }

            if (pageRepository.hasFailedPages(job.id)) {
                jobRepository.markFailed(job.id, workerId, "One or more pages failed")
            } else {
                jobRepository.completeJob(job.id, workerId)
            }

        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            jobRepository.markFailed(job.id, workerId, e.message)
        } finally {
            heartbeatJob.cancelAndJoin()
        }
    }
}