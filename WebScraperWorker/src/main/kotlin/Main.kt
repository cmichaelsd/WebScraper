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
import org.example.services.JobService
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