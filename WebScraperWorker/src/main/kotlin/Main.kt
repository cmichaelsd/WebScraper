package org.webscraper

import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.example.di.appModule
import org.webscraper.services.JobService
import org.koin.core.context.startKoin
import org.koin.java.KoinJavaComponent.getKoin
import org.slf4j.LoggerFactory
import java.util.UUID

private val logger = LoggerFactory.getLogger("org.webscraper.Main")

@Volatile
var shuttingDown = false


fun main(): Unit = runBlocking {
    startKoin {
        modules(appModule)
    }
    logger.info("Koin started – DI graph initialised")

    val workerId = System.getenv("WORKER_ID") ?: "worker-${UUID.randomUUID()}"
    logger.info("Worker starting with ID '{}'", workerId)

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info("Shutdown signal received")
        shuttingDown = true
        this.cancel()
    })

    val jobService: JobService = getKoin().get()

    launch {
        logger.debug("Starting maintenance coroutine")
        jobService.maintenance()
    }
    launch {
        logger.debug("Starting worker coroutine")
        jobService.worker(workerId)
    }

    awaitCancellation()
}