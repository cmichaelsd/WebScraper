package org.webscraper

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.di.appModule
import org.webscraper.services.JobService
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

    val jobService: JobService = getKoin().get()

    launch { jobService.maintenance() }
    launch { jobService.worker(workerId) }

    awaitCancellation()
}