package org.example.di

import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import org.example.services.CrawlerService
import org.example.services.RobotsService
import org.example.util.KtorRobotsFetcher
import org.example.util.interfaces.RobotsFetcher
import org.koin.dsl.module

val appModule = module {
    single {
        HttpClient(CIO) {
            followRedirects = true
            expectSuccess = false
        }
    }

    single<RobotsFetcher> {
        KtorRobotsFetcher(get())
    }

    single {
        RobotsService(get())
    }

    single {
        CrawlerService(
            robotsService = get(),
            client = get()
        )
    }
}