package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.db.Database
import org.example.db.JobRepository
import org.example.db.PageRepository
import org.example.di.appModule
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin

@Volatile
var shuttingDown = false


fun main(): Unit = runBlocking {
    startKoin {
        modules(appModule)
    }

    val workerId = System.getenv("WORKER_ID") ?: "worker-local"

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutdown signal received")
        shuttingDown = true
    })

    val jobRepository: JobRepository = getKoin().get()

    launch { maintenance(jobRepository) }
    launch { worker(workerId, jobRepository) }

    awaitCancellation()
}

private suspend fun worker(workerId: String, jobRepository: JobRepository) {
    while (!shuttingDown) {
        try {
            val job = jobRepository.claimJob(workerId)

            if (job == null) {
                delay(2_000)
                continue
            }

            println("Claimed job ${job.id}")
            jobRepository.processJob(job, workerId)
        } catch (e: Exception) {
            println("Worker error: ${e.message}")
            delay(3_000)
        }
    }
}

private suspend fun maintenance(jobRepository: JobRepository) {
    while (!shuttingDown) {
        try {
            jobRepository.reclaimStaleJobs()
            PageRepository.reclaimStalePages()
            delay(10_000)
        } catch (e: Exception) {
            println("Maintenance error: ${e.message}")
        }
    }
}