package org.example

import io.ktor.client.*
import io.ktor.client.engine.cio.*
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.db.JobRepository
import org.example.db.PageRepository
import org.example.di.appModule
import org.koin.core.context.startKoin

@Volatile
var shuttingDown = false
//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
fun main(): Unit = runBlocking {
    startKoin {
        modules(appModule)
    }

    val workerId = System.getenv("WORKER_ID") ?: "worker-local"

    Runtime.getRuntime().addShutdownHook(Thread {
        println("Shutdown signal received")
        shuttingDown = true
    })

    launch { maintenance() }
    launch { worker(workerId) }

    awaitCancellation()
}

private suspend fun worker(workerId: String) {
    while (!shuttingDown) {
        try {
            val job = JobRepository.claimJob(workerId)

            if (job == null) {
                delay(2_000)
                continue
            }

            println("Claimed job ${job.id}")
            JobRepository.processJob(job, workerId)
        } catch (e: Exception) {
            println("Worker error: ${e.message}")
            delay(3_000)
        }
    }
}

private suspend fun maintenance() {
    while (!shuttingDown) {
        try {
            JobRepository.reclaimStaleJobs()
            PageRepository.reclaimStalePages()
            delay(10_000)
        } catch (e: Exception) {
            println("Maintenance error: ${e.message}")
        }
    }
}